package com.almworks.universe.data;

import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.util.fileformats.AlmworksFormatsUtil;
import com.almworks.util.fileformats.FileFormatException;
import com.almworks.util.io.BufferedDataOutput;
import com.almworks.util.io.OutputPump;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;
import util.external.CompactInt;
import util.external.UID;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class AtomDataFileTests extends BaseTestCase {
  private FileOutputStream myOutputStream = null;
  private File myFileName;

  protected void setUp() throws Exception {
    super.setUp();
    myOutputStream = null;
    myFileName = null;
  }

  protected void tearDown() throws Exception {
    if (myOutputStream != null) {
      try {
        myOutputStream.close();
      } catch (IOException e) {
      }
      myOutputStream = null;
    }
    super.tearDown();
  }

  public void testFileCreation() throws IOException, FileFormatException {
    AtomDataFile dataFile = createDataFile();
    UID fileID = dataFile.getUID();
    dataFile.close();

    dataFile = new AtomDataFile(myFileName);
    dataFile.open();
    assertEquals(fileID, dataFile.getUID());
    assertTrue(dataFile.isOpen());
    dataFile.close();
  }

  public void testFutureVersionOfFile() throws IOException, FileFormatException {
    BufferedDataOutput output = new OutputPump(openTempFile());
    int posStart = output.getPosition();
    output.writeInt(AtomDataFileFormatV1.SIGNATURE);
    int posLength = output.getPosition();
    output.writeInt(0); // size
    UID uid = new UID();
    uid.write(output);
    output.writeShort(AtomDataFileFormatV1.MY_VERSION + 100);
    // now writing custom element
    output.writeByte(255); // marker
    CompactInt.writeInt(output, 8000); // write length
    output.write(new byte[8000]); // write content
    output.writeByte(254); // another element
    CompactInt.writeInt(output, 1); // write length
    output.write(new byte[1]); // write content
    int posEnd = output.getPosition();
    output.setPosition(posLength);
    output.writeInt((int) (posEnd + AtomDataFileFormatV1.CRC_BLOCK_LENGTH));
    output.setPosition(posEnd);
    AlmworksFormatsUtil.putCRC(output, posStart, posEnd - posStart);
    ((OutputPump) output).close();
    closeFile();

    // now read
    AtomDataFile file = new AtomDataFile(myFileName);
    file.open();
    assertEquals(uid, file.getUID());
  }

  public void testWriteReadExpansion() throws FileFormatException, IOException {
    AtomDataFile file = createDataFile();
    Atom atom1 = new Atom(999, 2);
    atom1.buildJunction(1, Particle.createEmpty());
    atom1.buildJunction(2, Particle.createLong(2));
    atom1.buildJunction(3, Particle.createIsoString("3"));
    byte[] bytes = new byte[1];
    bytes[0] = 4;
    atom1.buildJunction(4, Particle.createBytes(bytes));
    Atom atom2 = new Atom(1000, 0);
    atom2.buildJunction(5, Particle.createEmpty());
    ExpansionInfo info = new ExpansionInfo(1001, new Atom[]{atom1, atom2});
    file.write(info);

    final List<ExpansionInfo> infos = Collections15.arrayList();
    file.readAll(new ExpansionInfoSink() {
      public boolean visitExpansionInfo(ExpansionInfo info) {
        infos.add(info);
        return true;
      }
    });
    assertEquals(1, infos.size());
    info = infos.get(0);

    assertEquals(info.UCN, 1001);
    assertEquals(2, info.atoms.length);
    Atom atom = info.atoms[0];
    assertEquals(999, atom.getAtomID());
    assertEquals(0, atom.get(1).raw().length);
    assertEquals(2, atom.getLong(2));
    assertEquals("3", atom.getString(3));
    assertEquals(1, atom.get(4).raw().length);
    assertEquals(4, atom.get(4).raw()[0]);
    atom = info.atoms[1];
    assertEquals(0, atom.get(5).raw().length);

    file.close();
  }

  public void testReadEmptyFile() throws FileFormatException, IOException {
    AtomDataFile file = createDataFile();
    file.close();
    file.open();
    final int[] counter = {0};
    file.readAll(new ExpansionInfoSink() {
      public boolean visitExpansionInfo(ExpansionInfo info) {
        counter[0]++;
        return true;
      }
    });
    assertEquals(0, counter[0]);
  }

  public void testCustomProperties() throws IOException, FileFormatException {
    createTempName();
    AtomDataFile dataFile = new AtomDataFile(myFileName);
    dataFile.setCustomProperties(Collections.singletonMap("key", "val"));
    dataFile.create();
    assertTrue(dataFile.isOpen());
    Map<String, String> props = dataFile.getCustomProperties();
    assertEquals(1, props.size());
    assertEquals("val", props.get("key"));
    dataFile.close();
    dataFile.open();
    props = dataFile.getCustomProperties();
    assertEquals(1, props.size());
    assertEquals("val", props.get("key"));
    dataFile.close();
    dataFile = new AtomDataFile(myFileName);
    dataFile.open();
    props = dataFile.getCustomProperties();
    assertEquals(1, props.size());
    assertEquals("val", props.get("key"));
    dataFile.close();
  }

  private AtomDataFile createDataFile() throws IOException, AtomDataFileException {
    createTempName();
    AtomDataFile dataFile = new AtomDataFile(myFileName);
    dataFile.create();
    assertTrue(dataFile.isOpen());
    return dataFile;
  }

  private File createTempName() throws IOException {
    myFileName = createFileName();
    return myFileName;
  }

  private void closeFile() {
    if (myOutputStream != null) {
      try {
        myOutputStream.close();
      } catch (IOException e) {
      }
      myOutputStream = null;
    }
  }

  private FileChannel openTempFile() throws IOException {
    createTempName();
    myOutputStream = new FileOutputStream(myFileName);
    return myOutputStream.getChannel();
  }
}
