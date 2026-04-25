@echo off
setlocal enabledelayedexpansion

:: ============================================================
::  generate-assetlinks.bat
::
::  Generates assetlinks.json for Digital Asset Links.
::  Requires: Android Studio (uses its bundled keytool)
::
::  Usage:
::    generate-assetlinks.bat
::
::  You will be prompted for your release keystore path and
::  credentials. The debug keystore is found automatically.
:: ============================================================

set PACKAGE_NAME=com.beckersuite.box
set OUTPUT_FILE=assetlinks.json

:: ── Find keytool inside Android Studio's bundled JDK ────────────────────────
set KEYTOOL=
for %%D in (
    "%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe"
    "%ProgramFiles%\Android\Android Studio\jre\bin\keytool.exe"
    "%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe"
    "%ProgramFiles(x86)%\Android\Android Studio\jre\bin\keytool.exe"
    "%LocalAppData%\Programs\Android Studio\jbr\bin\keytool.exe"
    "%LocalAppData%\Programs\Android Studio\jre\bin\keytool.exe"
) do (
    if exist %%D (
        set KEYTOOL=%%~D
        goto :found_keytool
    )
)

echo ERROR: Could not find keytool inside Android Studio.
echo Make sure Android Studio is installed.
exit /b 1

:found_keytool
echo Found keytool: %KEYTOOL%
echo.

:: ── Debug keystore ───────────────────────────────────────────────────────────
set DEBUG_KEYSTORE=%USERPROFILE%\.android\debug.keystore
set DEBUG_ALIAS=androiddebugkey
set DEBUG_PASS=android

if not exist "%DEBUG_KEYSTORE%" (
    echo WARNING: Debug keystore not found at %DEBUG_KEYSTORE%
    echo          Build and run the app in Android Studio at least once to generate it.
    set DEBUG_FP=NOT_FOUND
    goto :get_release
)

echo Extracting debug fingerprint...
set DEBUG_FP=
set TMP_DEBUG_OUT=%TEMP%\assetlinks_debug_%RANDOM%.txt
"%KEYTOOL%" -list -v -keystore "%DEBUG_KEYSTORE%" -alias %DEBUG_ALIAS% -storepass %DEBUG_PASS% > "%TMP_DEBUG_OUT%" 2>nul
for /f "tokens=1* delims=:" %%A in ('findstr /i "SHA256:" "%TMP_DEBUG_OUT%"') do (
    set DEBUG_FP=%%B
    set DEBUG_FP=!DEBUG_FP: =!
)
del "%TMP_DEBUG_OUT%" >nul 2>&1


if "!DEBUG_FP!"=="" (
    echo WARNING: Could not extract debug fingerprint.
    set DEBUG_FP=NOT_FOUND
)
echo Debug SHA-256:   !DEBUG_FP!
echo.

:: ── Release keystore ─────────────────────────────────────────────────────────
:get_release
set /p RELEASE_KEYSTORE="Enter path to your RELEASE keystore (leave blank to skip): "

set RELEASE_FP=
if "!RELEASE_KEYSTORE!"=="" goto :generate

if not exist "!RELEASE_KEYSTORE!" (
    echo WARNING: Release keystore not found at !RELEASE_KEYSTORE!
    goto :generate
)

set /p RELEASE_ALIAS="Enter key alias in release keystore: "
set /p RELEASE_PASS="Enter keystore password: "

echo.
echo Extracting release fingerprint...
set TMP_RELEASE_OUT=%TEMP%\assetlinks_release_%RANDOM%.txt
"%KEYTOOL%" -list -v -keystore "!RELEASE_KEYSTORE!" -alias !RELEASE_ALIAS! -storepass !RELEASE_PASS! > "!TMP_RELEASE_OUT!" 2>nul
for /f "tokens=1* delims=:" %%A in ('findstr /i "SHA256:" "!TMP_RELEASE_OUT!"') do (
    set RELEASE_FP=%%B
    set RELEASE_FP=!RELEASE_FP: =!
)
del "!TMP_RELEASE_OUT!" >nul 2>&1

if "!RELEASE_FP!"=="" (
    echo WARNING: Could not extract release fingerprint. Check alias and password.
)
echo Release SHA-256: !RELEASE_FP!
echo.

:: ── Build fingerprint array ───────────────────────────────────────────────────
:generate
set FP_ARRAY=
set FP_COUNT=0

if not "!DEBUG_FP!"=="NOT_FOUND" if not "!DEBUG_FP!"=="" (
    set FP_ARRAY="!DEBUG_FP!"
    set /a FP_COUNT+=1
)

if not "!RELEASE_FP!"=="" (
    if !FP_COUNT! gtr 0 (
        set FP_ARRAY=!FP_ARRAY!, "!RELEASE_FP!"
    ) else (
        set FP_ARRAY="!RELEASE_FP!"
    )
    set /a FP_COUNT+=1
)

if !FP_COUNT! equ 0 (
    echo ERROR: No fingerprints found. Cannot generate assetlinks.json.
    exit /b 1
)

:: ── Write assetlinks.json ────────────────────────────────────────────────────
(
echo [
echo   {
echo     "relation": ["delegate_permission/common.handle_all_urls"],
echo     "target": {
echo       "namespace": "android_app",
echo       "package_name": "%PACKAGE_NAME%",
echo       "sha256_cert_fingerprints": [
echo         !FP_ARRAY!
echo       ]
echo     }
echo   }
echo ]
) > "%OUTPUT_FILE%"

echo Generated: %OUTPUT_FILE%
echo.
echo Next step:
echo   Host this file at:
echo   https://r.box.beckersuite.com/.well-known/assetlinks.json
echo.
echo Verify with:
echo   https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://r.box.beckersuite.com^&relation=delegate_permission/common.handle_all_urls
echo.

pause
endlocal