:: 编译打包项目
@echo off :: 从本行开始关闭回显
rem A batch script to build -> deploy -> restart
if [%1]==[] (
    echo.
    echo Usage: %0 meven-profile-1 maven-profile2 ...
    echo.
    goto end
)

set profiles=%~1

:loopProfiles
shift
if "%~1"=="" (
    goto build
) else (
    set profiles=%profiles%,%~1
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
if "%errorlevel%"=="1" goto :releasefailed

goto :releasesuccess

:releasesuccess
echo.
echo.
echo ***************************************************************************************
echo RELEASE SUCCESS!!
echo ***************************************************************************************
goto end

:releasefailed
echo.
echo.
echo ***************************************************************************************
echo RELEASE FAILED!!
echo ***************************************************************************************
goto end

:end

