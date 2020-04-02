package com.almworks.util.io;

public interface StringTransferTracker {
  /**
   * Is called when some part of transfer is made. IMPORTANT: DO NOT MODIFY StringBuffer, or you will corrupt the result.
   * If false is returned, then transfer is aborted.
   */
  void onTransfer(StringBuilder buffer);

  /**
   * This method <b>MAY</b> be called to indicate the expected total bytes in the transfer. In some cases
   * where content length is unavailable, this method will not be called.
   */
  void setContentLengthHint(long length);
}
