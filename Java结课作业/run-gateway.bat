@echo off
if not exist dist\mine-gateway.jar call build.bat
java -jar dist\mine-gateway.jar %*
