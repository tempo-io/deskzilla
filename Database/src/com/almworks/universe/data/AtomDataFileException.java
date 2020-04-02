package com.almworks.universe.data;

import com.almworks.util.fileformats.FileFormatException;

/**
 * :todoc:
 *
 * @author sereda
 */
public class AtomDataFileException extends FileFormatException {
  public AtomDataFileException() {
    super();
  }

  public AtomDataFileException(String message) {
    super(message);
  }

  public AtomDataFileException(Throwable cause) {
    super(cause);
  }

  public AtomDataFileException(String message, Throwable cause) {
    super(message, cause);
  }
}
