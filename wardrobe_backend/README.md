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

Because OOTDiffusion needs its own model files and usually needs a GPU environment, run it as a separate service and point this backend to it.

First clone and install OOTDiffusion by following the official project instructions. Make sure the required checkpoints are downloaded in the folders expected by that project.

Then start the local AVA wrapper service from this Android project root:

```powershell
.\run_ootdiffusion_service.bat
```

When the script asks for the OOTDiffusion folder, enter the local clone path, for example:

```text
C:\Users\Ali\Projects\OOTDiffusion
```

The wrapper runs here:

```text
http://127.0.0.1:7860
```

In a second terminal, start the normal AVA backend with OOTDiffusion enabled:

```powershell
.\run_backend_ootdiffusion.bat
```

That script sets a hosted Hugging Face fallback first, then the local OOTDiffusion service:

```powershell
HF_VTON_SPACE=yisol/IDM-VTON
HF_VTON_API_NAME=/tryon
HF_VTON_DENOISE_STEPS=20
OOTDIFFUSION_SERVICE_URL=http://127.0.0.1:7860
```

The hosted fallback is useful on computers without an NVIDIA CUDA GPU. If the hosted Space is available, the app receives a real virtual try-on image. If the hosted Space is down or busy, the backend shows the error and falls back to the reference-set demo.

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

The wrapper calls OOTDiffusion's `run/run_ootd.py` script with:

```text
--model_path
--cloth_path
--model_type dc
--category 0/1/2
--scale 2.0
--sample 1
```

Optional environment settings:

```text
OOTDIFFUSION_REPO        local OOTDiffusion folder
OOTDIFFUSION_PYTHON     Python executable for the OOTDiffusion environment
OOTDIFFUSION_MODEL_TYPE dc or hd, default dc
OOTDIFFUSION_SCALE      default 2.0
OOTDIFFUSION_SAMPLE     default 1
OOTDIFFUSION_TIMEOUT    default 600 seconds
```

Local Windows install used on this computer:

```text
OOTDIFFUSION_REPO=C:\Users\Ali\Projects\OOTDiffusion
OOTDIFFUSION_PYTHON=C:\Users\Ali\miniconda3\envs\ootd\python.exe
```

Important: the official OOTDiffusion code is CUDA-first and calls `torch.cuda.set_device`.
If `torch.cuda.is_available()` is false, the environment can be installed and checked, but real generation needs an NVIDIA CUDA GPU or a hosted GPU server.

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
