package com.almworks.universe.data;

import java.nio.ByteBuffer;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface BufferManager {
  ByteBuffer allocateBuffer(int capacity);
}
