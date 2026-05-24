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

## OOTDiffusion virtual try-on

The app now uses `OOTDiffusion` as the first/default virtual try-on model.
OOTDiffusion is the model from:

```text
https://github.com/levihsu/OOTDiffusion
```

Because OOTDiffusion needs a GPU environment, run it as a separate service and point this backend to it:

```powershell
set OOTDIFFUSION_SERVICE_URL=http://YOUR_GPU_SERVER:7860
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

Expected service endpoint:

```text
POST /try-on
```

Expected request fields:

```json
{
  "model_image_base64": "...",
  "garment_image_base64": "...",
  "category": 0,
  "category_label": "upperbody",
  "sex": "Woman",
  "occasion": "Office",
  "style": "Classic",
  "prompt": "..."
}
```

OOTDiffusion category mapping:

```text
0 = upperbody
1 = lowerbody
2 = dress
```

Expected response:

```json
{
  "image_base64": "...",
  "message": "OOTDiffusion virtual try-on completed",
  "source": "ootdiffusion"
}
```

## Local geometric try-on pipeline

The file `virtual_tryon_pipeline.py` contains a local Computer Vision fallback for avatar fitting.
It is designed for prototype use when hosted VTON models are unavailable.

Features:

```text
BodyDetector          MediaPipe Pose, optional YOLO pose, silhouette fallback
BodySegmenter         Binary body mask, torso mask, arm/occlusion mask, contours
ClothingProcessor     Background removal, alpha crop, contour, neck anchor estimate
GarmentFitter         Rotation-aware affine fitting and arm-aware compositing
VirtualTryOnPipeline  Single-image and batch runner
```

Example:

```python
from virtual_tryon_pipeline import FitPart, VirtualTryOnPipeline

pipeline = VirtualTryOnPipeline()
result = pipeline.run_files(
    avatar_path="avatar.png",
    cloth_path="shirt.png",
    output_path="avatar_with_shirt.png",
    fit_part=FitPart.UPPER,
)

print(result.landmarks.confidence)
print(result.landmarks.body_angle)
```

Optional upgrades:

```text
mediapipe       improves landmark detection
ultralytics     enables YOLO pose fallback
DensePose       improves body-surface mapping
SAM 2           improves precise person/arm segmentation
Detectron2      improves parsing and human-part segmentation
IDM-VTON/CatVTON/Stable Diffusion VTON for photorealistic final try-on
```

## Simple local shirt fitting endpoint

For fast prototype fitting without GPU VTON, use:

```text
POST /local-fit
```

Request:

```json
{
  "avatar_image_base64": "...",
  "cloth_image_base64": "...",
  "fit_part": "upper",
  "cloth_width_ratio": 0.95,
  "cloth_y_ratio": 0.22,
  "cloth_height_ratio": 0.62
}
```

The endpoint removes light clothing backgrounds, crops to alpha, overlays the garment, and restores the avatar head/hair layer on top.
