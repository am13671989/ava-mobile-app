# Google Cloud Vision Setup

This project uses Google Cloud Vision Object Localization to find clothing in a camera/gallery image, crop it, place it on a clean 2D canvas, and then show a confirm button before saving.

## 1. Enable Cloud Vision API

1. Open Google Cloud Console.
2. Create or select a project.
3. Enable **Cloud Vision API** for that project.
4. Create an API key.

Official docs:

- https://cloud.google.com/vision/docs/object-localizer
- https://cloud.google.com/vision/docs/reference/rest/v1/images/annotate

## 2. Add Your API Key

Open:

```text
AIWardrobeNativeAndroid/gradle.properties
```

Find this line:

```properties
GOOGLE_CLOUD_VISION_API_KEY=
```

Paste your key after `=`:

```properties
GOOGLE_CLOUD_VISION_API_KEY=YOUR_GOOGLE_CLOUD_API_KEY
```

## 3. Run In Android Studio

1. Open `AIWardrobeNativeAndroid` in Android Studio.
2. Click **Sync Project with Gradle Files**.
3. Click **Run**.
4. Go to **Wardrobe**.
5. Choose **Camera** or **Gallery**.
6. Tap **Extract Clothing**.
7. Review the extracted 2D image.
8. Tap **Confirm & Save**.

## Important

For MVP testing, an API key inside `gradle.properties` is OK. For production, call Google Cloud from your own backend so users cannot extract your API key from the APK.
