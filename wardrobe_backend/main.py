import base64
import io
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import numpy as np
import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from PIL import Image, ImageDraw, ImageFont

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


class TryOnRequest(BaseModel):
    person_image_base64: str
    outfit_image_base64: str
    model: str = "IDM-VTON"
    sex: str = "Woman"
    occasion: str = "Casual"
    style: str = "Classic"
    prompt: str = ""


class TryOnResponse(BaseModel):
    image_base64: str
    message: str
    model: str
    source: str


class LocalFitRequest(BaseModel):
    avatar_image_base64: str
    cloth_image_base64: str
    fit_part: str = "upper"
    cloth_width_ratio: float = 0.95
    cloth_y_ratio: float = 0.22
    cloth_height_ratio: float = 0.62


class LocalFitResponse(BaseModel):
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


@app.post("/try-on", response_model=TryOnResponse)
def try_on(request: TryOnRequest):
    model = normalize_vton_model(request.model)
    try:
        person = decode_image(request.person_image_base64)
        outfit = decode_image(request.outfit_image_base64)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Invalid try-on image payload") from exc

    hosted_space = os.getenv("HF_VTON_SPACE", "").strip()
    if hosted_space:
        result = call_huggingface_vton_space(hosted_space, person, outfit, request, model)
        if result is not None:
            return result

    ootdiffusion = os.getenv("OOTDIFFUSION_SERVICE_URL", "").strip()
    if ootdiffusion:
        result = call_ootdiffusion_service(ootdiffusion, person, outfit, request)
        if result is not None:
            return result

    external = os.getenv("VTON_SERVICE_URL", "").strip()
    if external:
        result = call_external_vton_service(external, request, model)
        if result is not None:
            return result

    replicate_token_present = bool(os.getenv("REPLICATE_API_TOKEN", "").strip())
    replicate_result = call_replicate_vton(person, outfit, request, model)
    if replicate_result is not None:
        return replicate_result

    demo = compose_reference_try_on_demo(person, outfit, request, model)
    message = (
            "Replicate VTON call failed. Check REPLICATE_API_TOKEN, account billing, and model availability. "
            "This response is a reference-set demo."
            if replicate_token_present
            else (
            f"{model} endpoint contract is ready. Set OOTDIFFUSION_SERVICE_URL to a GPU service "
            "running https://github.com/levihsu/OOTDiffusion, or set VTON_SERVICE_URL for another VTON service. "
            "This response is a reference-set demo."
        )
    )
    return TryOnResponse(
        image_base64=encode_png(demo),
        message=message,
        model=model,
        source="reference-demo",
    )


