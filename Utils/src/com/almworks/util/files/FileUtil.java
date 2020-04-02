package com.almworks.util.files;

import com.almworks.util.collections.ByteArray;
import com.almworks.util.io.IOUtils;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.math.BigDecimal;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Pattern;

public class FileUtil {
  private static final Map<String, Integer> SIZE_MULTIPLIERS = createMultipliers();

  private static Map<String, Integer> createMultipliers() {
    HashMap<String, Integer> map = Collections15.hashMap();
    map.put("BYTES", 1);
    map.put("BYTE", 1);
    map.put("B", 1);
    map.put("KB", 1024);
    map.put("KBYTES", 1024);
    map.put("MB", 1024 * 1024);
    map.put("MBYTES", 1024 * 1024);
    return Collections.unmodifiableMap(map);
  }


  public static byte[] readBytes(InputStream stream) throws IOException {
    assert stream != null;
    ByteArray byteArray = new ByteArray();
    byteArray.readAllFromStream(stream);
    return byteArray.toNativeArray();
  }

  public static String loadTextResource(String resourceName, ClassLoader classLoader) {
    byte[] bytes = loadResource(resourceName, classLoader);
    if (bytes == null)
      throw new Failure("Missing resource: " + resourceName + ". Class loaded: " + classLoader);
    return new String(bytes);
  }

  public static byte[] loadResource(String resourceName, ClassLoader classLoader) {
    InputStream stream = classLoader.getResourceAsStream(resourceName);
    try {
      return stream != null ? readBytes(stream) : null;
    } catch (IOException e) {
      throw new Failure(e);
    } finally {
      IOUtils.closeStreamIgnoreExceptions(stream);
    }
  }

  public static byte[] loadFile(File file) throws IOException {
    InputStream stream = null;
    try {
      stream = new FileInputStream(file);
      return readBytes(stream);
    } finally {
      IOUtils.closeStreamIgnoreExceptions(stream);
    }
  }


  public static void copyAndClose(InputStream inputStream, OutputStream outputStream) throws IOException {
    try {
      byte[] bytes = readBytes(inputStream);
      outputStream.write(bytes);
    } finally {
      try {
        inputStream.close();
        outputStream.close();
      } catch (IOException e) {
      }
    }
  }

  public static Properties loadProperties(ClassLoader classLoader, String resourceName) {
    Properties properties = new Properties();
    InputStream stream = classLoader.getResourceAsStream(resourceName);
    try {
      properties.load(stream);
    } catch (IOException e) {
      throw new Failure(e);
    } finally {
      IOUtils.closeStreamIgnoreExceptions(stream);
    }
    return properties;
  }

  public static void copyFile(@NotNull File source, @NotNull File destination) throws IOException {
    copyFile(source, destination, true);
  }

  public static void copyFile(@NotNull File source, @NotNull File destination, boolean keepModificationTime)
    throws IOException
  {
// todo currently this assertion causes problems, because actions do want to copy files in UI thread 
//    Threads.assertLongOperationsAllowed();
    FileInputStream in = null;
    FileOutputStream out = null;
    try {
      in = new FileInputStream(source);
      out = new FileOutputStream(destination);
      IOUtils.transfer(in, out);
    } finally {
      IOUtils.closeStreamIgnoreExceptions(in);
      IOUtils.closeStreamIgnoreExceptions(out);
    }
    if (keepModificationTime) {
      long lastModified = 0;
      try {
        lastModified = source.lastModified();
      } catch (Exception e) {
        // ignore
      }
      if (lastModified != 0) {
        try {
          destination.setLastModified(lastModified);
        } catch (Exception e) {
          // ignore
        }
      }
    }
  }

  public static void writeFile(File file, String content) throws IOException {
    writeFile(file, content.getBytes());
  }

  public static void writeFile(File file, String content, String charset) throws IOException {
    writeFile(file, content.getBytes(charset));
  }

  public static void writeFile(File file, byte[] bytes) throws IOException {
    FileOutputStream stream = null;
    try {
      stream = new FileOutputStream(file);
      stream.write(bytes);
    } finally {
      IOUtils.closeStreamIgnoreExceptions(stream);
    }
  }

  public static String readFile(File file) throws IOException {
    return new String(loadFile(file));
  }

  public static String readFile(String file, String encoding) throws IOException {
    return new String(loadFile(new File(file)), encoding);
  }

  public static boolean deleteFile(File file, boolean tryDeleteOnExitIfFailing) throws InterruptedException {
//    Threads.assertLongOperationsAllowed();
    int attempts = 10;
    for (int i = 0; i < attempts; i++) {
      boolean success = file.delete();
      if (success)
        return true;
      Thread.sleep(50);
    }
    if (tryDeleteOnExitIfFailing)
      file.deleteOnExit();
    return false;
  }

  public static void mkdir(File dir, int attempts) throws IOException, InterruptedException {
    boolean success = dir.mkdir();
    while (!success) {
      if (--attempts < 0)
        throw new IOException("cannot create " + dir);
      if (dir.exists())
        throw new IOException(dir + " exists");
      File parent = dir.getParentFile();
      if (parent != null) {
        if (!parent.exists()) {
          throw new IOException(parent + " does not exist");
        }
        if (!parent.isDirectory()) {
          throw new IOException(parent + " is not a directory");
        }
      }
      Thread.sleep(500);
      success = dir.mkdir();
    }
  }

