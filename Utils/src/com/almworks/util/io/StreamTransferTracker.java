package com.almworks.util.io;

import java.nio.ByteBuffer;

public interface StreamTransferTracker {
  /**
   * @param bytesTransferred total bytes that has been transferred
   * @param lastChunk byte buffer that has been used to transfer the last chunk. Its state is "after writing",
   * i.e. you need to reset it to read anything.
   */
  void onTransfer(long bytesTransferred, ByteBuffer lastChunk);
}