@app.post("/local-fit", response_model=LocalFitResponse)
def local_fit(request: LocalFitRequest):
    try:
        avatar = decode_image_rgba(request.avatar_image_base64)
        cloth = decode_image_rgba(request.cloth_image_base64)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Invalid local-fit image payload") from exc

    try:
        result = simple_upper_body_fit(
            avatar,
            cloth,
            fit_part=request.fit_part,
            cloth_width_ratio=request.cloth_width_ratio,
            cloth_y_ratio=request.cloth_y_ratio,
            cloth_height_ratio=request.cloth_height_ratio,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Local fit failed: {exc}") from exc

    return LocalFitResponse(
        image_base64=encode_png(result),
        message="Local upper/lower body fitting completed with background removal and head/hair occlusion.",
        source="local-fit",
    )


def decode_image(image_base64: str) -> Image.Image:
    if "," in image_base64:
        image_base64 = image_base64.split(",", 1)[1]
    raw = base64.b64decode(image_base64)
    return Image.open(io.BytesIO(raw)).convert("RGB")


def decode_image_rgba(image_base64: str) -> Image.Image:
    if "," in image_base64:
        image_base64 = image_base64.split(",", 1)[1]
    raw = base64.b64decode(image_base64)
    return Image.open(io.BytesIO(raw)).convert("RGBA")


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


VTON_MODELS = {
    "idm-vton": "IDM-VTON",
    "ootdiffusion": "OOTDiffusion",
    "viton-hd": "VITON-HD",
    "stableviton": "StableVITON",
    "hr-viton": "HR-VITON",
}


def normalize_vton_model(name: str) -> str:
    key = name.lower().replace("_", "-").replace(" ", "")
    if key == "oot-diffusion":
        key = "ootdiffusion"
    if key == "stable-viton":
        key = "stableviton"
    return VTON_MODELS.get(key, "IDM-VTON")


def call_external_vton_service(url: str, request: TryOnRequest, model: str) -> Optional[TryOnResponse]:
    payload = request.dict()
    payload["model"] = model
    try:
        response = requests.post(url.rstrip("/") + "/try-on", json=payload, timeout=240)
        response.raise_for_status()
        data = response.json()
        image_base64 = data.get("image_base64", "")
        if not image_base64:
            return None
        return TryOnResponse(
            image_base64=image_base64,
            message=data.get("message", f"{model} virtual try-on completed by external service"),
            model=data.get("model", model),
            source=data.get("source", "external-vton"),
        )
    except Exception:
        return None


def call_ootdiffusion_service(url: str, person: Image.Image, outfit: Image.Image, request: TryOnRequest) -> Optional[TryOnResponse]:
    payload = {
        "model_image_base64": encode_png(person),
        "garment_image_base64": encode_png(outfit),
        "category": ootdiffusion_category(request),
        "category_label": ootdiffusion_category_label(request),
        "sex": request.sex,
        "occasion": request.occasion,
        "style": request.style,
        "prompt": request.prompt,
    }
    try:
        response = requests.post(url.rstrip("/") + "/try-on", json=payload, timeout=300)
        response.raise_for_status()
        data = response.json()
        image_base64 = data.get("image_base64", "")
        if not image_base64:
            return None
        return TryOnResponse(
            image_base64=image_base64,
            message=data.get("message", "OOTDiffusion virtual try-on completed."),
            model="OOTDiffusion",
            source=data.get("source", "ootdiffusion"),
        )
    except requests.HTTPError as error:
        detail = response_error_detail(error)
        demo = compose_reference_try_on_demo(person, outfit, request, "OOTDiffusion")
        return TryOnResponse(
            image_base64=encode_png(demo),
            message=f"OOTDiffusion backend failed: {detail}. Showing reference-set demo.",
            model="OOTDiffusion",
            source="reference-demo",
        )
    except Exception as error:
        demo = compose_reference_try_on_demo(person, outfit, request, "OOTDiffusion")
        return TryOnResponse(
            image_base64=encode_png(demo),
            message=f"OOTDiffusion backend error: {error.__class__.__name__}. Showing reference-set demo.",
            model="OOTDiffusion",
            source="reference-demo",
        )


def response_error_detail(error: requests.HTTPError) -> str:
    response = error.response
    if response is None:
        return error.__class__.__name__
    try:
        data = response.json()
        detail = data.get("detail") or data.get("message") or response.text
    except Exception:
        detail = response.text
    detail = str(detail).replace("\n", " ").strip()
    return detail[:260] if detail else f"HTTP {response.status_code}"


def call_huggingface_vton_space(
    space: str,
    person: Image.Image,
    outfit: Image.Image,
    request: TryOnRequest,
    model: str,
) -> Optional[TryOnResponse]:
    try:
        from gradio_client import Client, handle_file
    except Exception:
        return hosted_vton_demo(person, outfit, request, model, "gradio_client is not installed")

    api_name = os.getenv("HF_VTON_API_NAME", "/tryon").strip() or "/tryon"
    denoise_steps = float(os.getenv("HF_VTON_DENOISE_STEPS", "20"))
    seed = float(os.getenv("HF_VTON_SEED", "42"))
    garment_description = request.prompt or f"{request.style} {request.occasion} outfit"

    try:
        with tempfile.TemporaryDirectory(prefix="ava_hf_vton_") as tmp:
            tmp_dir = Path(tmp)
            person_path = tmp_dir / "person.png"
            garment_path = tmp_dir / "garment.png"
            person.save(person_path)
            outfit.save(garment_path)

            client = Client(space)
            result = client.predict(
                {
                    "background": handle_file(str(person_path)),
                    "layers": [],
                    "composite": None,
                },
                handle_file(str(garment_path)),
                garment_description,
                True,
                False,
                denoise_steps,
                seed,
                api_name=api_name,
            )
            output_path = gradio_output_path(result)
            if output_path is None:
                return hosted_vton_demo(person, outfit, request, model, "hosted Space returned no image")
            generated = Image.open(output_path).convert("RGB")
            return TryOnResponse(
                image_base64=encode_png(generated),
                message=f"Hosted virtual try-on completed with Hugging Face Space {space}.",
                model=f"{model} hosted",
                source="huggingface-space",
            )
    except Exception as error:
        detail = f"{error.__class__.__name__}: {str(error)[:180]}"
        return hosted_vton_demo(person, outfit, request, model, detail)


def gradio_output_path(result) -> Optional[str]:
    first = result[0] if isinstance(result, (tuple, list)) and result else result
    if isinstance(first, str):
        return first
    if isinstance(first, dict):
        path = first.get("path") or first.get("name")
        return path if isinstance(path, str) else None
    return None


def hosted_vton_demo(
    person: Image.Image,
    outfit: Image.Image,
    request: TryOnRequest,
    model: str,
    detail: str,
) -> TryOnResponse:
    demo = compose_reference_try_on_demo(person, outfit, request, model)
    return TryOnResponse(
        image_base64=encode_png(demo),
        message=f"Hosted virtual try-on failed: {detail}. Showing reference-set demo.",
        model=model,
        source="reference-demo",
    )


def ootdiffusion_category(request: TryOnRequest) -> int:
    text = f"{request.occasion} {request.style} {request.prompt}".lower()
    if any(word in text for word in ("dress", "robe", "gown", "evening")):
        return 2
    if any(word in text for word in ("trouser", "pants", "jeans", "shorts", "skirt", "legging")):
        return 1
    return 0


def ootdiffusion_category_label(request: TryOnRequest) -> str:
    category = ootdiffusion_category(request)
    if category == 2:
        return "dress"
    if category == 1:
        return "lowerbody"
    return "upperbody"


def call_replicate_vton(person: Image.Image, outfit: Image.Image, request: TryOnRequest, model: str) -> Optional[TryOnResponse]:
    token = os.getenv("REPLICATE_API_TOKEN", "").strip()
    if not token:
        return None

    # Default: Flux VTON on Replicate. You can override this with a full Replicate
    # version id if you later choose another hosted VTON model.
    version = os.getenv(
        "REPLICATE_VTON_VERSION",
        "a02643ce418c0e12bad371c4adbfaec0dd1cb34b034ef37650ef205f92ad6199",
    ).strip()
    garment_part = replicate_garment_part(request)
    payload = {
        "version": version,
        "input": {
            "image": image_data_uri(person),
            "garment": image_data_uri(outfit),
            "part": garment_part,
        },
    }

    try:
        response = requests.post(
            "https://api.replicate.com/v1/predictions",
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
                "Prefer": "wait=60",
            },
            json=payload,
            timeout=90,
        )
        response.raise_for_status()
        prediction = response.json()
        prediction = wait_for_replicate_prediction(prediction, token)
        output_url = replicate_output_url(prediction)
        if not output_url:
            return None
        image_response = requests.get(output_url, timeout=90)
        image_response.raise_for_status()
        result = Image.open(io.BytesIO(image_response.content)).convert("RGB")
        return TryOnResponse(
            image_base64=encode_png(result),
            message=f"{model} request completed with Replicate hosted VTON ({garment_part}).",
            model=model,
            source="replicate",
        )
    except requests.HTTPError as error:
        detail = replicate_error_detail(error)
        demo = compose_reference_try_on_demo(person, outfit, request, model)
        return TryOnResponse(
            image_base64=encode_png(demo),
            message=f"Replicate VTON failed: {detail}. This response is a reference-set demo.",
            model=model,
            source="reference-demo",
        )
    except Exception:
        return None


