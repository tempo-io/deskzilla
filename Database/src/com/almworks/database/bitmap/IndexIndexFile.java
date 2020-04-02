package com.almworks.database.bitmap;

import com.almworks.util.commons.Lazy;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.fileformats.*;
import com.almworks.util.io.*;
import com.almworks.util.io.persist.*;
import com.almworks.util.threads.Bottleneck;
import org.almworks.util.Log;
import org.almworks.util.RuntimeInterruptedException;
import util.external.CompactInt;
import util.external.UID;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Format:
 * IndexIndexFile ::= Header Map Crc
 * Header ::= Signature Version Length <Arbitrary Data>
 * Map ::= persistable-hash-map-string-
 * string
 * Crc ::= all-crc
 */
public class IndexIndexFile extends FormattedFile {
  private final Persistable<Map<String, String>> myData =
    new PersistableHashMap<String, String>(new PersistableString(), new PersistableString());
  private boolean myLoaded;
  private final File myIndexDir;
  private final String myIndexPrefix;

  private Lazy<Bottleneck> mySave = new Lazy<Bottleneck>() {
    public Bottleneck instantiate() {
      return new Bottleneck(2000, ThreadGate.LONG(this), new Runnable() {
        public void run() {
          doSave();
        }
      });
    }
  };

  private static final int SIGNATURE = 0x11DE11DE;
  private static final int CURRENT_VERSION = 1;

  public IndexIndexFile(File indexIndexPath, File indexDir, String indexPrefix) {
    super(indexIndexPath, true);
    myIndexDir = indexDir;
    myIndexPrefix = indexPrefix;
  }

  public synchronized File getIndexFileForKey(String key) throws IOException {
    loadIndexIndex();
    Map<String, String> map = myData.access();
    String fileSuffix = map.get(key);
    if (fileSuffix == null) {
      Collection<String> files = map.values();
      do
        fileSuffix = new UID().toString();
      while (files.contains(fileSuffix));
      map.put(key, fileSuffix);
      scheduleSave();
    }
    assert fileSuffix != null;
    return new File(myIndexDir, myIndexPrefix + fileSuffix);
  }


  public boolean hasIndexFileForKey(String indexKey) throws IOException {
    loadIndexIndex();
    Map<String, String> map = myData.access();
    String fileSuffix = map.get(indexKey);
    return fileSuffix != null;
  }


  private void scheduleSave() {
    mySave.get().run();
  }

  private void doSave() {
    try {
      loadIndexIndex();
      drop();
      create();
      close();
    } catch (IOException e) {
      Log.warn("error writing index index", e);
    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }


  private void loadIndexIndex() throws IOException {
    if (myLoaded)
      return;
    try {
      try {
        if (myPath.exists())
          open();
        else
          create();
      } catch (FileFormatException e) {
        try {
          drop();
        } catch (InterruptedException ee) {
          throw new RuntimeInterruptedException(ee);
        }
        create();
      }
      close();
    } finally {
      myLoaded = true;
    }
  }

  protected void doOpen() throws IOException, FileFormatException {
    InputPump pump = new InputPump(myChannel);
    readHeader(pump);
    readMap(pump);
    AlmworksFormatsUtil.checkCRC(pump, 0);
    pump.discard();
  }

  private void readMap(BufferedDataInput input) throws IOException, FileFormatException {
    try {
      myData.restore(input);
    } catch (FormatException e) {
      throw new FileFormatException(e);
    }
  }

  private void readHeader(BufferedDataInput input) throws IOException, FileFormatException {
    int signature = input.readInt();
    if (signature != SIGNATURE)
      throw new FileFormatException("invalid signature");
    int version = CompactInt.readInt(input);
    if (version != CURRENT_VERSION)
      Log.warn("rii v" + version);
    int length = input.readInt();
    int pos = input.getPosition();
    if (pos < length)
      input.skipBytes(length - pos);
  }

  protected void doCreate() throws IOException {
    OutputPump pump = new OutputPump(myChannel);
    writeHeader(pump);
    writeMap(pump);
    AlmworksFormatsUtil.putCRC(pump, 0);
    pump.flush();
  }

  private void writeMap(BufferedDataOutput output) throws IOException {
    myData.store(output);
  }

  private void writeHeader(BufferedDataOutput output) throws IOException {
    output.writeInt(SIGNATURE);
    CompactInt.writeInt(output, CURRENT_VERSION);
    int pos = output.getPosition();
    output.writeInt(pos + 4);
  }
}
