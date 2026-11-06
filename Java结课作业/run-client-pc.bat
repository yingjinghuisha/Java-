@echo off
if "%~1"=="" (
  echo 用法: run-client-pc.bat ^<本机工控机IP^> [网关IP] [端口]
  echo 示例: run-client-pc.bat 172.20.205.11 172.20.205.10 8888
  exit /b 1
)
set "LOCAL_IP=%~1"
if "%~2"=="" (set "GATEWAY_IP=127.0.0.1") else (set "GATEWAY_IP=%~2")
if "%~3"=="" (set "PORT=8888") else (set "PORT=%~3")
call "%~dp0run-client.bat" --host %GATEWAY_IP% --port %PORT% --local-ip %LOCAL_IP% --auto-connect
