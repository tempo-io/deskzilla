package com.almworks.database.bitmap;

import com.almworks.api.database.WCN;
import com.almworks.util.fileformats.*;
import com.almworks.util.io.*;
import org.almworks.util.Log;
import org.almworks.util.RuntimeInterruptedException;
import util.external.BitSet2;
import util.external.CompactInt;

import java.io.*;

/**
 * Bitmap Index File ::= Header Bitmap Crc
 * Header ::= Signature Version HeaderLength WCN <additional bytes>
 * Bitmap ::=
 */
class BitmapIndexFile extends FormattedFile {
  private static final int CURRENT_VERSION = 1;
  private static final int SIGNATURE = 0x11DE00F1;

  public BitmapIndexFile(File path) {
    super(path, true);
  }

  public synchronized BitmapIndexInfo loadIndex() throws IOException {
    try {
      open();
      BitmapIndexInfo info = readInfo();
      close();
      return info;
    } catch (FileFormatException e) {
      removeCorruptFile(e);
      return null;
    } catch (EOFException e) {
      removeCorruptFile(e);
      return null;
    } finally {
      try {
        close();
      } catch (Exception e) {
        // ignore
      }
    }
  }

  private void removeCorruptFile(Exception e) throws IOException {
    Log.warn(this + " corrupt, removing", e);
    try {
      drop();
    } catch (InterruptedException ee) {
      throw new RuntimeInterruptedException(ee);
    }
  }

  public synchronized void saveIndex(BitSet2 bits, WCN wcn) throws IOException {
    try {
      if (myPath.exists())
        drop();
    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    } catch (IOException e) {
      // ignore
    }

    create();
    writeFile(bits, wcn);
    close();
  }

  private void writeFile(BitSet2 bits, WCN wcn) throws IOException {
    OutputPump pump = new OutputPump(myChannel);
    writeHeader(pump, wcn.getUCN());
    writeBits(pump, bits);
    AlmworksFormatsUtil.putCRC(pump, 0);
    pump.flush();
  }

  private void writeBits(BufferedDataOutput output, BitSet2 bits) throws IOException {
    bits.writeTo(output);
  }

  private void writeHeader(BufferedDataOutput output, long ucn) throws IOException {
    output.writeInt(SIGNATURE);
    CompactInt.writeInt(output, CURRENT_VERSION);
    int length = output.getPosition();
    output.writeInt(0);
    CompactInt.writeLong(output, ucn);
    int pos = output.getPosition();
    output.setPosition(length);
    output.writeInt(pos);
    output.setPosition(pos);
  }

  private BitSet2 readBitmap(BufferedDataInput input) throws IOException {
    BitSet2 result = new BitSet2();
    result.readFrom(input);
    return result;
  }

  private WCN readHeader(BufferedDataInput input) throws IOException, FileFormatException {
    int signature = input.readInt();
    if (signature != SIGNATURE)
      throw new FileFormatException("invalid signature");
    int version = CompactInt.readInt(input);
    if (version != CURRENT_VERSION)
      Log.warn("ri v" + version);
    int length = input.readInt();

    long ucn = CompactInt.readLong(input);

    int pos = input.getPosition();
    if (pos < length)
      input.skipBytes(length - pos);
    return WCN.createWCN(ucn);
  }

  private BitmapIndexInfo readInfo() throws FileFormatException, IOException {
    InputPump pump = new InputPump(myChannel);
    WCN wcn = readHeader(pump);
    BitSet2 bits = readBitmap(pump);
    AlmworksFormatsUtil.checkCRC(pump, 0);
    pump.discard();
    return new BitmapIndexInfo(bits, wcn);
  }
}
