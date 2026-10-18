@echo off
setlocal
cd /d "%~dp0"
set "RW_HUD_JAVA=%~dp0runtime\bin\javaw.exe"
if exist "%RW_HUD_JAVA%" goto launch
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "RW_HUD_JAVA=%JAVA_HOME%\bin\javaw.exe"
if exist "%RW_HUD_JAVA%" goto launch
where javaw.exe >nul 2>&1
if errorlevel 1 goto missing
set "RW_HUD_JAVA=javaw.exe"
:launch
start "Runewarden HUD test" "%RW_HUD_JAVA%" @Runewarden-windows.args %*
exit /b 0
:missing
echo Java 17 was not found. Use the Windows x64 package with its bundled runtime.
pause
exit /b 1
