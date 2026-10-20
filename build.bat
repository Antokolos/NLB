:: Clean first: each build increments the project version, so jars from previous builds
:: (with other version numbers) would otherwise remain in target\ and be copied to dist\lib
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
call mvn clean
