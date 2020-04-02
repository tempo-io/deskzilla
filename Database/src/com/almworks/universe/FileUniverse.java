package com.almworks.universe;

import com.almworks.api.misc.WorkArea;
import com.almworks.api.universe.*;
import com.almworks.universe.data.*;
import com.almworks.universe.optimize.UniverseFileOptimizer;
import com.almworks.universe.optimize.UniverseMemoryOptimizer;
import com.almworks.util.fileformats.FileFormatException;
import org.almworks.util.*;
import org.picocontainer.Startable;
import util.concurrent.SynchronizedBoolean;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class FileUniverse implements Universe, Startable {
  public static final String SINGLE_DATAFILE_NAME = "universe.db";
  private final File myDataFileName;
  private final State myState = new State();
  private final AtomDataFile myDataFile;

  private Map<String, String> myPropertiesUponCreation = null;
  private volatile boolean myStarted = false;
  private final SynchronizedBoolean myStarting = new SynchronizedBoolean(false);

  public FileUniverse(File universeDir) {
    myDataFileName = new File(universeDir, SINGLE_DATAFILE_NAME);
    myDataFile = new AtomDataFile(myDataFileName);
  }

  public FileUniverse(WorkArea workArea) {
    this(workArea.getDatabaseDir());
  }

  public Map<String, String> getCustomProperties() {
    return myDataFile.getCustomProperties();
  }

  public void setCustomPropertiesIfCreating(Map<String, String> metadata) {
    if (myDataFile.isOpen())
      return;
    myPropertiesUponCreation = metadata;
  }

  public synchronized void start() {
    if (myStarted)
      throw new IllegalStateException("universe is already started");
    if (!myStarting.commit(false, true))
      throw new IllegalStateException("universe is already starting");
    try {
      if (myDataFileName.exists()) {
        myDataFile.open();
      } else {
        if (myPropertiesUponCreation != null)
          myDataFile.setCustomProperties(myPropertiesUponCreation);
        myDataFile.create();
      }
      try {
        UniverseFileOptimizer.start(myDataFileName, myDataFile.accessFile(), myDataFile.accessLock());
      } catch (Exception e) {
        Log.error(e);
      }
      rescanDataFile();
      myStarted = true;
      myStarting.commit(true, false);
    } catch (FileFormatException e) {
      throw new Failure(e); // todo
    } catch (IOException e) {
      throw new Failure(e); // todo
    }
  }

  public synchronized void stop() {
    if (!myStarted)
      throw new IllegalStateException("universe is not started");
    try {
      UniverseFileOptimizer.stop(myDataFileName);
    } catch (Exception e) {
      Log.error(e);
    }
    try {
      UniverseMemoryOptimizer.staticCleanup();
    } catch (Exception e) {
      Log.error(e);
    }
    myStarted = false;
    myDataFile.close();
  }

  public long getUCN() {
    return myState.getUCN();
  }

  private void checkStarted() {
    if (!myStarted)
      throw new IllegalStateException("universe is not started");
  }

  public Atom getAtom(long atomID) {
    assert myStarted : this;
    return myState.getAtom(atomID);
  }

  public synchronized Index createIndex(IndexInfo indexInfo) {
    checkStarted();
    IndexInternal index = IndexFactory.createIndex(myState, indexInfo);
    myState.addIndex(index);
    return index;
  }

  public synchronized Index[] getIndices() {
    checkStarted();
    Collection<IndexInternal> result = myState.getIndices();
    return result.toArray(new Index[result.size()]);
  }

  public synchronized Index[] getIndices(Atom atom) {
    checkStarted();
    Collection<IndexInternal> all = myState.getIndices();
    List<Index> result = Collections15.arrayList();
    for (IndexInternal index : all) {
      if (index.getInfo().getCondition().isAccepted(atom))
        result.add(index);
    }
    return result.toArray(new Index[result.size()]);
  }

  public Index getIndex(String indexName) {
    checkStarted();
    return myState.getIndex(indexName);
  }

  public Index getIndex(int indexID) {
    checkStarted();
    return myState.getIndex(indexID);
  }

  public Index getGlobalIndex() {
    checkStarted();
    return myState.getGlobalIndex();
  }

  public boolean isDefaultIndexing() {
    checkStarted();
    return myState.isDefaultIndexing();
  }

  public void setDefaultIndexing(boolean autoIndexing) {
    checkStarted();
    myState.setDefaultIndexing(autoIndexing);
  }

  public Expansion begin() {
    checkStarted();
    return new ExpansionImpl(myState, myDataFile);
  }

  private void rescanDataFile() {
    try {
      myDataFile.readAll(new ExpansionInfoSink() {
        public boolean visitExpansionInfo(ExpansionInfo info) {
          myState.expansionRead(info);
          return true;
        }
      });
      int count = myState.cleanPendingAtoms();
//      System.out.println("count = " + count);
    } catch (FileFormatException e) {
      throw new Failure(e); // todo
    } catch (IOException e) {
      throw new Failure(e); // todo
    }
  }

  public void setReadOnly(boolean readOnly) {
    myState.setReadOnly(readOnly);
  }
}
