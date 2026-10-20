:: Clean first, otherwise jars from previous builds (e.g. with other version numbers)
:: would remain in target\ and be copied to dist\lib
call mvn clean generate-sources package
if errorlevel 1 exit /b 1
if exist dist rd /S /Q dist
:: rd fails silently if some file in dist is locked (e.g. NLBB is running from dist)
if exist dist (
    echo Cannot delete dist directory, please close all programs which use it and try again
    exit /b 1
)
mkdir dist
copy NLBB\script\* dist
mkdir dist\lib
copy NLBB\target\NLBB*.jar dist\lib
copy NLBB\target\lib\* dist\lib
copy NLBB\target\modules\* dist\lib
xcopy NLBW\fonts dist\fonts /E /I /Y
xcopy NLBW\xsl dist\xsl /E /I /Y
xcopy NLBW\cfg dist\cfg /E /I /Y
xcopy NLBW\res dist\res /E /I /Y
xcopy NLBW\template dist\template /E /I /Y
:: Bundle a minimal Java runtime, so that Java does not need to be installed on the user's computer.
:: The runtime is built for the current OS, so the distribution should be built on the target OS.
:: java.se contains all standard Java modules; jdk.dynalink is needed by Nashorn, jdk.crypto.ec by HTTPS (Git),
:: jdk.localedata and jdk.charsets by non-English locales and encodings
set RUNTIME_MODULES=java.se,jdk.dynalink,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.charsets,jdk.zipfs
set "JLINK=jlink"
if defined JAVA_HOME set "JLINK=%JAVA_HOME%\bin\jlink"
call "%JLINK%" --add-modules "%RUNTIME_MODULES%" --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output dist\runtime
if errorlevel 1 exit /b 1
call mvn clean
