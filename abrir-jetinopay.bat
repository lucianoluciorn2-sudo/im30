@echo off
chcp 65001 >nul
title Abrir JetinoPay no IM30

set ADB=C:\adb\adb.exe

echo Reiniciando conexao ADB...
"%ADB%" kill-server >nul 2>&1
timeout /t 2 /nobreak >nul
"%ADB%" start-server >nul 2>&1
timeout /t 3 /nobreak >nul

echo Verificando IM30...
"%ADB%" devices

echo.
echo Abrindo JetinoPay...
"%ADB%" shell am start -n br.com.jetinopay.debug/.ui.TesteMdbActivity

if %errorlevel%==0 (
    echo [OK] Comando enviado! Verifique a tela do IM30.
) else (
    echo.
    echo Tentativa 2 — forcando abertura...
    timeout /t 2 /nobreak >nul
    "%ADB%" shell monkey -p br.com.jetinopay.debug -c android.intent.category.LAUNCHER 1
)

echo.
pause
