@echo off
chcp 65001 >nul
title JetinoPay — Instalador IM30

echo ============================================
echo   JetinoPay — Instalador para IM30
echo ============================================
echo.

REM Caminho do ADB — ajuste se necessario
set ADB=C:\adb\adb.exe

REM Verificar se ADB existe
if not exist "%ADB%" (
    echo [ERRO] ADB nao encontrado em %ADB%
    echo Coloque o adb.exe em C:\adb\ ou edite este arquivo.
    pause
    exit /b 1
)

echo Procurando IM30 conectado...
"%ADB%" devices
echo.

REM Pegar o ID do dispositivo automaticamente
for /f "skip=1 tokens=1" %%i in ('"%ADB%" devices') do (
    if not "%%i"=="" set DEVICE=%%i
)

if "%DEVICE%"=="" (
    echo [ERRO] Nenhum dispositivo encontrado.
    echo Verifique o cabo USB e a depuracao USB no IM30.
    pause
    exit /b 1
)

echo Dispositivo encontrado: %DEVICE%
echo.

REM ============================================
REM Instalar m-SiTef Simulado
REM ============================================
set MSITEF=msitef_3.340-Mobile-117.112.r7_sit6s-demo.apk

if exist "%~dp0%MSITEF%" (
    echo [1/2] Instalando m-SiTef Simulado...
    "%ADB%" -s %DEVICE% install -r "%~dp0%MSITEF%"
    if %errorlevel%==0 (
        echo [OK] m-SiTef instalado com sucesso!
    ) else (
        echo [AVISO] Erro ao instalar m-SiTef. Continuando...
    )
) else (
    echo [AVISO] m-SiTef APK nao encontrado nesta pasta. Pulando...
)

echo.

REM ============================================
REM Instalar JetinoPay
REM ============================================
set JETINOPAY=app-debug.apk

if exist "%~dp0%JETINOPAY%" (
    echo [2/2] Instalando JetinoPay...
    "%ADB%" -s %DEVICE% install -r "%~dp0%JETINOPAY%"
    if %errorlevel%==0 (
        echo [OK] JetinoPay instalado com sucesso!
    ) else (
        echo [ERRO] Falha ao instalar JetinoPay.
        pause
        exit /b 1
    )
) else (
    echo [ERRO] app-debug.apk nao encontrado nesta pasta.
    echo Baixe o APK do GitHub Actions e coloque na mesma pasta deste arquivo.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Abrindo JetinoPay no IM30...
echo ============================================
"%ADB%" -s %DEVICE% shell am start -n br.com.jetinopay.debug/.ui.TesteMdbActivity

echo.
echo Pronto! Verifique a tela do IM30.
echo.
pause
