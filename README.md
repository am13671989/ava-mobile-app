# AI Wardrobe Native Android UI

This is a native Android Studio project. It does not use Flutter.

## How to Run

1. Open Android Studio.
2. Choose **Open**.
3. Clone or download this repository, then select the project folder:

```text
ava-mobile-app
```

4. Wait for Gradle sync.
5. Start an emulator or connect an Android phone.
6. Press **Run**.

## What Is Included

- Native Android Java app.
- No Flutter.
- No Firebase.
- No backend.
- MediaPipe Image Classifier integration.
- Free/offline TensorFlow Lite clothing detection.
- UI-only prototype for:
  - Home
  - Wardrobe
  - Outfit AI
  - Shop
  - Profile

## Clothing Detection

The Wardrobe page now uses Google MediaPipe Image Classifier:

```gradle
implementation 'com.google.mediapipe:tasks-vision:latest.release'
```

Add your clothing classifier model here:

```text
app/src/main/assets/model.tflite
```

The model should be trained with labels such as:

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

Without `model.tflite`, the app still opens, but scanning will show that the model is missing.

See:

```text
MEDIAPIPE_TFLITE_SETUP.md
```
