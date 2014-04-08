@ECHO OFF

SET JAVA_HOME=C:\Java\jdk1.7.0_51

:: Set application lib directory
SET COMMON_LIB=lib\

:: prepare classpath
setlocal EnableDelayedExpansion
set CP=./bin
for %%f in (%COMMON_LIB%\*.jar) do set CP=%%f;!CP!

"%JAVA_HOME%/bin/java" -classpath %CP% com.nlbhub.nlb.builder.NLBBMain