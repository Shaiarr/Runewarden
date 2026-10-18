@echo off
setlocal
cd /d "%~dp0"
set "RW_HUD_VERIFY_JAVA=%~dp0runtime\bin\java.exe"
if exist "%RW_HUD_VERIFY_JAVA%" goto verify
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "RW_HUD_VERIFY_JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%RW_HUD_VERIFY_JAVA%" goto verify
set "RW_HUD_VERIFY_JAVA=java.exe"
:verify
"%RW_HUD_VERIFY_JAVA%" -Djava.awt.headless=true @Runewarden-windows.args --verify-package
set "RW_HUD_VERIFY_EXIT=%ERRORLEVEL%"
if not "%RW_HUD_VERIFY_EXIT%"=="0" echo Package verification failed. See the output above.
pause
exit /b %RW_HUD_VERIFY_EXIT%
