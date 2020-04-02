package com.almworks.util.io;

import org.almworks.util.*;
import org.jetbrains.annotations.*;
import util.external.CompactInt;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class IOUtils {
  private static final String INVALID_CHARS_REPLACEMENT = "?";
  static final int BLOCK_SIZE = 8192;
  public static final String DEFAULT_CHARSET = "ISO-8859-1";
  public static String[] COMMON_CHARSET_NAMES = {"UTF-8", "ISO-8859-1"};

  public static Set<Integer> readIntegerSet(DataInput input) throws IOException {
    int count = CompactInt.readInt(input);
    if (count == -1)
      return null;
    assert count >= 0;
    Set<Integer> result = Collections15.hashSet();
    for (int i = 0; i < count; i++)
      result.add(CompactInt.readInt(input));
    return result;
  }

  public static void writeIntegerSet(DataOutput output, Set<Integer> set) throws IOException {
    if (set == null) {
      CompactInt.writeInt(output, -1);
      return;
    }
    CompactInt.writeInt(output, set.size());
    for (Iterator<Integer> iterator = set.iterator(); iterator.hasNext();)
      CompactInt.writeInt(output, iterator.next().intValue());
  }

  public static long transfer(InputStream inputStream, OutputStream outputStream) throws IOException {
    return transfer(inputStream, outputStream, null);
  }

  public static long transfer(InputStream inputStream, OutputStream outputStream, @Nullable StreamTransferTracker tracker)
    throws IOException
  {
    ReadableByteChannel input = Channels.newChannel(inputStream);
    WritableByteChannel output = Channels.newChannel(outputStream);
    ByteBuffer buffer = ByteBuffer.allocateDirect(BLOCK_SIZE);
    long total = 0;
    while (true) {
      int read = readWorkaround(input, buffer);
      if (read < 0)
        break;
      buffer.flip();
      output.write(buffer);
      total += read;
      if (tracker != null)
        tracker.onTransfer(total, buffer);
      buffer.clear();
    }
    input.close();
    output.close();
    return total;
  }

  public static String transferToString(InputStream inputStream) throws IOException {
    try {
      return transferToString(inputStream, DEFAULT_CHARSET, null, null);
    } catch (UnsupportedEncodingException e) {
      throw new Failure(e);
    }
  }

  public static String transferToString(InputStream inputStream, String charset)
    throws IOException, UnsupportedEncodingException
  {
    return transferToString(inputStream, charset, null, null);
  }

  public static String transferToString(InputStream inputStream, String charset, CharValidator validator)
    throws IOException, UnsupportedEncodingException
  {
    return transferToString(inputStream, charset, validator, null);
  }

  public static String transferToString(InputStream inputStream, String charset, CharValidator validator,
    StringTransferTracker tracker) throws IOException, UnsupportedEncodingException
  {
    assert Charset.isSupported(charset);
    StringBuilder result = new StringBuilder();
    ReadableByteChannel input = Channels.newChannel(inputStream);
    ByteBuffer bytes = ByteBuffer.allocateDirect(BLOCK_SIZE);
    CharBuffer chars = CharBuffer.allocate(BLOCK_SIZE);
    CharsetDecoder decoder;
    try {
      decoder = Charset.forName(charset).newDecoder();
    } catch (IllegalArgumentException e) {
      Log.warn("charset " + charset, e);
      throw new UnsupportedEncodingException(charset);
    }
    decoder.onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
    decoder.replaceWith(INVALID_CHARS_REPLACEMENT);

    int totalRead = 0;
    while (true) {
      int read = readWorkaround(input, bytes);
      if (read < 0)
        break;
      totalRead += read;
      bytes.flip();
      while (true) {
        CoderResult coderResult = decoder.decode(bytes, chars, false);
        if (!processTransferAnalyzeCoderResult(coderResult, result, chars, validator))
          break;
      }
      bytes.compact();
      if (tracker != null)
        tracker.onTransfer(result);
    }
    // flush decoder
    if (totalRead > 0) {
      while (true) {
        CoderResult coderResult;
        bytes.flip();
        coderResult = decoder.decode(bytes, chars, true);
        processTransferAnalyzeCoderResult(coderResult, result, chars, validator);
        coderResult = decoder.flush(chars);
        if (!processTransferAnalyzeCoderResult(coderResult, result, chars, validator))
          break;
      }
    }
    if (chars.position() > 0)
      processTransferAddToBuffer(result, chars, validator);

    input.close();
    return result.substring(0, result.length());
  }

  private static int readWorkaround(ReadableByteChannel input, ByteBuffer bytes) throws IOException {
    try {
      return input.read(bytes);
    } catch (NullPointerException e) {
      // todo better
      /* NPE may be caught if we concurrently close connection and input stream.
          java.lang.NullPointerException
                  at java.lang.System.arraycopy(Native Method)
                  at java.io.BufferedInputStream.read1(BufferedInputStream.java:227)
                  at java.io.BufferedInputStream.read(BufferedInputStream.java:277)
                  at org.apache.commons.httpclient.WireLogInputStream.read(WireLogInputStream.java:68)
                  at org.apache.commons.httpclient.ChunkedInputStream.read(ChunkedInputStream.java:181)
                  at java.io.FilterInputStream.read(FilterInputStream.java:111)
                  at org.apache.commons.httpclient.AutoCloseInputStream.read(AutoCloseInputStream.java:107)
                  at java.nio.channels.Channels$ReadableByteChannelImpl.read(Channels.java:196)
                  at com.almworks.util.io.IOUtils.transferToString(IOUtils.java:108)
      */
      Log.debug(e);
      throw new IOException("read cancelled [NPE]");
    } catch (IllegalStateException e) {
/*
      // same as NPE problem:
      java.lang.IllegalStateException: Connection is not open
      at org.apache.commons.httpclient.HttpConnection.assertOpen(HttpConnection.java:1269)
      at org.apache.commons.httpclient.HttpConnection.isResponseAvailable(HttpConnection.java:872)
      at org.apache.commons.httpclient.HttpMethodBase.responseBodyConsumed(HttpMethodBase.java:2264)
      at org.apache.commons.httpclient.HttpMethodBase$1.responseConsumed(HttpMethodBase.java:1747)
      at org.apache.commons.httpclient.AutoCloseInputStream.notifyWatcher(AutoCloseInputStream.java:180)
      at org.apache.commons.httpclient.AutoCloseInputStream.checkClose(AutoCloseInputStream.java:152)
      at org.apache.commons.httpclient.AutoCloseInputStream.read(AutoCloseInputStream.java:108)
      at java.nio.channels.Channels$ReadableByteChannelImpl.read(Channels.java:196)
      at com.almworks.util.io.IOUtils.readWorkaround(SourceFile:146)
*/
      Log.debug(e);
      throw new IOException("read cancelled [ISE]");
    }
  }

  private static void processTransferAddToBuffer(StringBuilder result, CharBuffer chars, CharValidator validator) {
    if (validator == null || validator == CharValidator.ALL_VALID) {
      result.append(chars.array(), 0, chars.position());
      return;
    }
    int n = chars.position();
    char[] array = chars.array();
    for (int i = 0; i < n; i++) {
      char c = array[i];
      if (validator.isValid(c))
        result.append(c);
      else
        result.append(INVALID_CHARS_REPLACEMENT);
    }
  }

  private static boolean processTransferAnalyzeCoderResult(CoderResult coderResult, StringBuilder buffer,
    CharBuffer chars, CharValidator validator)
  {

    if (coderResult.isUnderflow()) {
      return false;
    }
    if (coderResult.isOverflow()) {
      processTransferAddToBuffer(buffer, chars, validator);
      chars.clear();
      return true;
    }
    assert false : coderResult;
    return false;
  }

  public static void closeStreamIgnoreExceptions(OutputStream stream) {
    if (stream != null)
      try {
        stream.close();
      } catch (IOException e) {
        // ignore
      } catch (Exception e) {
        Log.error(e);
      }
  }

  public static void closeStreamIgnoreExceptions(InputStream stream) {
    if (stream != null)
      try {
        stream.close();
      } catch (IOException e) {
        // ignore
      } catch (Exception e) {
        Log.error(e);
      }
  }

  public static void closeReaderIgnoreExceptions(Reader reader) {
    if (reader != null)
      try {
        reader.close();
      } catch (IOException e) {
        // ignore
      } catch (Exception e) {
        Log.error(e);
      }
  }

  public static void closeWriterIgnoreExceptions(Writer writer) {
    if (writer != null)
      try {
        writer.close();
      } catch (IOException e) {
        // ignore
      } catch (Exception e) {
        Log.error(e);
      }
  }

  public static String getDefaultCharsetName() {
    return System.getProperty("file.encoding", "ISO-8859-1");
  }

  public static String[] getAvalaibleCharsetNames() {
    Collection<String> names = Charset.availableCharsets().keySet();
    String[] values = names.toArray(new String[names.size()]);
    Arrays.sort(values, new Comparator<String>() {
      String[] top = {getDefaultCharsetName(), "UTF-8", "ISO-8859-1"};

      public int compare(String s1, String s2) {
        if (s1.equalsIgnoreCase(s2))
          return 0;
        for (String aTop : top) {
          if (s1.equalsIgnoreCase(aTop))
            return -1;
          if (s2.equalsIgnoreCase(aTop))
            return 1;
        }
        return s1.compareToIgnoreCase(s2);
      }
    });
    return values;
  }

  public static String md5sum(String data) throws NoSuchAlgorithmException, UnsupportedEncodingException {
    byte[] dataBytes = data.getBytes("UTF-8");
    return md5sum(dataBytes);
  }

  public static String md5sum(byte[] dataBytes) throws NoSuchAlgorithmException {
    MessageDigest md5 = MessageDigest.getInstance("MD5");
    byte[] digest = md5.digest(dataBytes);
    StringBuilder b = new StringBuilder();
    for (byte bt : digest) {
      String s = Integer.toHexString(((int) bt) & 0xFF);
      if (s.length() == 1) {
        b.append('0');
      } else {
        assert s.length() == 2 : s;
      }
      b.append(s);
    }
    return b.toString();
  }

  public static String readAll(Reader reader) throws IOException {
    StringBuilder builder = new StringBuilder();
    int read = 0;
    char[] chars = new char[128];
    while ((read = reader.read(chars)) >= 0) 
      builder.append(chars, 0, read);
    return builder.toString();
  }
}
