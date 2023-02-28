@echo off

set BASEPATH="C:\eclipse\workspace\COM_XtormBlob_20220502"
set JAVAHOME="C:\Program Files\Java\jdk1.8\bin"
set CLASSPATH="%BASEPATH%\lib\logback-classic-1.3.0-alpha12.jar;%BASEPATH%\lib\logback-core-1.3.0-alpha12.jar;%BASEPATH%\lib\slf4j-api-2.0.0-alpha5.jar;%BASEPATH%\lib\ojdbc6.jar;%BASEPATH%\lib\xvarmapi_20181220.jar;%BASEPATH%\bin"

%JAVAHOME%\javac.exe -encoding utf-8 -cp %CLASSPATH% -d %BASEPATH%\bin %BASEPATH%\src\ty\config\*.java %BASEPATH%\src\ty\module\*.java %BASEPATH%\src\ty\util\*.java %BASEPATH%\src\com\SyncThread.java %BASEPATH%\src\com\XtormApi.java %BASEPATH%\src\com\BlobCallable.java %BASEPATH%\src\com\Startup.java

pause