@echo off
cd /d "%~dp0wardrobe_backend"
python -m uvicorn main:app --host 0.0.0.0 --port 8000
