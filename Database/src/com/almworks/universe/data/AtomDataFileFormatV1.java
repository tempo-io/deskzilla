package com.almworks.universe.data;

import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.universe.optimize.UniverseFileOptimizer;
import com.almworks.util.fileformats.AlmworksFormatsUtil;
import com.almworks.util.fileformats.FileFormatException;
import com.almworks.util.io.BufferedDataInput;
import com.almworks.util.io.BufferedDataOutput;
import org.almworks.util.Collections15;
import org.almworks.util.Failure;
import util.external.*;

import java.io.*;
import java.util.*;

public class AtomDataFileFormatV1 implements AtomDataFileFormat, Versions {
  private static final int HOSTED_THRESHOLD = 32;
  public static final int SIGNATURE = 0xDA7AF11E;
  public static final int CRC_BLOCK_LENGTH = 5;
  private static final int EXPANSION_MARKER = 0xFFFFFFFF;
  private static final int ATOM_MARKER = 0x80;
  private static final int NO_MORE_ATOMS_MARKER = 0xC0;
  private static final int VALUECODE_COMPACTLONG = 00;
  private static final int VALUECODE_BYTEARRAY = 07;
  private static final int VALUECODE_STRING = 06;
  private static final int EXPANSION_END_MARKER = 0xAAAAAAAA;
  private static final byte PADDING_BYTE = 0x00;

  public static final short MY_VERSION = DATA_FILE_VERSION_1_3;
  private static final short MY_MIN_VERSION = DATA_FILE_VERSION_1;
  private static final short MY_MAX_VERSION = MY_VERSION + VERSION_AGE;
  private static final int CUSTOM_PROPERTIES_MARK = 'P';

  public AtomDataFileFormatV1() {
  }

  public void writeNewHeader(ADFMetaInfo metaInfo, BufferedDataOutput output) throws IOException {
    int posStart = output.getPosition();
    output.writeInt(SIGNATURE);
    int posLength = output.getPosition();
    output.writeInt(0); // size
    metaInfo.getUID().write(output);
    output.writeShort(MY_VERSION);
    writeCustomProperties(output, metaInfo.getCustomProperties());
    int posEnd = output.getPosition();
    output.setPosition(posLength);
    output.writeInt((int) (posEnd + CRC_BLOCK_LENGTH));
    output.setPosition(posEnd);
    AlmworksFormatsUtil.putCRC(output, posStart, posEnd - posStart);
  }

  private void writeCustomProperties(BufferedDataOutput output, Map<String, String> customProperties)
    throws IOException {

    if (customProperties == null || customProperties.size() == 0)
      return;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(buffer);
    for (Iterator<Map.Entry<String, String>> ii = customProperties.entrySet().iterator(); ii.hasNext();) {
      Map.Entry<String, String> entry = ii.next();
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || value == null)
        continue;
      CompactChar.writeString(out, key);
      CompactChar.writeString(out, value);
    }
    CompactChar.writeString(out, null);
    out.close();
    byte[] bytes = buffer.toByteArray();