def replicate_error_detail(error: requests.HTTPError) -> str:
    response = error.response
    if response is None:
        return error.__class__.__name__
    try:
        data = response.json()
        return data.get("detail") or data.get("title") or response.text[:240]
    except Exception:
        return response.text[:240] or f"HTTP {response.status_code}"


def wait_for_replicate_prediction(prediction: dict, token: str) -> dict:
    status = prediction.get("status")
    get_url = prediction.get("urls", {}).get("get")
    if status in {"succeeded", "failed", "canceled"} or not get_url:
        return prediction
    for _ in range(24):
        response = requests.get(get_url, headers={"Authorization": f"Bearer {token}"}, timeout=30)
        response.raise_for_status()
        prediction = response.json()
        status = prediction.get("status")
        if status in {"succeeded", "failed", "canceled"}:
            return prediction
    return prediction


def replicate_output_url(prediction: dict) -> Optional[str]:
    if prediction.get("status") != "succeeded":
        return None
    output = prediction.get("output")
    if isinstance(output, str):
        return output
    if isinstance(output, list) and output:
        first = output[0]
        return first if isinstance(first, str) else None
    return None


def replicate_garment_part(request: TryOnRequest) -> str:
    text = f"{request.occasion} {request.style} {request.prompt}".lower()
    if any(word in text for word in ("trouser", "pants", "jeans", "shorts", "skirt", "legging")):
        return "lower_body"
    if any(word in text for word in ("dress", "robe", "gown", "evening")):
        return "dresses"
    return "upper_body"


