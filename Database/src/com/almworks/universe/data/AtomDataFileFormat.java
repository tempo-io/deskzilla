package com.almworks.universe.data;

import com.almworks.util.fileformats.FileFormatException;
import com.almworks.util.io.BufferedDataInput;
import com.almworks.util.io.BufferedDataOutput;

import java.io.IOException;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface AtomDataFileFormat {
  void writeNewHeader(ADFMetaInfo metaInfo, BufferedDataOutput output) throws IOException;

  ADFMetaInfo readHeader(BufferedDataInput input) throws IOException, FileFormatException;

  void writeExpansion(ExpansionInfo expansionInfo, BufferedDataOutput output) throws IOException,
    AtomDataFileException;

  void skipPadding(BufferedDataInput input) throws IOException;

  ExpansionInfo readExpansion(BufferedDataInput input) throws IOException, FileFormatException;
}
