@echo off
setlocal

echo AVA OOTDiffusion service
echo.
echo Enter the folder where you cloned https://github.com/levihsu/OOTDiffusion
echo Example: C:\Users\Ali\Projects\OOTDiffusion
echo.
set /p OOTDIFFUSION_REPO=OOTDiffusion folder: 

if "%OOTDIFFUSION_REPO%"=="" (
  set "OOTDIFFUSION_REPO=C:\Users\Ali\Projects\OOTDiffusion"
)

set "OOTDIFFUSION_MODEL_TYPE=dc"
set "OOTDIFFUSION_PYTHON=C:\Users\Ali\miniconda3\envs\ootd\python.exe"

cd /d "%~dp0wardrobe_backend"
python -m uvicorn ootdiffusion_service:app --host 0.0.0.0 --port 7860

pause
