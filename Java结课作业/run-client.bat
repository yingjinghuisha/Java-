@echo off
if not exist dist\mine-client.jar call build.bat
java -jar dist\mine-client.jar %*
