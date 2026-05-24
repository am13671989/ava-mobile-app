import base64
import io
import os
import subprocess
import tempfile
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from PIL import Image


app = FastAPI(title="AVA OOTDiffusion Service")


class OOTDiffusionRequest(BaseModel):
    model_image_base64: str
    garment_image_base64: str
    category: int = 0
    category_label: str = "upperbody"
    sex: str = ""
    occasion: str = ""
    style: str = ""
    prompt: str = ""


class OOTDiffusionResponse(BaseModel):
    image_base64: str
    message: str
    source: str = "ootdiffusion"


@app.get("/health")
def health():
    repo = ootdiffusion_repo(required=False)
    return {
        "ok": repo is not None,
        "repo": str(repo) if repo is not None else "",
        "source": "ootdiffusion",
    }


@app.post("/try-on", response_model=OOTDiffusionResponse)
def try_on(request: OOTDiffusionRequest):
    repo = ootdiffusion_repo(required=True)
    run_dir = repo / "run"
    runner = run_dir / "run_ootd.py"
    if not runner.exists():
        raise HTTPException(
            status_code=500,
            detail=f"OOTDiffusion runner not found: {runner}",
        )

    output_dir = run_dir / "images_output"
    output_dir.mkdir(parents=True, exist_ok=True)

    model_type = select_model_type(request.category)
    python_exe = os.getenv("OOTDIFFUSION_PYTHON", "python").strip() or "python"
    scale = os.getenv("OOTDIFFUSION_SCALE", "2.0").strip() or "2.0"
    sample = os.getenv("OOTDIFFUSION_SAMPLE", "1").strip() or "1"
    timeout = int(os.getenv("OOTDIFFUSION_TIMEOUT", "600"))

    with tempfile.TemporaryDirectory(prefix="ava_ootd_") as tmp:
        tmp_dir = Path(tmp)
        model_path = tmp_dir / "avatar.png"
        cloth_path = tmp_dir / "garment.png"
        decode_image(request.model_image_base64).save(model_path)
        decode_image(request.garment_image_base64).save(cloth_path)

        before = output_snapshot(output_dir)
        command = [
            python_exe,
            str(runner),
            "--model_path",
            str(model_path),
            "--cloth_path",
            str(cloth_path),
            "--model_type",
            model_type,
            "--category",
            str(request.category),
            "--scale",
            scale,
            "--sample",
            sample,
        ]
        completed = subprocess.run(
            command,
            cwd=str(run_dir),
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        if completed.returncode != 0:
            detail = tail_text(completed.stderr or completed.stdout)
            raise HTTPException(status_code=500, detail=f"OOTDiffusion failed: {detail}")

        result_path = newest_output(output_dir, model_type, before)
        if result_path is None:
            detail = tail_text(completed.stdout + "\n" + completed.stderr)
            raise HTTPException(status_code=500, detail=f"OOTDiffusion produced no output image. {detail}")

        result = Image.open(result_path).convert("RGB")

    return OOTDiffusionResponse(
        image_base64=encode_png(result),
        message=(
            f"OOTDiffusion virtual try-on completed "
            f"({request.sex} | {request.occasion} | {request.style} | {request.category_label})."
        ),
    )


def ootdiffusion_repo(required: bool) -> Optional[Path]:
    raw = os.getenv("OOTDIFFUSION_REPO", "").strip()
    if not raw:
        if required:
            raise HTTPException(
                status_code=500,
                detail="Set OOTDIFFUSION_REPO to the local levihsu/OOTDiffusion folder.",
            )
        return None
    repo = Path(raw).expanduser().resolve()
    if not repo.exists():
        if required:
            raise HTTPException(status_code=500, detail=f"OOTDIFFUSION_REPO does not exist: {repo}")
        return None
    return repo


def select_model_type(category: int) -> str:
    requested = os.getenv("OOTDIFFUSION_MODEL_TYPE", "dc").strip().lower() or "dc"
    if requested == "hd" and category != 0:
        return "dc"
    if requested not in {"hd", "dc"}:
        return "dc"
    return requested


def decode_image(image_base64: str) -> Image.Image:
    if "," in image_base64:
        image_base64 = image_base64.split(",", 1)[1]
    raw = base64.b64decode(image_base64)
    return Image.open(io.BytesIO(raw)).convert("RGB")


def encode_png(image: Image.Image) -> str:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("utf-8")


def output_snapshot(output_dir: Path) -> set[Path]:
    return {path.resolve() for path in output_dir.glob("out_*.png")}


def newest_output(output_dir: Path, model_type: str, before: set[Path]) -> Optional[Path]:
    candidates = [
        path
        for path in output_dir.glob(f"out_{model_type}_*.png")
        if path.resolve() not in before and path.is_file()
    ]
    if not candidates:
        candidates = [path for path in output_dir.glob("out_*.png") if path.is_file()]
    if not candidates:
        return None
    return max(candidates, key=lambda path: path.stat().st_mtime)


def tail_text(text: str, limit: int = 1200) -> str:
    clean = text.strip()
    if len(clean) <= limit:
        return clean
    return clean[-limit:]
