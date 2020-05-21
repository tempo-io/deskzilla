## How to Build

### Prerequisites

In order to build the project you need [Apache Ant](https://ant.apache.org/) and [Oracle JDK](https://www.oracle.com/java/) version 8. 

1. Download Apache Ant from https://ant.apache.org/bindownload.cgi 
  
     The build has been tested with Ant version 1.10.7

2. Download Oracle JDK 8 from [Java Download Archive](https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html)

     The build has been tested with version 8u192, though it is likely compatible with any JDK version
     from 8u112 to 8u202.
   
     Note, the build is NOT compatible with:
    
      * Java 9 and later
    
      * Java 8 updates before 8u112
    
      * any version of OpenJDK.   

### Build Steps

1. Have Apache Ant and Oracle JDK 8 installed on your system.

2. Open the [build.sh](ant/build.sh) with a plain text editor.

3. Specify values of the ANT_HOME and JDK8_HOME variable.

4. Save the updated build.sh file.

5. Open command line terminal and go to the [ant](ant) directory (`cd ant`)

6. Run the [build.sh](ant/build.sh) shell script (`./build.sh`)

7. When the build successfully completes, find built application in the [build/.dist/deskzilla](/build/.dist/deskzilla)
directory.

     Find ZIPed application in the [build/.dist/deskzilla-NNNN.zip](/build/.dist/deskzilla-9876.zip) file.
   
For more details see the [build documentation](ant/BUILD.md)   

## How to Run

To start the built application run the start up script from command-line terminal.
The script is in the:

 * [deskzilla.sh](./build/.dist/deskzilla/bin/deskzilla.sh) for Mac
 
 * [linux_deskzilla.sh](./build/.dist/deskzilla/bin/linux_deskzilla.sh) for Linux
 
 * [deskzilla.bat](./build/.dist/deskzilla/bin/deskzilla.bat) for Windows
 
You can pass [workspace](https://wiki.almworks.com/display/jc16/Workspace) location as command-line parameter.

Deskzilla requires Oracle Java8 updates 8u112 to 8u202 (versions 8u192 or 8u202 are recommended).
Java either must be available on the PATH environment variable 
or JAVA_HOME variable must be defined and point to the corresponding JRE or JDK.
 
 Copyright 2004–2020 [ALM Works, Inc](https://almworks.com/). This work is licensed under the terms of [GPL v3  license](https://www.gnu.org/licenses/gpl-3.0.html). 
 If you require a different license, please contact [info@almworks.com](info@almworks.com).