@echo off
if not exist out\classes\com\mine\client\tools\NetworkPreflight.class call build.bat
java -cp out\classes com.mine.client.tools.NetworkPreflight %*