  public static void renameTo(File src, File dest, int attempts) throws IOException, InterruptedException {
    boolean success = src.renameTo(dest);
    while (!success) {
      if (--attempts < 0)
        throw new IOException("cannot rename " + src + " to " + dest);
      if (dest.exists())
        throw new IOException(dest + " exists");
      Thread.sleep(100);
      success = src.renameTo(dest);
    }
  }

  public static byte[] native2ascii(File file, String encoding) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
    FileInputStream fis = null;
    PrintWriter writer = null;
    try {
      fis = new FileInputStream(file);
      BufferedReader reader = new BufferedReader(new InputStreamReader(fis, encoding));
      writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out)));
      while (true) {
        String s = reader.readLine();
        if (s == null)
          break;
        StringBuffer sb = new StringBuffer(s.length() + 80);
        for (int i = 0; i < s.length(); i++) {
          char c = s.charAt(i);
          if (c <= 0xff) {
            sb.append(c);
          } else if (((int) c) <= 0xffff) {
            sb.append("\\u");
            String hex = Integer.toHexString(c);
            for (int k = hex.length(); k < 4; k++)
              sb.append('0');
            sb.append(hex);
          } else {
            assert false : ((int) c);
          }
        }
        writer.println(sb.toString());
      }
    } finally {
      IOUtils.closeStreamIgnoreExceptions(fis);
      IOUtils.closeWriterIgnoreExceptions(writer);
    }
    byte[] bytes = out.toByteArray();
    return bytes;
  }

  public static boolean deleteDirectoryWithContents(File directory) throws InterruptedException {
    if (!directory.isDirectory())
      return false;
    String[] children = directory.list();
    if (children != null) {
      for (String child : children) {
        if (".".equals(child) || "..".equals(child))
          continue;
        File f = new File(directory, child);
        if (!directory.equals(f.getParentFile())) {
          // suspicious
          continue;
        }
        boolean success = f.isDirectory() ? deleteDirectoryWithContents(f) : deleteFile(f, false);
        if (!success)
          return false;
      }
    }
    return deleteFile(directory, false);
  }

  /**
   * Copies all files and directories from sourceDirectory to targetDirectory. Both directories must exist.
   */
  public static void copyAllRecursively(File sourceDirectory, File targetDirectory) throws IOException {
    File[] files = sourceDirectory.listFiles();
    if (files == null)
      return;
    for (File file : files) {
      String name = file.getName();
      if (".".equals(name) || "..".equals(name))
        continue;
      File targetFile = new File(targetDirectory, name);
      if (file.isFile()) {
        FileUtil.copyFile(file, targetFile, true);
      } else if (file.isDirectory()) {
        if (targetFile.exists()) {
          if (!targetFile.isDirectory()) {
            throw new IOException(targetFile + " exists but not a directory");
          }
        } else {
          targetFile.mkdir();
        }
        copyAllRecursively(file, targetFile);
      } else {
        // skipping file
      }
    }
  }

  public static String guessMimeType(String fileName) {
    if (fileName == null || fileName.length() == 0)
      return null;
    String guess = URLConnection.guessContentTypeFromName(fileName);
    if (guess == null || guess.length() == 0) {
      guess = URLConnection.guessContentTypeFromName(Util.lower(fileName));
    }
    return guess;
  }

  public static String getSizeString(long size) {
    if (size < 1)
      return null;
    if (size < Const.KILOBYTE) {
      return size + " bytes";
    }
    if (size < Const.MEGABYTE) {
      return (size >> 10) + " Kb";
    } else {
      long mb = size >> 20;
      long kb100 = (size % Const.MEGABYTE) / 10240;
      if (kb100 == 0)
        return mb + " Mb";
      else if (kb100 < 10)
        return mb + ".0" + kb100 + " Mb";
      else
        return mb + "." + kb100 + " Mb";
    }
  }

  public static int getMemoryMegs(long total) {
    int megs = (int) (total / 1048576);
    for (int round = 1024; round > 16; round = round >> 1) {
      if (Math.abs(round - megs) < 10) {
        megs = round;
        break;
      }
    }
    return megs;
  }

  /**
   * @return approxinate number of bytes for sizeString, or -1 if cannot parse
   */
  public static long getSizeFromString(String sizeString) {
    if (sizeString == null)
      return -1;
    sizeString = sizeString.trim();
    int k = sizeString.indexOf(' ');
    if (k <= 0) {
      try {
        return Long.parseLong(sizeString);
      } catch (NumberFormatException e) {
        Log.warn(e);
        return -1;
      }
    }
    String number = sizeString.substring(0, k);
    String measure = sizeString.substring(k + 1).trim();
    try {
      BigDecimal d = new BigDecimal(number);
      Integer multiplier = SIZE_MULTIPLIERS.get(Util.upper(measure));
      if (multiplier == null)
        return -1;
      return (long) (d.doubleValue() * multiplier);
    } catch (NumberFormatException e) {
      Log.warn(e);
      return -1;
    }
  }

  /**
   * Return extension (without dot) for a given name
   *
   * @param fileName given filename
   * @return extension in lowercase dotless form
   */

  public static String getExtensionLowercase(String fileName) {
    int i = Util.NN(fileName).lastIndexOf('.');
    if (i > 0 && i < fileName.length() - 1)
      return Util.lower(fileName.substring(i + 1));
    return "";
  }

  public static Pattern FORBIDDEN_CHARS_RE = Pattern.compile("[\\\\/:\"?*<|>]");
  public static String excludeForbddenChars(@NotNull String fileName) {
    return FORBIDDEN_CHARS_RE.matcher(fileName).replaceAll("");
  }

  public static boolean isEmpty(File file) {
    return file == null || file.getName().length() == 0;
  }
}
