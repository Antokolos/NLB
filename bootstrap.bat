@echo off
rem Сборка NLB на новой машине без Java и Maven (Windows 10/11).
rem
rem   bootstrap.bat               — скачать JDK 21 и Maven (если их ещё нет) и собрать дистрибутив (build.bat)
rem   bootstrap.bat mvn test      — то же окружение для любой команды (здесь — тесты)
rem
rem JDK — Amazon Corretto 21, Maven — 3.9.9; ставятся в .tools\ внутри репозитория (без прав
rem администратора, система не меняется; удалить — папку .tools). Загрузку и проверку контрольных
rem сумм делает tools\bootstrap.ps1 (PowerShell есть в любой Windows 10/11).
setlocal
set "NLB_ROOT=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%NLB_ROOT%tools\bootstrap.ps1" -Tools "%NLB_ROOT%.tools"
if errorlevel 1 exit /b 1
set "JAVA_HOME=%NLB_ROOT%.tools\jdk"
set "PATH=%JAVA_HOME%\bin;%NLB_ROOT%.tools\maven\bin;%PATH%"
cd /d "%NLB_ROOT%"
if "%~1"=="" (
    call build.bat
) else (
    call %*
)
exit /b %errorlevel%
