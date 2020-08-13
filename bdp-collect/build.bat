@echo off
rem 构建bdp-collect

if [%1]==[] (
    echo.
    echo Usage: %0 [-delta] maven-profile-1 maven-profile-2 ...
    echo Option: -delta: 最小化增量部署.
    goto end
)

set deltaDeploy=0
if "%~1"=="-delta" (
    set deltaDeploy=1
    shift
)

set profiles=%~1

:loopProfiles
shift
if "%~1"=="" (
    goto build
) else (
    set profiles=%profiles%,"%~1"
    goto loopProfiles
)

:build
echo.
echo ***************************************************************************************
echo BUILD...
echo ***************************************************************************************
echo.

if "%profiles%"=="" (
    call mvn clean install -DskipTests=true
) else (
    call mvn clean install -DskipTests=true -P%profiles%
)


if "%errorlevel%"=="1" goto buildfailed


::if "%deltaDeploy%"=="1" (
::    call target\classes\deploy.bat -delta
:: ) else (
::    call target\classes\deploy.bat
::)

goto buildsuccess

:buildsuccess
echo.
echo.
echo ***************************************************************************************
echo BUILD SUCCESS!!
echo ***************************************************************************************
goto end

:buildfailed
echo.
echo.
echo ***************************************************************************************
echo BUILD FAILED!!
echo ***************************************************************************************
goto end

:end
