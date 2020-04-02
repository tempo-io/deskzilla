# Application launcher - do not call manually.
##############################################

if [ "$X_ALMWORKS_LAUNCH_PERMIT" != "true" ]; then
echo ==========================================================================
echo ERROR: `basename "$0"` should not be called manually. 
echo Please start application with other .sh files.
echo ==========================================================================
exit 1
fi

if [ "x$PROGRAM_NAME" = "x" -o "x$PROGRAM_JAR" = "x" ]; then
echo ==========================================================================
echo ERROR: Bad call to `basename "$0"`
echo [$PROGRAM_JAR] [$PROGRAM_NAME]
echo ==========================================================================
exit 1
fi

APPBIN="`dirname \"$0\"`"
APPHOME="$APPBIN/.."
JAVA_EXE=java
JAVA=$JAVA_EXE

if [ ! -f "$APPHOME/$PROGRAM_JAR" ]; then
echo ==========================================================================
echo ERROR: Cannot start $PROGRAM_NAME
echo Cannot find $PROGRAM_JAR in $APPHOME
echo ==========================================================================
exit 1
fi

if [ "x$JAVA_HOME" != "x" ]; then
JAVA="$JAVA_HOME/bin/$JAVA_EXE"
if [ ! -f "$JAVA" ]; then JAVA="$JAVA_HOME/jre/bin/$JAVA_EXE"; fi
if [ ! -f "$JAVA" ]; then JAVA="$APPHOME/jre/bin/$JAVA_EXE"; fi
if [ ! -f "$JAVA" ]; then JAVA=$JAVA_EXE; fi
fi

"$JAVA" $JAVA_OPTIONS -jar "$APPHOME/$PROGRAM_JAR" $*
