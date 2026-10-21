@echo off
:: Консольный плеер книг NLB (com.nlbhub.nlb.player.ConsolePlayer).
:: Использование: play.bat <каталог книги> [vn|standard] [--deterministic[=<n>]] [--config <config.xml>]
:: Путь к книге — относительно текущего каталога, поэтому, в отличие от run.bat, каталог не меняется
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

:: Плеер пишет в UTF-8: на время игры консоль переключается на UTF-8, потом кодовая страница
:: возвращается прежняя
for /f "tokens=2 delims=:" %%c in ('chcp') do set "OLDCP=%%c"
set "OLDCP=%OLDCP: =%"
set "OLDCP=%OLDCP:.=%"
chcp 65001 > nul

:: Настройки экспорта (тексты по умолчанию, needs-action-count) — из cfg дистрибутива;
:: --config в аргументах указывается позже и поэтому важнее
"%JAVA%" -classpath "%DIST%lib\*" com.nlbhub.nlb.player.ConsolePlayer --config "%DIST%cfg\config.xml" %*
set "RESULT=%ERRORLEVEL%"

if defined OLDCP chcp %OLDCP% > nul
exit /b %RESULT%
