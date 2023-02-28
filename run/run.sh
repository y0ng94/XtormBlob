#!/bin/sh
BASEPATH=/app/xtorm/test/COM_XtormBlob_20220502
JAVAHOME=/usr/bin
CLASSPATH=$BASEPATH/lib/logback-classic-1.3.0-alpha12.jar:$BASEPATH/lib/logback-core-1.3.0-alpha12.jar:$BASEPATH/lib/slf4j-api-2.0.0-alpha5.jar:$BASEPATH/lib/ojdbc6.jar:$BASEPATH/lib/xvarmapi_20181220.jar:$BASEPATH/bin

$JAVAHOME/java -cp $CLASSPATH com.Startup $BASEPATH