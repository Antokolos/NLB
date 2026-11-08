:: Increments the project version in all pom.xml files (mvn versions:set):
::   bump-version.bat                 1.0.6-SNAPSHOT to 1.0.7-SNAPSHOT (last number)
::   bump-version.bat minor           1.0.6-SNAPSHOT to 1.1.0-SNAPSHOT
::   bump-version.bat major           1.0.6-SNAPSHOT to 2.0.0-SNAPSHOT
::   bump-version.bat 1.2.3-SNAPSHOT  set the version explicitly
@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "CURRENT="
for /f "tokens=3 delims=<>" %%v in ('findstr /r /c:"^    <version>" pom.xml') do (
    if not defined CURRENT set "CURRENT=%%v"
)
if not defined CURRENT (
    echo Cannot find the project version in pom.xml
    exit /b 1
)
set "BASE=!CURRENT!"
set "SUFFIX="
if not "!CURRENT:-=!"=="!CURRENT!" (
    for /f "tokens=1* delims=-" %%a in ("!CURRENT!") do (
        set "BASE=%%a"
        set "SUFFIX=-%%b"
    )
)
set "MAJOR="
set "MINOR="
set "PATCH="
for /f "tokens=1-3 delims=." %%a in ("!BASE!") do (
    set "MAJOR=%%a"
    set "MINOR=%%b"
    set "PATCH=%%c"
)
if not defined PATCH (
    echo Cannot parse the project version !CURRENT! in pom.xml
    exit /b 1
)

set "MODE=%~1"
if "!MODE!"=="" set "MODE=patch"
set "NEW="
if /i "!MODE!"=="patch" (
    set /a PATCH+=1
    set "NEW=!MAJOR!.!MINOR!.!PATCH!!SUFFIX!"
)
if /i "!MODE!"=="minor" (
    set /a MINOR+=1
    set "NEW=!MAJOR!.!MINOR!.0!SUFFIX!"
)
if /i "!MODE!"=="major" (
    set /a MAJOR+=1
    set "NEW=!MAJOR!.0.0!SUFFIX!"
)
if not defined NEW (
    rem an explicit version starts with a digit
    set "FIRST=!MODE:~0,1!"
    for %%d in (0 1 2 3 4 5 6 7 8 9) do (
        if "!FIRST!"=="%%d" set "NEW=!MODE!"
    )
)
if not defined NEW (
    echo Usage: bump-version.bat [patch^|minor^|major^|X.Y.Z[-SNAPSHOT]]
    exit /b 1
)

echo Version: !CURRENT! to !NEW!
call mvn versions:set -DnewVersion=!NEW!