    output.writeByte(CUSTOM_PROPERTIES_MARK);
    CompactInt.writeInt(output, bytes.length);
    output.write(bytes);
  }


  public ADFMetaInfo readHeader(BufferedDataInput input) throws IOException, FileFormatException {
    int posStart = input.getPosition();
    int signature = input.readInt();
    if (signature != SIGNATURE)
      throw new AtomDataFileException("incorrect format - bad magic number");
    int length = input.readInt();
    final UID resultUID = UID.read(input);
    short version = input.readShort();
    if (!isSupported(version))
      throw new AtomDataFileException("data file format version " + version + " is not supported");
    final Map<String, String> props = readCustomElementsAndCRC(input, posStart);

    return new ADFMetaInfo() {
      public UID getUID() {
        return resultUID;
      }

      public Map<String, String> getCustomProperties() {
        return props;
      }
    };
  }

  public void skipPadding(BufferedDataInput input) throws IOException {
    try {
      while (true) {
        byte b = input.readByte();
        if (b != PADDING_BYTE) {
          input.unread(1);
          break;
        }
      }
    } catch (EOFException e) {
      // it's ok - let caller find out he's at EOF
    }
  }

  public ExpansionInfo readExpansion(BufferedDataInput input) throws IOException, FileFormatException {
    int posStart = input.getPosition();
    int marker = input.readInt();
    if (marker != EXPANSION_MARKER)
      throw new AtomDataFileException("data file corrupt: bad marker " + marker);

    long UCN = CompactInt.readLong(input);
    List<Atom> atoms = Collections15.arrayList();
    while (true) {
      int atomMarker = input.readByte() & 0xFF;
      if (atomMarker == NO_MORE_ATOMS_MARKER)
        break;
      if (atomMarker != ATOM_MARKER)
        throw new AtomDataFileException("data file corrupt: bad atom marker " + atomMarker);
      long atomID = CompactInt.readLong(input);
      Atom atom = new Atom(atomID, 0);

      while (true) {
        int junctionMarker = input.readByte() & 0xFF;
        if (junctionMarker == NO_MORE_ATOMS_MARKER || junctionMarker == ATOM_MARKER) {
          input.unread(1);
          break;
        }
        if ((junctionMarker & 0xC0) == 0x40) {
          // extension
          readAtomExtension(junctionMarker & 0x3F, input);
          continue;
        }
        // read junction
        int oldCode1 = (junctionMarker >>> 3) & 0x07;
        // support for previous versions - could be unsupported
        if (oldCode1 != VALUECODE_COMPACTLONG)
          throw new UnsupportedOperationException("your database format is not supported, please migrate"); // todo
        int valueCode = (junctionMarker) & 0x3F;

        long junctionKey = CompactInt.readLong(input);
        Particle junctionValue = readValue(valueCode, input);
        atom.buildJunction(junctionKey, junctionValue);
      }
      atom.buildFinished(UCN);
      atoms.add(atom);
    }
    readCustomElementsAndCRC(input, posStart);
    marker = input.readInt();
    if (marker != EXPANSION_END_MARKER)
      throw new AtomDataFileException("data file corrupt: invalid expansion end marker " + marker);
    ExpansionInfo info = new ExpansionInfo(UCN, atoms.toArray(new Atom[atoms.size()]));
    return info;
  }

  private Particle readValue(int code, BufferedDataInput input) throws IOException, AtomDataFileException {
    switch (code) {
    case VALUECODE_COMPACTLONG:
      return Particle.createLong(CompactInt.readLong(input));
    case VALUECODE_BYTEARRAY:
      int length = CompactInt.readInt(input);
      if (length < 0)
        throw new AtomDataFileException("data file corrupt: bad bytearray length " + length);
      if (length > HOSTED_THRESHOLD && UniverseFileOptimizer.isActive()) {
        long firstLong = input.readLong();
        if ((firstLong & ~0x7FFFFFFFL) == 0) {
          // positive int
          int position = (int) input.getCarrierFilePosition();
          int len = length - 8;
          input.skipBytes(len);
          return new Particle.PFileHostedBytes((int) firstLong, position, len);
        } else {
          input.unread(8);
        }
      }
      byte[] array = new byte[length];
      input.readFully(array);
      return Particle.createBytes(array);
    case VALUECODE_STRING:
      return Particle.createIsoString(CompactChar.readString(input));
    default:
      throw new AtomDataFileException("data file corrupt: unknown value type " + code);
    }
  }

  private void readAtomExtension(int extensionID, BufferedDataInput input) throws IOException, AtomDataFileException {
    // no extensions right now, skipping
    int length = CompactInt.readInt(input);
    skipBytes(input, length);
  }

  public void writeExpansion(ExpansionInfo expansionInfo, final BufferedDataOutput output) throws IOException,
    AtomDataFileException {

    int posStart = output.getPosition();
    output.writeInt(EXPANSION_MARKER);
    CompactInt.writeLong(output, expansionInfo.UCN);
    for (int i = 0; i < expansionInfo.atoms.length; i++) {
      Atom atom = expansionInfo.atoms[i];
      output.writeByte(ATOM_MARKER);
      CompactInt.writeLong(output, atom.getAtomID());
      Exception exception = atom.visit(null, new Atom.Visitor<Exception>() {
        public Exception visitJunction(long key, Particle particle, Exception passThrough) {
          if (passThrough != null)
            return passThrough;
          try {
            int code = getValueTypeCode(particle);
            int junctionCode = 0x00 | (code & 0x3F);
            output.writeByte(junctionCode);
            CompactInt.writeLong(output, key);
            writeValue(output, particle, code);
            return null;
          } catch (AtomDataFileException e) {
            return e;
          } catch (IOException e) {
            return e;
          }
        }
      });
      if (exception != null) {
        if (exception instanceof AtomDataFileException)
          throw (AtomDataFileException) exception;
        if (exception instanceof IOException)
          throw (IOException) exception;
        throw new Failure((Exception) exception);
      }
    }
    output.writeByte(NO_MORE_ATOMS_MARKER);
    AlmworksFormatsUtil.putCRC(output, posStart);
    output.writeInt(EXPANSION_END_MARKER);
  }

  private void writeValue(BufferedDataOutput output, Particle value, int code) throws IOException,
    AtomDataFileException {

    switch (code) {
    case VALUECODE_COMPACTLONG:
      CompactInt.writeLong(output, ((Particle.PLong) value).getValue());
      break;
    case VALUECODE_STRING:
      CompactChar.writeString(output, ((Particle.PIsoString) value).getValue());
      break;
    case VALUECODE_BYTEARRAY:
      byte[] bytes = value.raw();
      CompactInt.writeInt(output, bytes.length);
      output.write(bytes);
      break;
    default:
      throw new AtomDataFileException("value code " + code + " is not supported");
    }
  }

  private int getValueTypeCode(Particle value) {
    if (value instanceof Particle.PLong)
      return VALUECODE_COMPACTLONG;
    else if (value instanceof Particle.PIsoString)
      return VALUECODE_STRING;
    else
      return VALUECODE_BYTEARRAY;
  }

  private void readUnknownCustomElement(int elementType, BufferedDataInput input) throws AtomDataFileException, IOException {
    int length = CompactInt.readInt(input);
    if (length < 0)
      throw new AtomDataFileException("data file is corrupt, custom element length " + length);
    skipBytes(input, length);
  }

  private void skipBytes(BufferedDataInput input, int length) throws IOException, AtomDataFileException {
    int n = length;
    while (n > 0) {
      int skipped = input.skipBytes(n);
      if (skipped <= 0) {
        throw new AtomDataFileException("data file is corrupt, custom element spans over eof (length " + length + ")");
      }
      n -= skipped;
    }
  }

  private boolean isSupported(short version) {
    return version >= MY_MIN_VERSION && version < MY_MAX_VERSION;
  }

  private Map<String, String> readCustomElementsAndCRC(BufferedDataInput input, int posStart) throws IOException,
    FileFormatException {

    Map<String, String> customProperties = null;

    while (true) {
      int elementType = input.readByte();
      if (elementType == 'C') {
        input.unread(1);
        AlmworksFormatsUtil.checkCRC(input, posStart);
        break;
      } else if (elementType == CUSTOM_PROPERTIES_MARK) {
        customProperties = readCustomProperties(input);
      } else {
        readUnknownCustomElement(elementType, input);
      }
    }

    return customProperties;
  }

  private Map<String, String> readCustomProperties(BufferedDataInput input) throws IOException {
    Map<String, String> result = Collections15.hashMap();
    int length = CompactInt.readInt(input);
    while (true) {
      String key = CompactChar.readString(input);
      if (key == null)
        break;
      String value = CompactChar.readString(input);
      result.put(key, value);
    }
    return result;
  }


}
