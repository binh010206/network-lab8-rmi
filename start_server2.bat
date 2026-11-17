@echo off
cd /d "%~dp0"
set "JAVA_EXE=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=java"
set "M2=%USERPROFILE%\.m2\repository"
set "FX_MODULES=%M2%\org\openjfx\javafx-base\21.0.2\javafx-base-21.0.2-win.jar;%M2%\org\openjfx\javafx-base\21.0.2\javafx-base-21.0.2.jar;%M2%\org\openjfx\javafx-controls\21.0.2\javafx-controls-21.0.2-win.jar;%M2%\org\openjfx\javafx-controls\21.0.2\javafx-controls-21.0.2.jar;%M2%\org\openjfx\javafx-graphics\21.0.2\javafx-graphics-21.0.2-win.jar;%M2%\org\openjfx\javafx-graphics\21.0.2\javafx-graphics-21.0.2.jar"
set "CP=target\classes;%M2%\org\checkerframework\checker-qual\3.55.1\checker-qual-3.55.1.jar;%M2%\org\postgresql\postgresql\42.7.14\postgresql-42.7.14.jar;%M2%\org\mongodb\mongodb-driver-sync\5.1.0\mongodb-driver-sync-5.1.0.jar;%M2%\org\mongodb\bson\5.1.0\bson-5.1.0.jar;%M2%\org\mongodb\mongodb-driver-core\5.1.0\mongodb-driver-core-5.1.0.jar"

start "Lab8 Server 2" "%JAVA_EXE%" --module-path "%FX_MODULES%" --add-modules javafx.base,javafx.controls,javafx.graphics -classpath "%CP%" server.Server2App config/server2.local.properties
