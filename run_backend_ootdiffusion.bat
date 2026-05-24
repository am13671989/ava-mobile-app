@echo off
setlocal

set "OOTDIFFUSION_SERVICE_URL=http://127.0.0.1:7860"
set "HF_VTON_SPACE=yisol/IDM-VTON"
set "HF_VTON_API_NAME=/tryon"
set "HF_VTON_DENOISE_STEPS=20"

cd /d "%~dp0wardrobe_backend"
python -m uvicorn main:app --host 0.0.0.0 --port 8000

pause
