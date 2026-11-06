@echo off
call build.bat
if errorlevel 1 exit /b 1
java -cp out\classes;out\test-classes com.mine.ProtocolSelfTest
if errorlevel 1 exit /b 1
java -Djava.awt.headless=true -cp out\classes;out\test-classes com.mine.ServiceSelfTest
if errorlevel 1 exit /b 1
java -Djava.awt.headless=true -cp out\classes;out\test-classes com.mine.GatewayClientIntegrationTest
