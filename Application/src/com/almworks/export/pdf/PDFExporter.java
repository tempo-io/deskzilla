package com.almworks.export.pdf;

import com.almworks.api.gui.DialogManager;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.platform.ProductInformation;
import com.almworks.export.*;
import com.almworks.export.pdf.itext.PrintFormatRecord;
import com.almworks.util.config.Configuration;

import java.io.IOException;
import java.io.OutputStream;

public class PDFExporter extends FileExporterHelper {
  private final WorkArea myWorkArea;
  private final ProductInformation myProductInfo;

  protected PDFExporter(ExporterDescriptor descriptor, Configuration config, DialogManager dialogManager,
    WorkArea workArea, ProductInformation productInfo)
  {
    super(descriptor, new PDFParametersForm(config), dialogManager);
    myWorkArea = workArea;
    myProductInfo = productInfo;
  }

  protected void writeData(OutputStream out, ExportedData data, ExportParameters parameters) throws IOException {
    PrintFormatRecord pdfExprorter = new PrintFormatRecord(data, parameters, myWorkArea);
    pdfExprorter.writePdf(out, myProgress, myCancelled, myProductInfo);
  }
}
