@echo off
REM ============================================================
REM  DiamondShop Build Script  (Windows)
REM  For Beta 1.7.3 / CraftBukkit b1060 (Project Poseidon)
REM
REM  Place craftbukkit-b1060.jar in the same folder as this script,
REM  then double-click or run from a command prompt.
REM ============================================================

SET JAVAC=javac
SET JAR=jar
SET CB=craftbukkit-b1060.jar
SET SRC=src\main\java
SET OUT=build\classes
SET RES=src\main\resources

IF NOT EXIST %CB% (
    echo ERROR: %CB% not found!
    echo Place the CraftBukkit b1060 jar next to this script.
    pause
    exit /b 1
)

echo Cleaning build directory...
IF EXIST build rmdir /S /Q build
mkdir %OUT%

echo Compiling sources...
%JAVAC% -source 1.6 -target 1.6 -cp %CB% -d %OUT% ^
    %SRC%\com\shop\plugin\ShopItem.java ^
    %SRC%\com\shop\plugin\ShopManager.java ^
    %SRC%\com\shop\plugin\ShopPlugin.java

IF ERRORLEVEL 1 (
    echo Compilation FAILED.
    pause
    exit /b 1
)

echo Copying resources...
copy %RES%\plugin.yml %OUT%\plugin.yml

echo Packaging JAR...
cd build\classes
%JAR% cvf ..\..\DiamondShop.jar .
cd ..\..

echo.
echo ============================================================
echo  SUCCESS! DiamondShop.jar is ready.
echo  Copy it into your server's /plugins folder and restart.
echo ============================================================
pause
