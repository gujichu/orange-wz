
@echo off
cd /d "%~dp0"
REM 堆内存：最大 16G；初始堆可按机器调小，避免一启动就占满物理内存
start "" jre\bin\javaw -Xms512m -Xmx16g -jar OrzRepacker.jar
