import base64
import io
import os
from dataclasses import dataclass
from typing import Optional

import numpy as np
import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from PIL import Image

try:
    import cv2
except Exception:  # pragma: no cover - optional runtime dependency
    cv2 = None

try:
    from rembg import remove as remove_background_ai
except Exception:  # pragma: no cover - optional runtime dependency
    remove_background_ai = None


app = FastAPI(title="AI Wardrobe Backend")


class ExtractRequest(BaseModel):
    image_base64: str


class ExtractResponse(BaseModel):
    label: str
    image_base64: str
    message: str
    source: str


@dataclass
class Detection:
    label: str
    box: Optional[tuple[int, int, int, int]]
    score: float = 0.0


CLOTHING_WORDS = {
    "shirt": "Shirt",
    "t-shirt": "T-Shirt",
    "tee": "T-Shirt",
    "dress": "Dress",
    "suit": "Suit",
    "tie": "Tie",
    "shoe": "Shoes",
    "sneaker": "Sneakers",
    "boot": "Boots",
    "footwear": "Shoes",
    "jacket": "Jacket",
    "coat": "Coat",
    "blazer": "Blazer",
    "hoodie": "Hoodie",
    "sweater": "Sweater",
    "pants": "Pants",
    "trousers": "Pants",
    "jeans": "Jeans",
    "shorts": "Shorts",
    "skirt": "Skirt",
    "hat": "Hat",
    "cap": "Cap",
    "bag": "Bag",
    "backpack": "Backpack",
    "sock": "Socks",
    "clothing": "Clothing Item",
    "apparel": "Clothing Item",
}


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/extract", response_model=ExtractResponse)
def extract(request: ExtractRequest):
    try:
        image = decode_image(request.image_base64)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Invalid image_base64") from exc

    detection = detect_clothing_with_google(image)
    if detection.box is None:
        detection = Detection(label=detection.label, box=edge_crop_box(image), score=detection.score)

    cropped = crop_image(image, detection.box)
    transparent = remove_background(cropped)
    avatar_ready = center_on_transparent_canvas(transparent, 640, 640)

    return ExtractResponse(
        label=detection.label,
        image_base64=encode_png(avatar_ready),
        message="AI backend recognized, cropped, removed background, and prepared a 2D avatar-ready item",
        source="backend",
    )


def decode_image(image_base64: str) -> Image.Image:
    if "," in image_base64:
        image_base64 = image_base64.split(",", 1)[1]
    raw = base64.b64decode(image_base64)
    return Image.open(io.BytesIO(raw)).convert("RGB")


def encode_png(image: Image.Image) -> str:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("utf-8")


def detect_clothing_with_google(image: Image.Image) -> Detection:
    api_key = os.getenv("GOOGLE_CLOUD_VISION_API_KEY", "").strip()
    if not api_key:
        return Detection(label="Clothing Item", box=None)

    image_base64 = encode_jpeg(image)
    payload = {
        "requests": [
            {
                "image": {"content": image_base64},
                "features": [
                    {"type": "OBJECT_LOCALIZATION", "maxResults": 20},
                    {"type": "LABEL_DETECTION", "maxResults": 20},
                ],
            }
        ]
    }
    url = f"https://vision.googleapis.com/v1/images:annotate?key={api_key}"
    try:
        response = requests.post(url, json=payload, timeout=45)
        response.raise_for_status()
        data = response.json()["responses"][0]
    except Exception:
        return Detection(label="Clothing Item", box=None)

    label = label_from_annotations(data)
    width, height = image.size
    best = Detection(label=label, box=None)
    for item in data.get("localizedObjectAnnotations", []):
        name = item.get("name", "")
        score = float(item.get("score", 0.0))
        normalized = normalize_label(name)
        if normalized is None or score < best.score:
            continue
        vertices = item["boundingPoly"].get("normalizedVertices", [])
        xs = [float(v.get("x", 0.0)) for v in vertices]
        ys = [float(v.get("y", 0.0)) for v in vertices]
        if not xs or not ys:
            continue
        left = max(0, min(width - 1, round(min(xs) * width)))
        top = max(0, min(height - 1, round(min(ys) * height)))
        right = max(left + 1, min(width, round(max(xs) * width)))
        bottom = max(top + 1, min(height, round(max(ys) * height)))
        best = Detection(label=normalized, box=(left, top, right, bottom), score=score)

    return best


