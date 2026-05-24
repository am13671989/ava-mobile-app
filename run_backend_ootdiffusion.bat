@echo off
setlocal

set "OOTDIFFUSION_SERVICE_URL=http://127.0.0.1:7860"

cd /d "%~dp0wardrobe_backend"
python -m uvicorn main:app --host 0.0.0.0 --port 8000

pause
