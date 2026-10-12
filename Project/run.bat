@echo off
cd /d "%~dp0"
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven was not found. Install Maven and add its bin folder to PATH.
  echo Then open a new terminal and run run.bat again.
  pause
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install a Java 17 or newer JDK first.
  pause
  exit /b 1
)
mvn compile exec:java
pause
