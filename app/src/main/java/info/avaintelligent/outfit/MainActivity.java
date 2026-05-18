package info.avaintelligent.outfit;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_CAMERA = 10;
    private static final int REQUEST_GALLERY = 11;

    private static final int CANVAS = Color.rgb(245, 241, 234);
    private static final int SURFACE = Color.rgb(255, 252, 247);
    private static final int INK = Color.rgb(32, 36, 33);
    private static final int MUTED = Color.rgb(114, 119, 111);
    private static final int LINE = Color.rgb(225, 216, 203);
    private static final int FOREST = Color.rgb(54, 88, 76);
    private static final int CLAY = Color.rgb(185, 111, 82);
    private static final int BLUE = Color.rgb(88, 109, 140);
    private static final int MIST = Color.rgb(231, 239, 235);
    private static final int BLUSH = Color.rgb(249, 246, 241);

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private int selectedTab = 0;
    private Bitmap selectedImage;
    private Bitmap extractedClothingImage;
    private String extractedClothingName = "Clothing Item";
    private String lastAiResult = "No clothing image selected yet.";
    private String lastAiDetails = "Use camera or gallery, then extract a clean 2D clothing image.";
    private boolean analysisInProgress;
    private final ArrayList<SavedClothingItem> savedClothingItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(CANVAS);
            getWindow().setNavigationBarColor(SURFACE);
        }
        showApp();
    }

    private void showApp() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CANVAS);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(7), dp(10), dp(8));
        nav.setBackgroundColor(SURFACE);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(72)));

        setContentView(root);
        renderTab(0);
    }

    private void renderTab(int index) {
        selectedTab = index;
        content.removeAllViews();
        nav.removeAllViews();

        if (index == 0) home();
        if (index == 1) wardrobe();
        if (index == 2) outfitAi();
        if (index == 3) shop();
        if (index == 4) profile();

        addNavButton("Home", 0);
        addNavButton("Wardrobe", 1);
        addNavButton("Outfit AI", 2);
        addNavButton("Shop", 3);
        addNavButton("Profile", 4);
    }

    private void home() {
        LinearLayout page = page("Today", "Your AI stylist picked a clean smart casual look.");
        LinearLayout hero = panel(FOREST);
        hero.addView(label("19 C  |  Casual Friday", 14, Color.WHITE, false));
        hero.addView(spacer(14));
        hero.addView(label("Navy Overshirt Set", 27, Color.WHITE, true));
        hero.addView(label("White tee, beige trousers, white sneakers, brown belt.", 15, Color.WHITE, false));
        hero.addView(spacer(16));
        hero.addView(outfitPreview());
        hero.addView(spacer(14));
        Button wear = button("Wear This", Color.WHITE, FOREST);
        hero.addView(wear, new LinearLayout.LayoutParams(-1, dp(48)));
        page.addView(hero);

        page.addView(section("Occasion"));
        page.addView(chips(new String[]{"Casual", "Office", "Party", "Date", "Travel", "Classic"}));

        page.addView(section("Recently Added"));
        page.addView(horizontalCards(new String[]{"White Tee", "Navy Jacket", "Beige Trouser", "Brown Loafer"}));

        page.addView(section("Stylist Notes"));
        page.addView(insight("Strong palette", "Navy, white, beige, and brown give you easy outfit combinations."));
        page.addView(insight("Missing piece", "A white overshirt would unlock 12 more outfits."));
    }

    private void wardrobe() {
        LinearLayout page = page("Wardrobe", "42 items scanned across five categories.");
        LinearLayout upload = panel(SURFACE);
        upload.addView(label("Extract clothing", 22, INK, true));
        upload.addView(label("Take a photo or choose one from gallery. Google Cloud detects clothes, then you confirm the extracted item.", 14, MUTED, false));
        upload.addView(spacer(14));

        if (selectedImage != null) {
            upload.addView(imageFrame(extractedClothingImage != null ? extractedClothingImage : selectedImage));
            upload.addView(spacer(14));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button camera = button("Camera", FOREST, Color.WHITE);
        camera.setOnClickListener(v -> openCamera());
        Button gallery = button("Gallery", SURFACE, FOREST);
        gallery.setOnClickListener(v -> openGallery());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        actionParams.setMargins(0, 0, dp(10), 0);
        actions.addView(camera, actionParams);
        actions.addView(gallery, new LinearLayout.LayoutParams(0, dp(52), 1));
        upload.addView(actions);
        upload.addView(spacer(14));

        LinearLayout result = panel(MIST);
        result.addView(label(lastAiResult, 18, INK, true));
        result.addView(label(lastAiDetails, 14, MUTED, false));
        if (canExtractSelectedImage()) {
            result.addView(spacer(12));
            Button extract = button("Extract Clothing", FOREST, Color.WHITE);
            extract.setOnClickListener(v -> extractSelectedImage());
            result.addView(extract, new LinearLayout.LayoutParams(-1, dp(52)));
        }
        if (canSaveExtractedImage()) {
            result.addView(spacer(12));
            Button save = button("Confirm & Save", FOREST, Color.WHITE);
            save.setOnClickListener(v -> saveExtractedItem());
            result.addView(save, new LinearLayout.LayoutParams(-1, dp(52)));
        }
        upload.addView(result);
        page.addView(upload);

        page.addView(section("Categories"));
        GridLayout categories = grid(2);
        categories.addView(category("Tops", "14 items"));
        categories.addView(category("Bottoms", "8 items"));
        categories.addView(category("Jackets", "6 items"));
        categories.addView(category("Shoes", "9 items"));
        page.addView(categories);

        page.addView(section("Filters"));
        page.addView(chips(new String[]{"All", "White", "Navy", "Summer", "Office", "Favorites"}));

        page.addView(section("Items"));
        GridLayout items = grid(2);
        for (SavedClothingItem item : savedClothingItems) {
            items.addView(savedClothingCard(item));
        }
        items.addView(clothingCard("White Tee", "Top | Casual", Color.rgb(247, 245, 239)));
        items.addView(clothingCard("Navy Jacket", "Outerwear | Smart", Color.rgb(38, 57, 79)));
        items.addView(clothingCard("Beige Trouser", "Bottom | Office", Color.rgb(201, 176, 138)));
        items.addView(clothingCard("Brown Loafer", "Shoes | Classic", Color.rgb(109, 75, 60)));
        items.addView(clothingCard("Denim Shirt", "Top | Casual", BLUE));
        items.addView(clothingCard("Black Jeans", "Bottom | Street", Color.rgb(34, 34, 34)));
        page.addView(items);
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(intent, REQUEST_CAMERA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Camera app not found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        try {
            startActivityForResult(intent, REQUEST_GALLERY);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Gallery app not found.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        Bitmap bitmap = null;
        if (requestCode == REQUEST_CAMERA && data.getExtras() != null) {
            bitmap = (Bitmap) data.getExtras().get("data");
        }

        if (requestCode == REQUEST_GALLERY) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                } catch (IOException e) {
                    Toast.makeText(this, "Could not load selected image.", Toast.LENGTH_SHORT).show();
                }
            }
        }

        if (bitmap != null) {
            selectedImage = bitmap;
            extractedClothingImage = null;
            extractedClothingName = "Clothing Item";
            analysisInProgress = false;
            lastAiResult = "Image ready";
            lastAiDetails = "Tap Extract Clothing to create a clean 2D item image.";
            renderTab(1);
        }
    }

    private void extractSelectedImage() {
        if (selectedImage == null) {
            Toast.makeText(this, "Choose an image first.", Toast.LENGTH_SHORT).show();
            return;
        }

        analysisInProgress = true;
        extractedClothingImage = null;
        lastAiResult = "Extracting clothing...";
        lastAiDetails = hasGoogleCloudVisionKey()
                ? "Google Cloud Vision is finding clothing objects."
                : "No Google Cloud key found. Using local extraction fallback.";
        renderTab(1);
        createExtractedClothingImage(selectedImage);
    }

    private void createExtractedClothingImage(Bitmap bitmap) {
        new Thread(() -> {
            ExtractionResult result;
            if (hasBackendUrl()) {
                result = extractWithBackend(bitmap);
            } else if (hasGoogleCloudVisionKey()) {
                result = extractWithGoogleCloudVision(bitmap);
            } else {
                result = new ExtractionResult(extractForegroundClothing(bitmap), "Clothing Item", "Local fallback extraction");
            }
            runOnUiThread(() -> {
                analysisInProgress = false;
                extractedClothingImage = result.bitmap;
                extractedClothingName = result.itemName;
                lastAiResult = "Detected: " + result.itemName;
                lastAiDetails = result.message + ". Review the preview, then confirm if it is correct.";
                renderTab(1);
            });
        }).start();
    }

    private boolean hasGoogleCloudVisionKey() {
        return BuildConfig.GOOGLE_CLOUD_VISION_API_KEY != null
                && !BuildConfig.GOOGLE_CLOUD_VISION_API_KEY.trim().isEmpty();
    }

    private boolean hasBackendUrl() {
        return BuildConfig.WARDROBE_BACKEND_BASE_URL != null
                && !BuildConfig.WARDROBE_BACKEND_BASE_URL.trim().isEmpty();
    }

    private ExtractionResult extractWithBackend(Bitmap bitmap) {
        HttpURLConnection connection = null;
        try {
            String baseUrl = BuildConfig.WARDROBE_BACKEND_BASE_URL.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            URL url = new URL(baseUrl + "/extract");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(90000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JSONObject request = new JSONObject();
            request.put("image_base64", encodeBitmap(bitmap));

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(request.toString().getBytes());
            outputStream.flush();
            outputStream.close();

            int status = connection.getResponseCode();
            String response = readStream(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (status < 200 || status >= 300) {
                return hasGoogleCloudVisionKey()
                        ? extractWithGoogleCloudVision(bitmap)
                        : new ExtractionResult(extractForegroundClothing(bitmap), "Clothing Item", "Backend failed: " + status);
            }

            JSONObject json = new JSONObject(response);
            String label = normalizeClothingName(json.optString("label", "Clothing Item"));
            String imageBase64 = json.optString("image_base64", "");
            if (imageBase64.isEmpty()) {
                return new ExtractionResult(extractForegroundClothing(bitmap), label, "Backend returned no image");
            }

            byte[] bytes = Base64.decode(imageBase64, Base64.DEFAULT);
            Bitmap extracted = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (extracted == null) {
                return new ExtractionResult(extractForegroundClothing(bitmap), label, "Backend image decode failed");
            }

            String message = json.optString("message", "Backend AI extracted the item for avatar testing");
            return new ExtractionResult(extracted, label, message);
        } catch (Exception e) {
            return hasGoogleCloudVisionKey()
                    ? extractWithGoogleCloudVision(bitmap)
                    : new ExtractionResult(extractForegroundClothing(bitmap), "Clothing Item", "Backend error: " + e.getClass().getSimpleName());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ExtractionResult extractWithGoogleCloudVision(Bitmap bitmap) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(
                    "https://vision.googleapis.com/v1/images:annotate?key="
                            + BuildConfig.GOOGLE_CLOUD_VISION_API_KEY
            );
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Android-Package", getPackageName());
            String certificate = getSigningCertificateSha1();
            if (!certificate.isEmpty()) {
                connection.setRequestProperty("X-Android-Cert", certificate);
            }

            JSONObject request = new JSONObject();
            JSONArray requests = new JSONArray();
            JSONObject item = new JSONObject();

            JSONObject image = new JSONObject();
            image.put("content", encodeBitmap(bitmap));

            JSONArray features = new JSONArray();
            JSONObject objectLocalization = new JSONObject();
            objectLocalization.put("type", "OBJECT_LOCALIZATION");
            objectLocalization.put("maxResults", 20);
            features.put(objectLocalization);

            JSONObject labelDetection = new JSONObject();
            labelDetection.put("type", "LABEL_DETECTION");
            labelDetection.put("maxResults", 20);
            features.put(labelDetection);

            item.put("image", image);
            item.put("features", features);
            requests.put(item);
            request.put("requests", requests);

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(request.toString().getBytes());
            outputStream.flush();
            outputStream.close();

            int status = connection.getResponseCode();
            String response = readStream(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (status < 200 || status >= 300) {
                return new ExtractionResult(
                        extractForegroundClothing(bitmap),
                        "Clothing Item",
                        "Google Cloud failed: " + status + " " + extractGoogleError(response)
                );
            }

            DetectionResult detection = clothingDetectionFromVisionResponse(response, bitmap.getWidth(), bitmap.getHeight());
            if (detection.rect == null) {
                return new ExtractionResult(
                        extractForegroundClothing(bitmap),
                        detection.name,
                        "No clothing object box found, used background removal fallback"
                );
            }

            Bitmap cropped = Bitmap.createBitmap(
                    bitmap,
                    detection.rect.left,
                    detection.rect.top,
                    detection.rect.width(),
                    detection.rect.height()
            );
            return new ExtractionResult(
                    centerOnTransparentCanvas(removeBackground(cropped), 640, 640),
                    detection.name,
                    "Google Cloud recognized and extracted the clothing area"
            );
        } catch (Exception e) {
            return new ExtractionResult(
                    extractForegroundClothing(bitmap),
                    "Clothing Item",
                    "Google Cloud error: " + e.getClass().getSimpleName()
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String extractGoogleError(String response) {
        try {
            JSONObject json = new JSONObject(response);
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (Exception ignored) {
            // Use generic text below when Google returns a non-JSON error.
        }
        return "used local fallback";
    }

    private String getSigningCertificateSha1() {
        try {
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo packageInfo = getPackageManager().getPackageInfo(
                        getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES
                );
                signatures = packageInfo.signingInfo.getApkContentsSigners();
            } else {
                PackageInfo packageInfo = getPackageManager().getPackageInfo(
                        getPackageName(),
                        PackageManager.GET_SIGNATURES
                );
                signatures = packageInfo.signatures;
            }

            if (signatures == null || signatures.length == 0) {
                return "";
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(signatures[0].toByteArray());
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format(Locale.US, "%02X", value));
            }
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private DetectionResult clothingDetectionFromVisionResponse(String response, int imageWidth, int imageHeight) throws Exception {
        JSONObject root = new JSONObject(response);
        JSONArray responses = root.optJSONArray("responses");
        if (responses == null || responses.length() == 0) {
            return new DetectionResult(null, "Clothing Item", 0);
        }

        JSONObject firstResponse = responses.getJSONObject(0);
        String bestLabelName = clothingLabelFromVisionResponse(firstResponse);
        JSONArray objects = firstResponse.optJSONArray("localizedObjectAnnotations");
        if (objects == null) {
            return new DetectionResult(null, bestLabelName, 0);
        }

        Rect bestRect = null;
        String bestName = bestLabelName;
        double bestScore = 0;
        for (int i = 0; i < objects.length(); i++) {
            JSONObject object = objects.getJSONObject(i);
            String name = object.optString("name", "");
            double score = object.optDouble("score", 0);
            if (!isClothingObjectName(name) || score < bestScore) {
                continue;
            }

            JSONObject boundingPoly = object.getJSONObject("boundingPoly");
            JSONArray vertices = boundingPoly.getJSONArray("normalizedVertices");
            float minX = 1f;
            float minY = 1f;
            float maxX = 0f;
            float maxY = 0f;
            for (int j = 0; j < vertices.length(); j++) {
                JSONObject vertex = vertices.getJSONObject(j);
                float x = (float) vertex.optDouble("x", 0);
                float y = (float) vertex.optDouble("y", 0);
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }

            int left = clamp(Math.round(minX * imageWidth), 0, imageWidth - 2);
            int top = clamp(Math.round(minY * imageHeight), 0, imageHeight - 2);
            int right = clamp(Math.round(maxX * imageWidth), left + 1, imageWidth);
            int bottom = clamp(Math.round(maxY * imageHeight), top + 1, imageHeight);
            bestRect = new Rect(left, top, right, bottom);
            bestName = normalizeClothingName(name);
            bestScore = score;
        }

        return new DetectionResult(bestRect, bestName, bestScore);
    }

    private String clothingLabelFromVisionResponse(JSONObject response) throws Exception {
        JSONArray labels = response.optJSONArray("labelAnnotations");
        if (labels == null) return "Clothing Item";

        for (int i = 0; i < labels.length(); i++) {
            JSONObject label = labels.getJSONObject(i);
            String description = label.optString("description", "");
            if (isClothingObjectName(description)) {
                return normalizeClothingName(description);
            }
        }
        return "Clothing Item";
    }

    private boolean isClothingObjectName(String name) {
        String clean = name.toLowerCase();
        return clean.contains("shirt")
                || clean.contains("t-shirt")
                || clean.contains("tee")
                || clean.contains("clothing")
                || clean.contains("apparel")
                || clean.contains("dress")
                || clean.contains("suit")
                || clean.contains("tie")
                || clean.contains("shoe")
                || clean.contains("sneaker")
                || clean.contains("boot")
                || clean.contains("footwear")
                || clean.contains("jacket")
                || clean.contains("coat")
                || clean.contains("blazer")
                || clean.contains("hoodie")
                || clean.contains("sweater")
                || clean.contains("pants")
                || clean.contains("jeans")
                || clean.contains("trousers")
                || clean.contains("shorts")
                || clean.contains("skirt")
                || clean.contains("hat")
                || clean.contains("cap")
                || clean.contains("bag")
                || clean.contains("backpack")
                || clean.contains("sock");
    }

    private String normalizeClothingName(String name) {
        String clean = name.toLowerCase();
        if (clean.contains("t-shirt") || clean.contains("tee")) return "T-Shirt";
        if (clean.contains("shirt")) return "Shirt";
        if (clean.contains("dress")) return "Dress";
        if (clean.contains("suit")) return "Suit";
        if (clean.contains("tie")) return "Tie";
        if (clean.contains("sneaker")) return "Sneakers";
        if (clean.contains("boot")) return "Boots";
        if (clean.contains("shoe") || clean.contains("footwear")) return "Shoes";
        if (clean.contains("blazer")) return "Blazer";
        if (clean.contains("jacket")) return "Jacket";
        if (clean.contains("coat")) return "Coat";
        if (clean.contains("hoodie")) return "Hoodie";
        if (clean.contains("sweater")) return "Sweater";
        if (clean.contains("jeans")) return "Jeans";
        if (clean.contains("pants") || clean.contains("trousers")) return "Pants";
        if (clean.contains("shorts")) return "Shorts";
        if (clean.contains("skirt")) return "Skirt";
        if (clean.contains("cap")) return "Cap";
        if (clean.contains("hat")) return "Hat";
        if (clean.contains("backpack")) return "Backpack";
        if (clean.contains("bag")) return "Bag";
        if (clean.contains("sock")) return "Socks";
        if (clean.contains("apparel") || clean.contains("clothing")) return "Clothing Item";
        return name.isEmpty() ? "Clothing Item" : name;
    }

    private String encodeBitmap(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean canExtractSelectedImage() {
        return selectedImage != null
                && !analysisInProgress
                && extractedClothingImage == null;
    }

    private boolean canSaveExtractedImage() {
        return selectedImage != null
                && extractedClothingImage != null
                && !analysisInProgress;
    }

    private void saveExtractedItem() {
        if (!canSaveExtractedImage()) {
            Toast.makeText(this, "Extract clothing first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = extractedClothingName;
        String meta = "Background removed | Avatar ready";
        Bitmap savedBitmap = extractedClothingImage.copy(Bitmap.Config.ARGB_8888, false);
        savedClothingItems.add(0, new SavedClothingItem(name, meta, savedBitmap));
        selectedImage = null;
        extractedClothingImage = null;
        extractedClothingName = "Clothing Item";
        analysisInProgress = false;
        lastAiResult = "Saved to wardrobe";
        lastAiDetails = name + " was added to your wardrobe items.";
        Toast.makeText(this, "Item saved", Toast.LENGTH_SHORT).show();
        renderTab(1);
    }

    private Bitmap extractForegroundClothing(Bitmap source) {
        Bitmap working = source.copy(Bitmap.Config.ARGB_8888, false);
        int width = working.getWidth();
        int height = working.getHeight();
        int background = estimateBackgroundColor(working);

        int minX = width;
        int minY = height;
        int maxX = 0;
        int maxY = 0;
        boolean found = false;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = working.getPixel(x, y);
                if (isForegroundPixel(pixel, background)) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    found = true;
                }
            }
        }

        if (!found) {
            minX = 0;
            minY = 0;
            maxX = width - 1;
            maxY = height - 1;
        }

        int padX = Math.max(12, (maxX - minX) / 10);
        int padY = Math.max(12, (maxY - minY) / 10);
        minX = Math.max(0, minX - padX);
        minY = Math.max(0, minY - padY);
        maxX = Math.min(width - 1, maxX + padX);
        maxY = Math.min(height - 1, maxY + padY);

        Bitmap cropped = Bitmap.createBitmap(working, minX, minY, maxX - minX + 1, maxY - minY + 1);
        return centerOnTransparentCanvas(removeBackground(cropped), 640, 640);
    }

    private int estimateBackgroundColor(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] samples = {
                bitmap.getPixel(0, 0),
                bitmap.getPixel(width - 1, 0),
                bitmap.getPixel(0, height - 1),
                bitmap.getPixel(width - 1, height - 1)
        };
        int r = 0;
        int g = 0;
        int b = 0;
        for (int color : samples) {
            r += Color.red(color);
            g += Color.green(color);
            b += Color.blue(color);
        }
        return Color.rgb(r / samples.length, g / samples.length, b / samples.length);
    }

    private boolean isForegroundPixel(int pixel, int background) {
        int dr = Color.red(pixel) - Color.red(background);
        int dg = Color.green(pixel) - Color.green(background);
        int db = Color.blue(pixel) - Color.blue(background);
        int distance = Math.abs(dr) + Math.abs(dg) + Math.abs(db);
        int brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
        return distance > 42 && brightness > 20;
    }

    private Bitmap removeBackground(Bitmap source) {
        Bitmap working = source.copy(Bitmap.Config.ARGB_8888, true);
        int background = estimateBackgroundColor(working);
        int width = working.getWidth();
        int height = working.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = working.getPixel(x, y);
                if (!isForegroundPixel(pixel, background)) {
                    working.setPixel(x, y, Color.TRANSPARENT);
                }
            }
        }
        return working;
    }

    private Bitmap centerOnTransparentCanvas(Bitmap item, int canvasWidth, int canvasHeight) {
        Bitmap output = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        float scale = Math.min(
                canvasWidth * 0.86f / item.getWidth(),
                canvasHeight * 0.86f / item.getHeight()
        );
        int targetWidth = Math.max(1, Math.round(item.getWidth() * scale));
        int targetHeight = Math.max(1, Math.round(item.getHeight() * scale));
        int left = (canvasWidth - targetWidth) / 2;
        int top = (canvasHeight - targetHeight) / 2;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Rect src = new Rect(0, 0, item.getWidth(), item.getHeight());
        Rect dst = new Rect(left, top, left + targetWidth, top + targetHeight);
        canvas.drawBitmap(item, src, dst, paint);
        return output;
    }

    private Bitmap centerOnCanvas(Bitmap item, int canvasWidth, int canvasHeight) {
        Bitmap output = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.rgb(248, 247, 243));

        float scale = Math.min(
                canvasWidth * 0.82f / item.getWidth(),
                canvasHeight * 0.82f / item.getHeight()
        );
        int targetWidth = Math.max(1, Math.round(item.getWidth() * scale));
        int targetHeight = Math.max(1, Math.round(item.getHeight() * scale));
        int left = (canvasWidth - targetWidth) / 2;
        int top = (canvasHeight - targetHeight) / 2;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Rect src = new Rect(0, 0, item.getWidth(), item.getHeight());
        Rect dst = new Rect(left, top, left + targetWidth, top + targetHeight);
        canvas.drawBitmap(item, src, dst, paint);
        return output;
    }
    private void outfitAi() {
        LinearLayout page = page("Outfit AI", "Generate a look from your real wardrobe.");
        LinearLayout generator = panel(SURFACE);
        generator.addView(label("Generate outfit", 22, INK, true));
        generator.addView(label("Pick occasion and color mood.", 14, MUTED, false));
        generator.addView(spacer(10));
        generator.addView(chips(new String[]{"Office", "Date", "Party", "Streetwear"}));
        generator.addView(chips(new String[]{"Warm", "Neutral", "Bold", "Monochrome"}));
        generator.addView(button("Generate Look", FOREST, Color.WHITE), new LinearLayout.LayoutParams(-1, dp(48)));
        page.addView(generator);

        page.addView(section("Current Look"));
        LinearLayout canvas = panel(SURFACE);
        canvas.addView(outfitRow("Navy overshirt", "Outerwear"));
        canvas.addView(outfitRow("White tee", "Top"));
        canvas.addView(outfitRow("Beige trousers", "Bottom"));
        canvas.addView(outfitRow("White sneakers", "Shoes"));
        page.addView(canvas);

        page.addView(section("Why it works"));
        page.addView(insight("Balanced contrast", "The navy jacket anchors the look while beige and white keep it relaxed."));
    }

    private void shop() {
        LinearLayout page = page("Shop", "Smart suggestions based on gaps in your wardrobe.");
        LinearLayout hero = panel(BLUE);
        hero.addView(label("Best next buy", 14, Color.WHITE, false));
        hero.addView(label("White Overshirt", 26, Color.WHITE, true));
        hero.addView(label("Completes 12 outfits and matches your navy, beige, and denim pieces.", 15, Color.WHITE, false));
        page.addView(hero);

        page.addView(section("Recommended Colors"));
        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.addView(colorTile("Olive", Color.rgb(101, 116, 92)));
        colors.addView(colorTile("Stone", Color.rgb(201, 192, 176)));
        colors.addView(colorTile("Brown", Color.rgb(124, 82, 62)));
        colors.addView(colorTile("Gray", Color.rgb(139, 143, 146)));
        page.addView(colors);

        page.addView(section("Missing Pieces"));
        page.addView(product("White Overshirt", "Adds a clean layer for casual and office outfits.", "From $49", Color.rgb(244, 241, 234)));
        page.addView(product("Olive Chino", "Works with white, navy, denim, and brown shoes.", "From $59", Color.rgb(101, 116, 92)));
        page.addView(product("Minimal Sneaker", "Completes your relaxed smart casual looks.", "From $69", Color.rgb(248, 247, 243)));
    }

    private void profile() {
        LinearLayout page = page("Style DNA", "Minimalist, smart casual, warm neutral palette.");
        LinearLayout score = panel(SURFACE);
        score.addView(label("Style identity", 20, INK, true));
        score.addView(scoreBar("Minimalist", 72, FOREST));
        score.addView(scoreBar("Smart casual", 65, BLUE));
        score.addView(scoreBar("Urban", 48, CLAY));
        page.addView(score);

        page.addView(section("Color Profile"));
        LinearLayout palette = panel(SURFACE);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        palette.addView(colorTile("", Color.rgb(36, 50, 72)));
        palette.addView(colorTile("", Color.rgb(244, 241, 234)));
        palette.addView(colorTile("", Color.rgb(198, 172, 131)));
        palette.addView(colorTile("", Color.rgb(109, 75, 60)));
        page.addView(palette);

        page.addView(section("Preferences"));
        page.addView(pref("Fit", "Relaxed slim"));
        page.addView(pref("Avoid", "Neon colors"));
        page.addView(pref("Budget", "Mid-range"));
        page.addView(pref("Brands", "Uniqlo, Zara, COS"));
    }

    private LinearLayout page(String title, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(30));
        scroll.addView(page);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        page.addView(label(title, 34, INK, true));
        page.addView(label(subtitle, 15, MUTED, false));
        page.addView(spacer(20));
        return page;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1.04f);
        view.setIncludeFontPadding(true);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView section(String text) {
        TextView view = label(text, 19, INK, true);
        view.setPadding(0, dp(22), 0, dp(10));
        return view;
    }

    private LinearLayout panel(int color) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(18), dp(18), dp(18), dp(18));
        view.setBackground(round(color, 14, strokeFor(color)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setElevation(dp(color == SURFACE ? 2 : 0));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        view.setLayoutParams(params);
        return view;
    }

    private int strokeFor(int color) {
        if (color == SURFACE || color == CANVAS || color == BLUSH) return LINE;
        if (color == MIST) return Color.rgb(211, 226, 219);
        return color;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private View spacer(int height) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return space;
    }

    private Button button(String text, int bg, int fg) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(fg);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextSize(15);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(round(bg, 14, bg == SURFACE ? LINE : bg));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(dp(1));
        }
        return button;
    }

    private LinearLayout imageFrame(Bitmap bitmap) {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setGravity(Gravity.CENTER);
        frame.setPadding(dp(10), dp(10), dp(10), dp(10));
        frame.setBackground(round(BLUSH, 16, LINE));

        ImageView preview = new ImageView(this);
        preview.setImageBitmap(bitmap);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.rgb(249, 248, 245));
        frame.addView(preview, new LinearLayout.LayoutParams(-1, previewHeight()));

        return frame;
    }

    private LinearLayout chips(String[] labels) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            TextView chip = label(labels[i], 14, i == 0 ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setBackground(round(i == 0 ? FOREST : SURFACE, 22, i == 0 ? FOREST : LINE));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(9), dp(8));
            row.addView(chip, params);
        }
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll);
        return wrap;
    }

    private LinearLayout outfitPreview() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] colors = {Color.rgb(38, 57, 79), Color.rgb(246, 243, 236), Color.rgb(201, 176, 138), Color.rgb(248, 247, 243)};
        for (int color : colors) {
            TextView tile = label("●", 32, readable(color), true);
            tile.setGravity(Gravity.CENTER);
            tile.setBackground(round(color, 8, Color.argb(70, 255, 255, 255)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(86), 1);
            params.setMargins(0, 0, dp(8), 0);
            row.addView(tile, params);
        }
        return row;
    }

    private LinearLayout horizontalCards(String[] names) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] colors = {Color.rgb(247, 245, 239), Color.rgb(38, 57, 79), Color.rgb(201, 176, 138), Color.rgb(109, 75, 60)};
        for (int i = 0; i < names.length; i++) {
            row.addView(clothingCard(names[i], "Recently added", colors[i]), new LinearLayout.LayoutParams(dp(124), dp(150)));
        }
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll);
        return wrap;
    }

    private LinearLayout insight(String title, String body) {
        LinearLayout card = panel(SURFACE);
        card.addView(label(title, 17, INK, true));
        card.addView(label(body, 14, MUTED, false));
        return card;
    }

    private GridLayout grid(int columns) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        grid.setUseDefaultMargins(false);
        return grid;
    }

    private LinearLayout category(String title, String count) {
        LinearLayout card = panel(SURFACE);
        card.addView(label(title, 17, INK, true));
        card.addView(label(count, 14, MUTED, false));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cardWidth();
        params.setMargins(0, 0, dp(10), dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout clothingCard(String name, String meta, int color) {
        LinearLayout card = panel(SURFACE);
        TextView block = label("●", 38, readable(color), true);
        block.setGravity(Gravity.CENTER);
        block.setBackground(round(color, 14, color));
        card.addView(block, new LinearLayout.LayoutParams(-1, itemImageHeight()));
        card.addView(spacer(8));
        card.addView(label(name, 16, INK, true));
        card.addView(label(meta, 13, MUTED, false));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cardWidth();
        params.setMargins(0, 0, dp(10), dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout savedClothingCard(SavedClothingItem item) {
        LinearLayout card = panel(SURFACE);
        ImageView image = new ImageView(this);
        image.setImageBitmap(item.image);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(dp(6), dp(6), dp(6), dp(6));
        image.setBackground(round(BLUSH, 14, LINE));
        card.addView(image, new LinearLayout.LayoutParams(-1, itemImageHeight()));
        card.addView(spacer(8));
        card.addView(label(item.name, 16, INK, true));
        card.addView(label(item.meta, 13, MUTED, false));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cardWidth();
        params.setMargins(0, 0, dp(10), dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout outfitRow(String title, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(round(CANVAS, 8, LINE));
        TextView copy = label(title + "\n" + detail, 15, INK, true);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(label("Swap", 14, FOREST, true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);
        return row;
    }

    private LinearLayout product(String title, String reason, String price, int color) {
        LinearLayout card = panel(SURFACE);
        card.setOrientation(LinearLayout.HORIZONTAL);
        TextView image = label("●", 34, readable(color), true);
        image.setGravity(Gravity.CENTER);
        image.setBackground(round(color, 8, color));
        card.addView(image, new LinearLayout.LayoutParams(dp(72), dp(88)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        copy.addView(label(title, 17, INK, true));
        copy.addView(label(reason, 14, MUTED, false));
        copy.addView(label(price, 15, FOREST, true));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return card;
    }

    private TextView colorTile(String name, int color) {
        TextView tile = label(name, 13, readable(color), true);
        tile.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        tile.setPadding(dp(8), dp(8), dp(8), dp(8));
        tile.setBackground(round(color, 8, color));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(82), 1);
        params.setMargins(0, 0, dp(8), dp(8));
        tile.setLayoutParams(params);
        return tile;
    }

    private LinearLayout scoreBar(String name, int value, int color) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(10), 0, 0);
        wrap.addView(label(name + "  " + value + "%", 15, INK, true));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(value);
        progress.getProgressDrawable().setTint(color);
        wrap.addView(progress, new LinearLayout.LayoutParams(-1, dp(18)));
        return wrap;
    }

    private LinearLayout pref(String label, String value) {
        LinearLayout row = panel(SURFACE);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(label(label, 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(label(value, 15, MUTED, false));
        return row;
    }

    private TextView iconBox(String text, int bg, int fg) {
        TextView view = label(text, 26, fg, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(bg, 8, bg));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
        return view;
    }

    private void addNavButton(String label, int index) {
        TextView button = label(label, 12, selectedTab == index ? FOREST : MUTED, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(false);
        button.setBackground(round(selectedTab == index ? MIST : SURFACE, 22, selectedTab == index ? MIST : SURFACE));
        button.setOnClickListener(v -> renderTab(index));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        nav.addView(button, params);
    }

    private int readable(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        return luminance > 0.58 ? INK : Color.WHITE;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int screenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int previewHeight() {
        return Math.max(dp(210), Math.min(dp(300), Math.round(screenWidth() * 0.56f)));
    }

    private int itemImageHeight() {
        return Math.max(dp(112), Math.min(dp(148), Math.round(screenWidth() * 0.18f)));
    }

    private int cardWidth() {
        return (screenWidth() - dp(50)) / 2;
    }

    private static class ExtractionResult {
        final Bitmap bitmap;
        final String itemName;
        final String message;

        ExtractionResult(Bitmap bitmap, String itemName, String message) {
            this.bitmap = bitmap;
            this.itemName = itemName;
            this.message = message;
        }
    }

    private static class DetectionResult {
        final Rect rect;
        final String name;
        final double score;

        DetectionResult(Rect rect, String name, double score) {
            this.rect = rect;
            this.name = name;
            this.score = score;
        }
    }

    private static class SavedClothingItem {
        final String name;
        final String meta;
        final Bitmap image;

        SavedClothingItem(String name, String meta, Bitmap image) {
            this.name = name;
            this.meta = meta;
            this.image = image;
        }
    }
}