def encode_jpeg(image: Image.Image) -> str:
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG", quality=90)
    return base64.b64encode(buffer.getvalue()).decode("utf-8")


def label_from_annotations(data: dict) -> str:
    for item in data.get("labelAnnotations", []):
        normalized = normalize_label(item.get("description", ""))
        if normalized is not None:
            return normalized
    return "Clothing Item"


def normalize_label(name: str) -> Optional[str]:
    clean = name.lower()
    for word, label in CLOTHING_WORDS.items():
        if word in clean:
            return label
    return None


def edge_crop_box(image: Image.Image) -> tuple[int, int, int, int]:
    width, height = image.size
    if cv2 is None:
        return color_difference_box(image)

    arr = np.array(image)
    gray = cv2.cvtColor(arr, cv2.COLOR_RGB2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, 40, 120)
    kernel = np.ones((7, 7), np.uint8)
    mask = cv2.dilate(edges, kernel, iterations=2)
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return color_difference_box(image)

    contour = max(contours, key=cv2.contourArea)
    x, y, w, h = cv2.boundingRect(contour)
    pad_x = max(12, int(w * 0.08))
    pad_y = max(12, int(h * 0.08))
    return (
        max(0, x - pad_x),
        max(0, y - pad_y),
        min(width, x + w + pad_x),
        min(height, y + h + pad_y),
    )


def color_difference_box(image: Image.Image) -> tuple[int, int, int, int]:
    arr = np.array(image.convert("RGB"))
    height, width, _ = arr.shape
    corners = np.array([arr[0, 0], arr[0, -1], arr[-1, 0], arr[-1, -1]], dtype=np.float32)
    background = corners.mean(axis=0)
    diff = np.abs(arr.astype(np.float32) - background).sum(axis=2)
    mask = diff > 48
    ys, xs = np.where(mask)
    if len(xs) == 0 or len(ys) == 0:
        return 0, 0, width, height
    left, right = int(xs.min()), int(xs.max())
    top, bottom = int(ys.min()), int(ys.max())
    pad_x = max(12, int((right - left) * 0.08))
    pad_y = max(12, int((bottom - top) * 0.08))
    return (
        max(0, left - pad_x),
        max(0, top - pad_y),
        min(width, right + pad_x),
        min(height, bottom + pad_y),
    )


def crop_image(image: Image.Image, box: Optional[tuple[int, int, int, int]]) -> Image.Image:
    if box is None:
        return image
    return image.crop(box)


def remove_background(image: Image.Image) -> Image.Image:
    if remove_background_ai is not None:
        try:
            return remove_background_ai(image).convert("RGBA")
        except Exception:
            pass

    if cv2 is not None:
        try:
            return grabcut_background_removal(image)
        except Exception:
            pass

    return color_key_background_removal(image)


def grabcut_background_removal(image: Image.Image) -> Image.Image:
    arr = np.array(image.convert("RGB"))
    height, width, _ = arr.shape
    mask = np.zeros((height, width), np.uint8)
    rect = (8, 8, max(1, width - 16), max(1, height - 16))
    bg_model = np.zeros((1, 65), np.float64)
    fg_model = np.zeros((1, 65), np.float64)
    cv2.grabCut(arr, mask, rect, bg_model, fg_model, 5, cv2.GC_INIT_WITH_RECT)
    foreground = np.where((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), 255, 0).astype("uint8")
    rgba = np.dstack([arr, foreground])
    return Image.fromarray(rgba, "RGBA")


def color_key_background_removal(image: Image.Image) -> Image.Image:
    rgba = np.array(image.convert("RGBA"))
    rgb = rgba[:, :, :3].astype(np.float32)
    corners = np.array([rgb[0, 0], rgb[0, -1], rgb[-1, 0], rgb[-1, -1]], dtype=np.float32)
    background = corners.mean(axis=0)
    diff = np.abs(rgb - background).sum(axis=2)
    rgba[:, :, 3] = np.where(diff > 48, 255, 0).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def center_on_transparent_canvas(image: Image.Image, width: int, height: int) -> Image.Image:
    image = image.convert("RGBA")
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    scale = min(width * 0.86 / image.width, height * 0.86 / image.height)
    target = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    resized = image.resize(target, Image.Resampling.LANCZOS)
    left = (width - resized.width) // 2
    top = (height - resized.height) // 2
    canvas.alpha_composite(resized, (left, top))
    return canvas
