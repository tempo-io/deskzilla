#!/bin/bash

# -----------------Configurable Section------------------------------
# Java options here, you can add more options if needed.
# See http://kb.almworks.com/wiki/Deskzilla_Command_Line_Options
# You may add JVM options by prepending them with "-J": deskzilla.sh -J-Dopt=value

JAVA_OPTIONS="-Xmx600m -Duse.metal=true"

# -----------------End of Configurable Secion------------------------
SHELL=/bin/bash
export JAVA_OPTIONS
$SHELL "`dirname \"$0\"`"/launch.sh $*
exit $?
