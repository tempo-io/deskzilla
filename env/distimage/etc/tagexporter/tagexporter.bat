@echo off

:: ============================================================================
:: Java options: if needed, you can add more options separated by spaces
:: ============================================================================
set JAVA_OPTIONS=-Xmx400m

set TAGEXPORTER_ETC=%~dp0
set HOME=%TAGEXPORTER_ETC%\..\..
set JAVA_EXE=javaw.exe

if exist "%HOME%\jiraclient.jar" (set PROGRAM_ID=jiraclient) else (set PROGRAM_ID=deskzilla)

if "%JAVA_HOME%" == "" goto find_java_1
set JAVA=%JAVA_HOME%\bin\%JAVA_EXE%
if not exist "%JAVA%" set JAVA=%JAVA_HOME%\jre\bin\%JAVA_EXE%
if exist "%JAVA%" goto launch

:find_java_1
if not exist "%HOME%\jre\bin\%JAVA_EXE%" goto find_java_2
set JAVA=%HOME%\jre\bin\%JAVA_EXE%
goto launch

:find_java_2
set JAVA=%JAVA_EXE%
goto launch

set CLASSPATH=%CLASSPATH%;"%TAGEXPORTER_ETC%\lib"

:: ============================================================================
:launch
start "Tag Exporter Console" "%JAVA%" %JAVA_OPTIONS% -Dproduct.id=%PROGRAM_ID% -jar "%TAGEXPORTER_ETC%\tagexporter.jar" %*
