package info.avaintelligent.outfit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

final class ClothingSegmenter implements AutoCloseable {
    static final int UPPER_CLOTHES = 4;
    static final int SKIRT = 5;
    static final int PANTS = 6;
    static final int DRESS = 7;

    private static final String MODEL_ASSET = "schp-atr-18-int8-static.onnx";
    private static final int INPUT_SIZE = 512;
    private static final int CLASS_COUNT = 18;
    private static final float[] MEAN = {0.406f, 0.456f, 0.485f};
    private static final float[] STD = {0.225f, 0.224f, 0.229f};

    private final OrtEnvironment environment;
    private final OrtSession session;

    static final class SegmentationResult {
        final Bitmap bitmap;
        final int dominantLabel;

        SegmentationResult(Bitmap bitmap, int dominantLabel) {
            this.bitmap = bitmap;
            this.dominantLabel = dominantLabel;
        }
    }

    ClothingSegmenter(Context context) throws IOException, OrtException {
        environment = OrtEnvironment.getEnvironment();
        File model = copyAssetToFiles(context);
        session = environment.createSession(model.getAbsolutePath(), new OrtSession.SessionOptions());
    }

    Bitmap segment(
            Bitmap source,
            Set<Integer> selectedLabels,
            boolean transparentBackground,
            boolean cropToClothing)
            throws OrtException {
        return segmentWithMetadata(source, selectedLabels, transparentBackground, cropToClothing).bitmap;
    }

    SegmentationResult segmentWithMetadata(
            Bitmap source,
            Set<Integer> selectedLabels,
            boolean transparentBackground,
            boolean cropToClothing)
            throws OrtException {
        Bitmap inputBitmap = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true);
        float[] inputValues = normalizedChw(inputBitmap);
        MaskResult maskResult;
        try (OnnxTensor input = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(inputValues),
                new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
             OrtSession.Result result = session.run(Collections.singletonMap("pixel_values", input))) {
            FloatBuffer logits = ((OnnxTensor) result.get(0)).getFloatBuffer();
            maskResult = maskFromLogits(logits, selectedLabels);
        }
        if (!hasForeground(maskResult.mask)) {
            throw new IllegalStateException("No selected clothing was detected.");
        }
        Bitmap cutout = applyMaskAtOriginalResolution(source, maskResult.mask, transparentBackground);
        if (!cropToClothing) {
            return new SegmentationResult(cutout, maskResult.dominantLabel);
        }
        return new SegmentationResult(
                cropMaskBounds(cutout, maskResult.mask, source.getWidth(), source.getHeight()),
                maskResult.dominantLabel
        );
    }

    private boolean hasForeground(boolean[] mask) {
        for (boolean selected : mask) {
            if (selected) {
                return true;
            }
        }
        return false;
    }

    private float[] normalizedChw(Bitmap input) {
        int plane = INPUT_SIZE * INPUT_SIZE;
        int[] pixels = new int[plane];
        input.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        float[] values = new float[plane * 3];
        for (int index = 0; index < plane; index++) {
            int color = pixels[index];
            values[index] = (Color.red(color) / 255.0f - MEAN[0]) / STD[0];
            values[plane + index] = (Color.green(color) / 255.0f - MEAN[1]) / STD[1];
            values[2 * plane + index] = (Color.blue(color) / 255.0f - MEAN[2]) / STD[2];
        }
        return values;
    }

    private MaskResult maskFromLogits(FloatBuffer logits, Set<Integer> selectedLabels) {
        int plane = INPUT_SIZE * INPUT_SIZE;
        boolean[] mask = new boolean[plane];
        int[] counts = new int[CLASS_COUNT];
        for (int pixel = 0; pixel < plane; pixel++) {
            int bestLabel = 0;
            float bestScore = logits.get(pixel);
            for (int label = 1; label < CLASS_COUNT; label++) {
                float score = logits.get(label * plane + pixel);
                if (score > bestScore) {
                    bestLabel = label;
                    bestScore = score;
                }
            }
            mask[pixel] = selectedLabels.contains(bestLabel);
            if (mask[pixel]) {
                counts[bestLabel]++;
            }
        }
        int dominantLabel = 0;
        int dominantCount = 0;
        for (int label : selectedLabels) {
            if (label >= 0 && label < counts.length && counts[label] > dominantCount) {
                dominantLabel = label;
                dominantCount = counts[label];
            }
        }
        return new MaskResult(mask, dominantLabel);
    }

    private static final class MaskResult {
        final boolean[] mask;
        final int dominantLabel;

        MaskResult(boolean[] mask, int dominantLabel) {
            this.mask = mask;
            this.dominantLabel = dominantLabel;
        }
    }

    private Bitmap applyMaskAtOriginalResolution(Bitmap source, boolean[] mask, boolean transparent) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int background = transparent ? Color.TRANSPARENT : Color.BLACK;
        for (int y = 0; y < height; y++) {
            int modelY = Math.min(INPUT_SIZE - 1, y * INPUT_SIZE / height);
            for (int x = 0; x < width; x++) {
                int modelX = Math.min(INPUT_SIZE - 1, x * INPUT_SIZE / width);
                if (!mask[modelY * INPUT_SIZE + modelX]) {
                    pixels[y * width + x] = background;
                }
            }
        }
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        output.setPixels(pixels, 0, width, 0, 0, width, height);
        return output;
    }

    private Bitmap cropMaskBounds(Bitmap output, boolean[] mask, int width, int height) {
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            int modelY = Math.min(INPUT_SIZE - 1, y * INPUT_SIZE / height);
            for (int x = 0; x < width; x++) {
                int modelX = Math.min(INPUT_SIZE - 1, x * INPUT_SIZE / width);
                if (mask[modelY * INPUT_SIZE + modelX]) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return output;
        }
        return Bitmap.createBitmap(output, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private File copyAssetToFiles(Context context) throws IOException {
        File directory = new File(context.getFilesDir(), "models");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Cannot create model directory.");
        }
        File model = new File(directory, MODEL_ASSET);
        if (model.exists() && model.length() > 0) {
            return model;
        }
        try (InputStream input = context.getAssets().open(MODEL_ASSET);
             FileOutputStream output = new FileOutputStream(model)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        return model;
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
