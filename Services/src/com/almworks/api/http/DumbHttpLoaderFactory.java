package com.almworks.api.http;

import com.almworks.util.commons.Condition;
import com.almworks.util.io.*;
import org.almworks.util.Collections15;
import org.almworks.util.Failure;
import org.apache.commons.httpclient.URI;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.URL;
import java.util.Map;

public class DumbHttpLoaderFactory implements HttpLoaderFactory {
  public HttpLoader createLoader(HttpMaterial httpMaterial, HttpMethodFactory methodFactory, final String escapedUrl) {
    return new HttpLoader() {
      public void setMaximumRedirects(int maximumRedirects) {
      }

      public void setRetries(int retries) {
      }

      public HttpMaterial getMaterial() {
        return null;
      }

      public void setRedirectMethodFactory(HttpMethodFactory factory) {
      }

      public void setFailedStatusApprover(Condition<Integer> failedStatusCodeApprover) {
      }

      public void setReportAcceptor(HttpReportAcceptor reportAcceptor) {
      }

      public void addRedirectUriHandler(RedirectURIHandler handler) {
      }

      public HttpResponseData load() {
        return new HttpResponseData() {
          private InputStream myStream;

          public long transferToStream(OutputStream output, StreamTransferTracker tracker) throws IOException {
            return IOUtils.transfer(getContentStream(), output, tracker);
          }

          public String transferToString(StringTransferTracker tracker) throws IOException {
            return IOUtils.transferToString(getContentStream(), IOUtils.DEFAULT_CHARSET, null, tracker);
          }

          public byte[] transferToBytes(StreamTransferTracker transferTracker)
            throws IOException
          {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            transferToStream(stream, transferTracker);
            return stream.toByteArray();
          }

          public InputStream getContentStream() {
            try {
              if (myStream == null)
                myStream = new URL(escapedUrl).openStream();
              return myStream;
            } catch (IOException e) {
              throw new Failure(e);
            }
          }

          public String getContentType() {
            return null;
          }

          public String getFullContentType() {
            return null;
          }

          public String getContentFilename() {
            return null;
          }

          public long getContentLength() {
            return -1;
          }

          @Nullable
          public URI getLastURI() {
            return null;
          }

          public Map<String, String> getResponseHeaders() {
            return Collections15.emptyMap();
          }
        };
      }
    };
  }
}
