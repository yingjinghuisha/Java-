@echo off
setlocal
if not exist out\classes mkdir out\classes
if not exist out\test-classes mkdir out\test-classes
if not exist dist mkdir dist
echo ==^> Compiling main sources
dir /s /b src\main\java\*.java > out\main-sources.txt
javac -encoding UTF-8 -source 1.8 -target 1.8 -d out\classes @out\main-sources.txt
if errorlevel 1 goto error
echo ==^> Compiling self tests
dir /s /b src\test\java\*.java > out\test-sources.txt
javac -encoding UTF-8 -source 1.8 -target 1.8 -cp out\classes -d out\test-classes @out\test-sources.txt
if errorlevel 1 goto error
echo ==^> Packaging jars
jar cfe dist\mine-client.jar com.mine.client.MainClient -C out\classes .
jar cfe dist\mine-gateway.jar com.mine.gateway.GatewayMain -C out\classes .
echo Build OK: dist\mine-client.jar  dist\mine-gateway.jar
exit /b 0
:error
echo Build FAILED
exit /b 1
