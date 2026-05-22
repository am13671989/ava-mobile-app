# AI Wardrobe Backend

This backend receives a camera/gallery image from the Android app and returns an avatar-ready 2D clothing PNG.

Pipeline:

1. Detect clothing label and box with Google Cloud Vision when `GOOGLE_CLOUD_VISION_API_KEY` is set.
2. If no box is found, use OpenCV edge detection to crop the main clothing/object area.
3. Remove background with `rembg` neural network when available.
4. Fall back to OpenCV GrabCut or color-key removal.
5. Return a transparent 640x640 PNG as base64.

## Run

```bash
cd /home/amir/Desktop/AIWardrobeNativeAndroid/wardrobe_backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export GOOGLE_CLOUD_VISION_API_KEY=YOUR_KEY_HERE
uvicorn main:app --host 0.0.0.0 --port 8000
```

## Android Setting

Open:

```text
/home/amir/Desktop/AIWardrobeNativeAndroid/gradle.properties
```

For Android emulator:

```properties
WARDROBE_BACKEND_BASE_URL=http://10.0.2.2:8000
```

For a real phone, use your computer IP address:

```properties
WARDROBE_BACKEND_BASE_URL=http://YOUR_COMPUTER_IP:8000
```

Then sync and run Android Studio.

## Replicate virtual try-on

To use the hosted Replicate VTON path on Windows, run this from the project root:

```powershell
.\run_backend_replicate.bat
```

The script asks for `REPLICATE_API_TOKEN` at launch, so the token is not stored in Git.
If no token is provided, `/try-on` returns the local reference demo instead of a real AI try-on.
