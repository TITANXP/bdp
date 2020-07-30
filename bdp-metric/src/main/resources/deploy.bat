:: 向服务器部署项目
@echo off

set host=${app.host}
set user=${app.user.name}
set password=${app.user.password}
set baseDir=${app.user.home}
set home=${app.home}
set buildDir=${project.build.directory}
set binzip=${project.build.finalName}-bin.zip

echo.
echo ***************************************************************************************
echo UPLOAD...
echo ***************************************************************************************

:: 需要安装PUTTY
@echo on
:: 复制项目打包后的zip文件到远程服务器。     -l 指定用户名，-pw 指定密码 -P 指定端口
PSCP -l %user% -pw %password% -P 22 "%buildDir%\\%binzip%" "%host%:/tmp/"
:: 如果远程服务器用户文件夹不存在，则创建
PLINK -l %user% -pw %password% %host% -t "if [ ! -d '%baseDir%' ]; then mkdir %baseDir%; fi"
:: 如果远程服务器项目文件夹存在，则删除
PLINK -l %user% -pw %password% %host% -t "if [ -d '%home%' ]; then rm -rf %home%; fi"
:: 在远程服务器上解压项目压缩包
PLINK -l %user% -pw %password% %host% -t "unzip /tmp/%binzip% -d %baseDir%/"
@echo off

echo.
echo ***************************************************************************************
echo STARTUP...
echo ***************************************************************************************

:: 也是注释，但和rem会回显
@echo on
:: 如果想在部署完成后启动项目，可以执行下面的命令
:: PLINK -l %user% -pw %password% %host% -t "%baseDir%/${project.build.finalName}/bin/${project.artifactId}.sh restart"
@echo off
