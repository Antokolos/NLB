@echo off
:: Веб-плеер книг NLB (com.nlbhub.nlb.web.player.PlayerServer): играть книги в браузере.
:: Использование: play-web.bat <каталог с книгами> [--port 8111] [--host localhost] [--config <config.xml>]
:: В каталоге — каталоги книг и пакеты книг <книга>.nlbpack; плеер — http://localhost:8111/play/
:: (--host 0.0.0.0 — открыть плеер для других компьютеров сети)
setlocal
set "DIST=%~dp0"

:: Java из дистрибутива; если её нет — из JAVA_HOME или из PATH
if exist "%DIST%runtime\bin\java.exe" (
    set "JAVA=%DIST%runtime\bin\java.exe"
) else if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)

:: Настройки экспорта (тексты по умолчанию, needs-action-count) — из cfg дистрибутива;
:: --config в аргументах указывается позже и поэтому важнее
"%JAVA%" -classpath "%DIST%lib\*" com.nlbhub.nlb.web.player.PlayerServer --config "%DIST%cfg\config.xml" %*
