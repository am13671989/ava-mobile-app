# MediaPipe / TensorFlow Lite Setup

This app is configured for free, offline image classification using:

```text
TensorFlow Lite
```

## Important

TensorFlow Lite is the engine. It does not magically know clothing categories by itself.

The Android app expects these two files:

```text
app/src/main/assets/model.tflite
app/src/main/assets/labels.txt
```

`model.tflite` is the trained model. `labels.txt` maps output numbers to clothing names.

## Recommended Labels

Train or use a model with labels like:

```text
t_shirt
shirt
shorts
socks
shoes
jacket
pants
hoodie
dress
skirt
bag
hat
```

## Best Free Model Path

Use the included Colab notebook:

```text
TRAIN_TFLITE_CLOTHING_MODEL_COLAB_SCRIPT.py
```

It trains a TensorFlow/Keras image classifier from a fashion dataset and exports:

```text
model.tflite
labels.txt
```

The app reads `labels.txt` directly, so no MediaPipe metadata step is needed.

## App Flow

1. User selects or takes an image.
2. App shows the image preview.
3. User taps `Analyze Image`.
4. TensorFlow Lite loads `app/src/main/assets/model.tflite`.
5. App shows the detected label and confidence.
6. User taps `Save Item`.

## Current Project State

The app has the MediaPipe code already.

You only need to add:

```text
AIWardrobeNativeAndroid/app/src/main/assets/model.tflite
```
