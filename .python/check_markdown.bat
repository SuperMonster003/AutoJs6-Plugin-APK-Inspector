@echo off
setlocal
cd /d "%~dp0.."
py -3 .python/generate_markdown.py --check
exit /b %errorlevel%
