call mvn package
rd /S /Q dist
mkdir dist
copy NLBB\script\* dist
mkdir dist\lib
copy NLBB\target\NLBB-2.0-SNAPSHOT.jar dist\lib
copy NLBB\target\dependency\* dist\lib
xcopy NLBW\fonts dist\fonts /E /I /Y
xcopy NLBW\xsl dist\xsl /E /I /Y
xcopy NLBW\cfg dist\cfg /E /I /Y
xcopy NLBW\template dist\template /E /I /Y
call mvn clean