def image_data_uri(image: Image.Image) -> str:
    return "data:image/png;base64," + encode_png(image)


def simple_upper_body_fit(
    avatar: Image.Image,
    cloth: Image.Image,
    fit_part: str = "upper",
    cloth_width_ratio: float = 0.95,
    cloth_y_ratio: float = 0.22,
    cloth_height_ratio: float = 0.62,
) -> Image.Image:
    avatar_rgba = np.array(avatar.convert("RGBA"), dtype=np.uint8)
    cloth_rgba = np.array(cloth.convert("RGBA"), dtype=np.uint8)

    if cloth_rgba[:, :, 3].min() >= 250:
        cloth_rgba = remove_light_background_np(cloth_rgba)

    cloth_rgba = crop_to_alpha_np(cloth_rgba)
    avatar_h, avatar_w = avatar_rgba.shape[:2]

    if fit_part.lower().startswith("lower"):
        target_w = int(avatar_w * 0.72)
        target_h = int(avatar_h * 0.44)
        x = int((avatar_w - target_w) / 2)
        y = int(avatar_h * 0.48)
        protected_mask = np.zeros((avatar_h, avatar_w), dtype=np.uint8)
    else:
        target_w = int(avatar_w * cloth_width_ratio)
        target_h = int(avatar_h * cloth_height_ratio)
        x = int((avatar_w - target_w) / 2)
        y = int(avatar_h * cloth_y_ratio)
        protected_mask = create_head_hair_mask_np(avatar_rgba)

    if cv2 is not None:
        cloth_resized = cv2.resize(cloth_rgba, (target_w, target_h), interpolation=cv2.INTER_AREA)
    else:
        cloth_resized = np.array(Image.fromarray(cloth_rgba, "RGBA").resize((target_w, target_h), Image.Resampling.LANCZOS))

    fitted = overlay_rgba_np(avatar_rgba, cloth_resized, x, y)
    if protected_mask.any():
        fitted = np.where(protected_mask[:, :, None] == 255, avatar_rgba, fitted)
    return Image.fromarray(fitted, "RGBA")


def remove_light_background_np(rgba: np.ndarray, threshold: int = 245) -> np.ndarray:
    rgb = rgba[:, :, :3]
    if cv2 is not None:
        gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
        alpha = np.where(gray < threshold, 255, 0).astype(np.uint8)
        alpha = cv2.GaussianBlur(alpha, (5, 5), 0)
    else:
        gray = rgb.mean(axis=2)
        alpha = np.where(gray < threshold, 255, 0).astype(np.uint8)
    output = rgba.copy()
    output[:, :, 3] = alpha
    return output


