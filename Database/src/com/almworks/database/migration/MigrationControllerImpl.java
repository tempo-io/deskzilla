package com.almworks.database.migration;

import com.almworks.api.misc.WorkArea;
import com.almworks.universe.FileUniverse;
import com.almworks.universe.data.*;
import com.almworks.util.fileformats.FileFormatException;
import com.almworks.util.files.FileUtil;
import org.almworks.util.Collections15;
import org.almworks.util.Failure;
import util.concurrent.SynchronizedBoolean;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class MigrationControllerImpl implements MigrationController {
  private static final SimpleDateFormat BACKUP_DIR_FORMAT = new SimpleDateFormat("yyMMdd-hhmmss-SSS");
  private final List<File> myTempFiles = Collections15.arrayList();
  private final SynchronizedBoolean myPassGoing = new SynchronizedBoolean(false);
  private final SynchronizedBoolean myStarted = new SynchronizedBoolean(false);
  private AtomDataFile myPassResultFile;
  private AtomDataFile myPassSourceFile;
  private File myDataFileName;
  private File myPassResultFileName;
  private File myPassSourceFileName;
  private int myPassCount = 0;

  public synchronized void startMigration(WorkArea workArea) throws MigrationFailure {
    if (!myStarted.commit(false, true))
      throw new IllegalStateException("already started");
    backup(workArea);
    myDataFileName = new File(workArea.getDatabaseDir(), FileUniverse.SINGLE_DATAFILE_NAME);
    myPassSourceFileName = myDataFileName;
    myPassCount = 0;
    myTempFiles.clear();
  }

  public void makePass(ExpansionInfoSink sink) throws MigrationFailure {
    if(!myStarted.get())
      throw new IllegalStateException("migration has not been started");
    if (!myPassGoing.commit(false, true))
      throw new IllegalStateException("migration pass is in progress");
    try {
      myPassCount++;
      myPassResultFileName = createPassResultFileName(myPassSourceFileName, myPassCount);
      openPassFiles();
      pass(sink);
      closePassFiles();
      advanceFileNames();
    } finally {
      myPassGoing.commit(true, false);
    }
  }

  public void saveExpansion(ExpansionInfo info) {
    if (!myPassGoing.get())
      throw new IllegalStateException("pass is not in progress");
    try {
      myPassResultFile.write(info);
    } catch (AtomDataFileException e) {
      throw new Failure("cannot write pass file " + myPassResultFileName, e);
    }
  }

  public void endMigration() throws MigrationFailure {
    if (myPassGoing.get())
      throw new IllegalStateException("pass is not finished");
    if (!myStarted.commit(true, false))
      throw new IllegalStateException("migration is not started");
    if (myPassSourceFileName.equals(myDataFileName)) {
      // no pass was taken
      return;
    }
    try {
      FileUtil.copyFile(myPassSourceFileName, myDataFileName);
    } catch (IOException e) {
      throw new MigrationFailure("cannot write to database file", e);
    }
    for (Iterator<File> ii = myTempFiles.iterator(); ii.hasNext();) {
      File file = ii.next();
      file.delete();
    }
  }

  private void advanceFileNames() {
    myPassSourceFileName = myPassResultFileName;
    myPassResultFileName = null;
  }

  private void backup(WorkArea workArea) throws MigrationFailure {
    try {
      File backupDir = new File(workArea.getDatabaseBackupDir(), BACKUP_DIR_FORMAT.format(new Date()));
      boolean success = backupDir.mkdirs();
      if (!success)
        throw new MigrationFailure("cannot create backup dir " + backupDir);
      File databaseDir = workArea.getDatabaseDir();
      if (!databaseDir.isDirectory())
        return;
      File[] files = databaseDir.listFiles();
      if (files == null || files.length == 0)
        return;
      for (int i = 0; i < files.length; i++) {
        File file = files[i];
        FileUtil.copyFile(file, new File(backupDir, file.getName()));
      }
    } catch (IOException e) {
      throw new MigrationFailure("backup failed", e);
    }
  }

  private void closePassFiles() {
    myPassSourceFile.close();
    myPassResultFile.close();
  }

  private File createPassResultFileName(File source, int passCount) {
    String name = source.getName();
    File dir = source.getParentFile();
    File result = new File(dir, name + "." + passCount);
    myTempFiles.add(result);
    return result;
  }

  private void openPassFiles() throws MigrationFailure {
    try {
      myPassSourceFile = new AtomDataFile(myPassSourceFileName);
      myPassSourceFile.open();
      myPassResultFile = new AtomDataFile(myPassResultFileName);
      myPassResultFile.create();
    } catch (IOException e) {
      throw new MigrationFailure("cannot open universe", e);
    } catch (FileFormatException e) {
      throw new MigrationFailure("cannot open universe", e);
    }
  }

  private void pass(ExpansionInfoSink sink) throws MigrationFailure {
    try {
      myPassSourceFile.readAll(sink);
    } catch (FileFormatException e) {
      throw new MigrationFailure("cannot read source file " + myPassSourceFileName, e);
    } catch (IOException e) {
      throw new MigrationFailure("cannot read source file " + myPassSourceFileName, e);
    }
  }
}
