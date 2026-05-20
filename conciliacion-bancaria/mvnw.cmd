@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM ----------------------------------------------------------------------------
@echo off
set MAVEN_PROJECTBASEDIR=%~dp0

for /F "usebackq delims=" %%i in (`powershell -Command "& {$home}"`) do set USERPROFILE_TMP=%%i

set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

set DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

if exist %WRAPPER_JAR% goto executeWrapper

echo Downloading Maven Wrapper...
powershell -Command "& {Invoke-WebRequest -Uri %DOWNLOAD_URL% -OutFile %WRAPPER_JAR%}"

:executeWrapper
set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9\bin
if exist "%MAVEN_HOME%\mvn.cmd" goto runMaven

for /F "usebackq delims=" %%i in (`dir /b /s "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9" 2^>nul ^| findstr "mvn.cmd"`) do set MAVEN_HOME_BIN=%%i

:runMaven
if defined MAVEN_HOME_BIN (
    "%MAVEN_HOME_BIN%" %*
) else (
    "%MAVEN_HOME%\mvn.cmd" %*
)