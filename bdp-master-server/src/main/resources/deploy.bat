@echo off

set host=${app.host}
set user=${app.user.name}
set password=${app.user.password}
set baseDir=${app.user.home}
set home=${app.home}
set buildDir=${project.build.directory}
set binZip=${project.build.finalName}-bin.zip
set deltaBinZip=${project.build.finalName}-bin-delta.zip
set logHome=${app.log.home}

echo.
echo ***************************************************************************************
echo UPLOAD...
echo ***************************************************************************************

if "%~1"=="-delta" (
    goto uploadDeltaBinZip
) else (
    goto uploadBinZip
)

:uploadBinZip
@echo on
:: 上传zip包到远程服务器
PSCP -l %user% -pw %password% -P 22 "%buildDir%\\%binZip%" "%host%:/tmp/"
:: 如果用户文件夹不存在，则创建
PLINK -l %user% -pw %password% %host% -t "if [ ! -d '%baseDir%' ]; then mkdir %baseDir; fi"
:: 如果项目文件夹已经存在，则删除
PLINK -l %user% -pw %password% %host% -t "if [ -d '%home%' ]; then rm -rf %home%; fi"
:: 解压到项目文件夹
PLINK -l %user% -pw %password% %host% -t "unzip /tmp/%binZip% -d %baseDir%/"
:: 创建日志目录
PLINK -l %user% -pw %password% %host% -t "mkdir %logHome%/"
@echo off
goto startup

:: 最小化增量部署
:uploadDeltaBinZip
@echo on
:: 上传zip包
PSCP -l %user% -pw %password% "%buildDir%\\%binDeltaZip%" "%host%:/tmp/"
:: 解压
PLINK -l %user% -pw %password% %host% -t "unzip /tmp/%binDeltaZip% -d %baseDir%/"
@echo off
goto startup

:startup
echo.
echo ***************************************************************************************
echo STARTUP...
echo ***************************************************************************************

@echo on
:: 启动项目
:: PLINK -l %user% -pw %password% %host% -t "%baseDir%/${project.build.finalName}/bin/${project.artifactId}.sh restart-with-logging
@echo off

