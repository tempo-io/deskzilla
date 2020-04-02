package com.almworks.util.config;

import com.almworks.util.BadFormatException;
import com.almworks.util.tests.*;
import org.almworks.util.Const;

import java.io.*;

/**
 * @author dyoma
 */
public class MediumToXmlWriterTests extends BaseTestCase {
  private final FileCompare myFileCompare = new FileCompare(getClass().getClassLoader());
  private static final String SYMBOLS = "!@#$%^&*()_+-=\"\'\\/<>?,.`~[]{}:;\u2011";
  private static final String[] SETTING_VALUES = new String[]{"a", XMLWriterTests.RUS, "a\nb", "line\r\nline2", SYMBOLS};
  private final CollectionsCompare CHECK = new CollectionsCompare();
  private static final int SAVE_TIMES = 3;

  protected void setUp() throws Exception {
    super.setUp();
    myFileCompare.setCharsetName("UTF-8");
    myFileCompare.setPathPrefix("com/almworks/util/config");
    myFileCompare.setSourcePrefix("Utils/tests.rc");
  }

  public void testSpecialCases() throws IOException, BadFormatException, ReadonlyConfiguration.NoSettingException {
    MapMedium medium = new MapMedium(null, "root");
    Configuration configuration = Configuration.createWritable(medium);
    configuration.setSettings("setting", SETTING_VALUES);
    configuration.setSetting("1", 1);
    configuration.setSettings("noValue", Const.EMPTY_STRINGS);
    configuration.setSetting(SYMBOLS, "");
    configuration.createSubset("1");
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    MediumToXmlWriter.writeMedium(medium, stream);
//    myFileCompare.writeFile("SpecialCases.xml", stream.toByteArray());
    myFileCompare.compareText("SpecialCases.xml", new String(stream.toByteArray(), "UTF-8"));

    ReadonlyConfiguration read = JDOMConfigurator.parse(new ByteArrayInputStream(stream.toByteArray()));
    assertEquals("root", read.getName());
    CHECK.order(SETTING_VALUES, read.getAllSettings("setting"));
    assertEquals(1, read.getIntegerSetting("1", -1));
    CHECK.empty(read.getAllSettings("noValue"));
    assertEquals("", read.getMandatorySetting(SYMBOLS));
    CHECK.unordered(read.getAllSettingNames(), new String[]{"setting", "1", SYMBOLS});
    CHECK.singleElement("1", read.getAllSubsetNames());
    ReadonlyConfiguration subset = read.getSubset("1");
    assertTrue(subset.getAllSettingNames().isEmpty());
    assertTrue(subset.getAllSubsetNames().isEmpty());
  }

  public void testPerformance() throws IOException, BadFormatException {
    InputStream stream = myFileCompare.getStream("Huge.xml");
    long start = System.currentTimeMillis();
    ReadonlyConfiguration configuration = JDOMConfigurator.parse(stream);
    long loadTime = System.currentTimeMillis() - start;
    System.out.println("loadTime = " + loadTime);
    stream.close();
    MapMedium copy = new MapMedium(null, configuration.getName());
    ConfigurationUtil.copyTo(configuration, Configuration.createWritable(copy));
    configuration = null;
    runSave(copy);
    start = System.currentTimeMillis();
    for (int i = 0; i < SAVE_TIMES; i++)
      runSave(copy);
    long saveTime = System.currentTimeMillis() - start;
    saveTime = saveTime / SAVE_TIMES;
    System.out.println("saveTime = " + saveTime);
//    System.exit(1);
  }

  private void runSave(ReadonlyMedium<? extends ReadonlyMedium> copy) throws IOException {
    ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
    BufferedOutputStream bufferedOutput = new BufferedOutputStream(byteOutput);
    MediumToXmlWriter.writeMedium(copy, bufferedOutput);
    bufferedOutput.flush();
  }
}
