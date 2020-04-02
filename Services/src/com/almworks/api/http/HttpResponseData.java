package com.almworks.api.http;

import com.almworks.util.io.StreamTransferTracker;
import com.almworks.util.io.StringTransferTracker;
import org.apache.commons.httpclient.URI;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public interface HttpResponseData {
  /**
   * @return file name suggested by server through Content-Type: ...; name= or Content-disposition: inline; filename=...
   * or null.<br/>
   * <b>NB!</b> The filename may be word-encoded as per RFC 2047 (e.g. <tt>"=?UTF-8?Q?file=20name?=.txt"</tt>), consider decoding it before use. Remember that Windows does not allow to create files with '?' in name.
   */
  @Nullable
  String getContentFilename();

  /**
   * @return MIME content type, with all additional info (e.g. ";charset=" stripped)
   */
  String getContentType();

  /**
   * @return full content type, as it was returned from the server - includes all additional info
   */
  String getFullContentType();

  /**
   * @return known content length (from headers), or negative value if content is not known
   */
  long getContentLength();

  long transferToStream(OutputStream output, @Nullable StreamTransferTracker tracker) throws IOException;

  String transferToString(StringTransferTracker tracker) throws IOException;

  byte[] transferToBytes(StreamTransferTracker transferTracker) throws IOException;

  @NotNull
  Map<String, String> getResponseHeaders();

  @Nullable
  URI getLastURI();
}
