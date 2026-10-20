@echo off
:: All paths (lib, cfg etc.) are relative to the distribution directory
cd /d "%~dp0"

:: Use the Java runtime bundled with the distribution; if it is absent, use JAVA_HOME or java from PATH
if exist runtime\bin\java.exe (
    set "JAVA=runtime\bin\java.exe"
) else if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)

"%JAVA%" -classpath "bin;lib\*" -DconsoleEncoding=UTF-8 com.nlbhub.nlb.builder.NLBBMain %*
