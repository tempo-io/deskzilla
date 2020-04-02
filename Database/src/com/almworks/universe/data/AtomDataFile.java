package com.almworks.universe.data;

import com.almworks.util.fileformats.FileFormatException;
import com.almworks.util.fileformats.FormattedFile;
import com.almworks.util.io.*;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import util.external.UID;

import java.io.*;
import java.util.Collections;
import java.util.Map;

public class AtomDataFile extends FormattedFile implements ADFMetaInfo {
  private final Object myAccessLock = new Object();

  private UID myUID;
  private final AtomDataFileFormat myFormat = new AtomDataFileFormatV1();
  private Map<String,String> myCustomProperties = null;

  public AtomDataFile(File path) {
    super(path, true);
    //myForceDiskWriteEachTime = true;
  }

  public void open() throws IOException, FileFormatException {
    synchronized (myAccessLock) {
      super.open();
    }
  }

  public void close() {
    synchronized (myAccessLock) {
      super.close();
    }
  }

  public void create() throws IOException {
    synchronized (myAccessLock) {
      super.create();
    }
  }

  public void drop() throws InterruptedException, IOException {
    synchronized (myAccessLock) {
      super.drop();
    }
  }

  protected void doOpen() throws IOException, FileFormatException {
    InputPump input = new InputPump(myChannel);
    ADFMetaInfo metaInfo = myFormat.readHeader(input);
    setMetaInfo(metaInfo);
    input.discard();
  }

  protected void doCreate() throws IOException {
    createNewMetaInfo();
    OutputPump output = new OutputPump(myChannel);
    myFormat.writeNewHeader(this, output);
    output.flush();
  }

  protected void doClose() {
  }

  protected void doDrop() {
  }

  protected void cleanUp() {
    Log.warn("closing data file", new Throwable());
    super.cleanUp();
  }

  public void write(ExpansionInfo expansionInfo) throws AtomDataFileException {
    checkOpen();
    boolean success = false;
    synchronized (myAccessLock) {
      try {
        myChannel.position(myChannel.size());
        OutputPump output = new OutputPump(myChannel);
        myFormat.writeExpansion(expansionInfo, output);
        output.flush();
        if (myForceDiskWriteEachTime)
          myChannel.force(false);
        success = true;
      } catch (IOException e) {
        // #564
        throw new AtomDataFileException(e); //todo - fail application
      } finally {
        if (!success)
          cleanUp();
      }
    }
  }

  public void readAll(ExpansionInfoSink sink) throws FileFormatException, IOException {
    synchronized (myAccessLock) {
      InputPump input = new InputPump(myChannel, 16 * 1024, 0);
      myFormat.readHeader(input);
      myFormat.skipPadding(input);
      input.discard();
      try {
        while (true) {
          if (atEOF(input))
            break;
          ExpansionInfo info = myFormat.readExpansion(input);
          myFormat.skipPadding(input);
          input.discard();
          boolean proceed = sink.visitExpansionInfo(info);
          if (!proceed)
            break;
        }
      } catch (EOFException e) {
        throw new AtomDataFileException(e); // todo
      }
    }
  }

  private boolean atEOF(BufferedDataInput input) throws IOException {
    try {
      input.readByte();
      input.unread(1);
      return false;
    } catch (EOFException e) {
      return true;
    }
  }

  public UID getUID() {
    return myUID;
  }

  private void createNewMetaInfo() {
    myUID = new UID();
  }

  private void setMetaInfo(ADFMetaInfo metaInfo) {
    myUID = metaInfo.getUID();
    Map<String, String> customProperties = metaInfo.getCustomProperties();
    myCustomProperties = customProperties == null ? null : Collections15.hashMap(customProperties);
  }

  public void setCustomProperties(Map<String, String> map) {
    checkNotOpen();
    myCustomProperties = map == null ? null : Collections15.hashMap(map);
  }

  public Map<String, String> getCustomProperties() {
    return myCustomProperties == null ? null : Collections.unmodifiableMap(myCustomProperties);
  }

  public RandomAccessFile accessFile() {
    checkOpen();
    return myFile;
  }

  public Object accessLock() {
    checkOpen();
    return myAccessLock;
  }
}
