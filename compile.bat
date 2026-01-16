@echo off
set "PROJECT=C:\Users\Crehop\Desktop\HyCrownDev\town\HyTowny"
set "CLASSPATH=%PROJECT%\lib\HytaleServer.jar;%PROJECT%\lib\HyConomy-1.0.0.jar"

cd /d "%PROJECT%"

echo Compiling Java files...
javac -d "target\classes" -cp "%CLASSPATH%" @sources.txt

if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Creating JAR...
cd "target\classes"
jar -cvf "..\hycrown_HyTowny-1.0.0.jar" .

echo Done!
cd /d "%PROJECT%"