def crop_to_alpha_np(rgba: np.ndarray) -> np.ndarray:
    alpha = rgba[:, :, 3]
    ys, xs = np.where(alpha > 8)
    if len(xs) == 0 or len(ys) == 0:
        return rgba
    left, right = xs.min(), xs.max() + 1
    top, bottom = ys.min(), ys.max() + 1
    return rgba[top:bottom, left:right]


def overlay_rgba_np(background: np.ndarray, foreground: np.ndarray, x: int, y: int) -> np.ndarray:
    bg = background.copy()
    fg_h, fg_w = foreground.shape[:2]
    bg_h, bg_w = bg.shape[:2]
    x1 = max(0, x)
    y1 = max(0, y)
    x2 = min(bg_w, x + fg_w)
    y2 = min(bg_h, y + fg_h)
    if x1 >= x2 or y1 >= y2:
        return bg
    fx1 = x1 - x
    fy1 = y1 - y
    fx2 = fx1 + (x2 - x1)
    fy2 = fy1 + (y2 - y1)
    fg_crop = foreground[fy1:fy2, fx1:fx2].astype(np.float32)
    alpha = fg_crop[:, :, 3:4] / 255.0
    bg_crop = bg[y1:y2, x1:x2].astype(np.float32)
    bg_crop[:, :, :3] = alpha * fg_crop[:, :, :3] + (1.0 - alpha) * bg_crop[:, :, :3]
    bg_crop[:, :, 3:4] = np.maximum(bg_crop[:, :, 3:4], fg_crop[:, :, 3:4] * alpha)
    bg[y1:y2, x1:x2] = np.clip(bg_crop, 0, 255).astype(np.uint8)
    return bg


def create_head_hair_mask_np(avatar_rgba: np.ndarray) -> np.ndarray:
    height, width = avatar_rgba.shape[:2]
    mask = np.zeros((height, width), dtype=np.uint8)
    mask[: int(height * 0.38), :] = 255
    mask[: int(height * 0.55), : int(width * 0.28)] = 255
    mask[: int(height * 0.55), int(width * 0.72):] = 255
    alpha = avatar_rgba[:, :, 3]
    return np.where(alpha > 8, mask, 0).astype(np.uint8)


def compose_reference_try_on_demo(person: Image.Image, outfit: Image.Image, request: TryOnRequest, model: str) -> Image.Image:
    canvas = Image.new("RGB", (1024, 1024), (252, 248, 241))
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((24, 24, 1000, 1000), radius=34, outline=(218, 207, 192), width=3)

    person_panel = center_image(person, 410, 720)
    outfit_panel = center_image(outfit, 456, 720)
    canvas.paste(person_panel, (54, 142))
    canvas.paste(outfit_panel, (514, 142))

    font_big = load_font(42)
    font_mid = load_font(28)
    font_small = load_font(22)
    draw.text((512, 58), f"{model} Virtual Try-On", anchor="mm", fill=(32, 36, 33), font=font_big)
    draw.text((512, 104), f"{request.sex} | {request.occasion} | {request.style}", anchor="mm", fill=(86, 93, 85), font=font_mid)
    draw.text((259, 892), "Avatar / user photo", anchor="mm", fill=(54, 88, 76), font=font_small)
    draw.text((742, 892), "Selected reference outfit set", anchor="mm", fill=(54, 88, 76), font=font_small)
    draw.text(
        (512, 946),
        "Real AI try-on will be generated by the configured GPU VTON service.",
        anchor="mm",
        fill=(114, 119, 111),
        font=font_small,
    )
    return canvas


def center_image(image: Image.Image, width: int, height: int) -> Image.Image:
    image = image.convert("RGB")
    canvas = Image.new("RGB", (width, height), (255, 252, 247))
    scale = min(width * 0.92 / image.width, height * 0.92 / image.height)
    target = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    resized = image.resize(target, Image.Resampling.LANCZOS)
    left = (width - resized.width) // 2
    top = (height - resized.height) // 2
    canvas.paste(resized, (left, top))
    return canvas


def load_font(size: int):
    for path in ("arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(path, size=size)
        except Exception:
            pass
    return ImageFont.load_default()


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
