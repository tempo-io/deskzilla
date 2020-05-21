Deskzilla Readme
Last edited: May 21, 2020
http://almworks.com/deskzilla
https://bitbucket.org/almworks/deskzilla/src/master/


Contents
========
* System Requirements
* Installation
* Upgrading
* Uninstall
* Backup and Restore
* Trademarks and Copyright


System Requirements
===================

1. Server: Bugzilla 3.0 - 5.1+

   You can install Deskzilla to access already existing Bugzilla installations,
   such as publicly available Bugzilla servers for numerous open-source 
   projects. If you plan to start your own server, you should install Bugzilla
   first. (See also our Virtual Bugzilla Server, http://almworks.com/vbs)

2. Additional server authentication: Basic HTTP, Digest, NTLM (version 1 or 2) 

3. Operating System: Microsoft Windows 7/8/10, Linux (with Oracle JRE),
   Mac OS X, or any other supported by Java;

4. System Memory: 256MB required, 512MB recommended;

5. Hard Drive Space: 200MB required, 300MB recommended;

6. Screen Resolution: 1024x768 or better;

7. Displays: multiple displays are supported, but Deskzilla should be 
   restarted when a display is connected or disconnected;

8. Network connection: on initial setup, Deskzilla may have to load a lot 
   from Bugzilla, so performance over slow network connection may be 
   degraded. (Hint: turn on gzip compression on the web-server that hosts
   Bugzilla.)

9. Java: In case you downloaded a distribution without Java bundled in, you
   will need Java SE 8 or later.


Installation - Windows
======================

To install the application, run downloaded executable file and follow 
instructions. If having problems installing on Windows 7/8/10, use
"Run as Administrator" to start the installer.


Installation - Linux
====================

To install the application, unpack downloaded archive. Run 
  bin/deskzilla.sh 
to start Deskzilla. 

Deskzilla will create ".Deskzilla" subdirectory in your home 
directory. Make sure the home is writable.


Installation - Mac OS X
=======================

Supported versions:

- OS X 10.8 (Mountain Lion);

- OS X 10.9 (Mavericks);

- OS X 10.10 (Yosemite);

- OS X 10.11 (El Capitan).

To install the application, copy it from the dmg image to the
Applications folder. Deskzilla will create ".Deskzilla"
subdirectory in your home directory. Make sure it is writable.

Note for OS X Mavericks Users
=============================

To do that, go to System Preferences -> Security & Privacy -> General
tab and select "Allow apps downloaded from: Anywhere". You can enable
GateKeeper again after the first successful launch of Deskzilla.


Upgrading
=========

1. Stop Deskzilla if it is running;

2. Back up your workspace; 
   see http://wiki.almworks.com/display/dz30/How+to+Backup+Your+Workspace

3. Either unpack the new version on top of the old version, or run the
   new installer.


Uninstall
=========

To uninstall Deskzilla on Windows, run uninstaller from 
Control Panel - Add/Remove Programs.

To uninstall Deskzilla on other operating systems, just delete Deskzilla
home directory.

NOTE: Deskzilla workspace (queries, local database, etc.) usually 
resides in a separate directory. Uninstallation should not affect it.


Backup and Restore
==================

See also: http://wiki.almworks.com/display/kb/How+to+Backup+Your+Workspace

All user information is contained within workspace directory. By default,
it is named ".Deskzilla" and it resides in the user's home directory.
(Something like /home/username/.Deskzilla on Unix or 
 C:\Users\username\.Deskzilla on Windows)

To back if up, just copy all of its contents to a directory of your choice. 

To restore from backed up workspace, delete or move current workspace and
copy back the workspace that you have saved earlier. 

NOTE: You must shut down Deskzilla to perform backup and restore.


Trademarks and Copyright
========================

Mozilla(TM) and Bugzilla(TM) are trademarks of the Mozilla Foundation 
in the United States and other countries.

Java is a registered trademark of Oracle and/or its affiliates.

Microsoft, Windows are trademarks of Microsoft Corporation.

Apple, Mac, Mac OS, and OS X are trademarks of Apple Inc.

All other trademarks mentioned in Deskzilla product, documentation and other
files are the property of their respective owners.

Deskzilla is copyright (C) ALM Works Ltd 2004-2016

All files included in this software distribution package are subject to 
copyright by ALM Works Ltd and its licensors.
