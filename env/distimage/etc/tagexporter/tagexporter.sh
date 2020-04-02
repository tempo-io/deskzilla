#!/bin/bash

# -----------------Configurable Section------------------------------
# Java options here, you can add more options if needed
# Separate options with spaces.

# JAVA_OPTIONS=-Xmx400m

# -----------------End of Configurable Secion------------------------

PROGRAM_NAME="Tag Exporter"
PROGRAM_JAR="tagexporter.jar"

ETC_TAGEXPORTER="`dirname \"$0\"`"
HOME="$ETC_TAGEXPORTER/../.."
JAVA_EXE=java
JAVA=$JAVA_EXE

if [ ! -f "$PROGRAM_JAR" ]; then
echo ==========================================================================
echo ERROR: Cannot start $PROGRAM_NAME
echo Cannot find $PROGRAM_JAR in $ETC_TAGEXPORTER
echo ==========================================================================
exit 1
fi

if [ -f "$HOME/jiraclient.jar" ];
then PRODUCT_ID=jiraclient
else PRODUCT_ID=deskzilla
fi

if [ "x$JAVA_HOME" != "x" ]; then
JAVA="$JAVA_HOME/bin/$JAVA_EXE"
if [ ! -f "$JAVA" ]; then JAVA="$JAVA_HOME/jre/bin/$JAVA_EXE"; fi
if [ ! -f "$JAVA" ]; then JAVA="$HOME/jre/bin/$JAVA_EXE"; fi
if [ ! -f "$JAVA" ]; then JAVA=$JAVA_EXE; fi
fi

"$JAVA" $JAVA_OPTIONS -Dproduct.id=$PRODUCT_ID -jar "$PROGRAM_JAR" $*
