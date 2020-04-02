#!/bin/bash

# -----------------Configurable Section------------------------------
# Java options here, you can add more options if needed.
# See http://kb.almworks.com/wiki/Deskzilla_Command_Line_Options

JAVA_OPTIONS="##JAVA_OPTIONS##"
I4JSCRIPT="##I4JSCRIPT##"

# -----------------End of Configurable Secion------------------------

PROG_OPTIONS=""
for ARG in $* ; do
  if [ "${ARG:0:2}" = "-J" ] ; then JAVA_OPTIONS="$JAVA_OPTIONS ${ARG:2}"
  else PROG_OPTIONS="$PROG_OPTIONS $ARG" ; fi
done

ME=$0
while [ -L "$ME" ]; do
  _dir="`dirname \"$ME\"`"
  _link="`readlink \"$ME\"`"
  if [ "x$?" != "x0" ]; then break; fi
  if [ "x${_link}" = "x" ]; then break; fi
  if [ "x${_link:0:1}" != "x/" ]; then
    ME="${_dir}/${_link}"
  else 
    ME="${_link}"
  fi
done
MYDIR="`dirname \"$ME\"`"

PROGRAM_JAR=##PRODUCT_FAMILY##.jar
PROGRAM_NAME="##PRODUCT_NAME_CAPITALIZED##"
LAUNCHER=launch.sh
LAUNCH="$MYDIR/$LAUNCHER"
ROOT_LAUNCHER="$MYDIR/../##PRODUCT_FAMILY##"
SHELL=/bin/bash

if [ "x$I4JSCRIPT" != "xfalse" ]; then
  if [ -x "$ROOT_LAUNCHER" ]; then
    INSTALL4J_ADD_VM_PARAMS=$JAVA_OPTIONS
    export INSTALL4J_ADD_VM_PARAMS
    "$ROOT_LAUNCHER" $PROG_OPTIONS
    exit $?
  fi
fi

if [ ! -f "$LAUNCH" ] ; then
echo ==========================================================================
echo ERROR: Cannot start $PROGRAM_NAME
echo Cannot find $LAUNCHER in $MYDIR
echo ==========================================================================
exit 1
fi

X_ALMWORKS_LAUNCH_PERMIT=true
export PROGRAM_JAR PROGRAM_NAME X_ALMWORKS_LAUNCH_PERMIT JAVA_OPTIONS
$SHELL "$LAUNCH" $PROG_OPTIONS
exit $?
