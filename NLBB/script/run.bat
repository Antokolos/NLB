@echo off
 
Set RegQry=HKLM\Hardware\Description\System\CentralProcessor\0
 
REG.exe Query %RegQry% > checkOS.txt
 
Find /i "x86" < CheckOS.txt > StringCheck.txt
 
If %ERRORLEVEL% == 0 (
    Echo "This is 32 Bit Operating system"
    SET JAVA_HOME=jre-7u51-windows-i586
) ELSE (
    Echo "This is 64 Bit Operating System"
    SET JAVA_HOME=jre-7u51-windows-x64
)

:: Set application lib directory
SET COMMON_LIB=lib\

:: prepare classpath
setlocal EnableDelayedExpansion
set CP=./bin
for %%f in (%COMMON_LIB%\*.jar) do set CP=%%f;!CP!

"%JAVA_HOME%/bin/java" -classpath %CP% -DconsoleEncoding=UTF-8 com.nlbhub.nlb.builder.NLBBMain