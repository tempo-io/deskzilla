#! /bin/sh

# Installation directory of Apache ANT (the dir which containts "bin", "lib" sub directories)
ANT_HOME=/Users/dyoma/Progs/apache-ant-1.10.7
# Home directory of Java8 JDK (the dir which contains "bin", "lib", "jre" subdirectories)
JDK8_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home

"$JDK8_HOME/bin/java" -cp "$ANT_HOME/lib/ant-launcher.jar" org.apache.tools.ant.launch.Launcher -f ./build.xml prepareDistribution -Djdk="$JDK8_HOME" -Dbuild.number=9876