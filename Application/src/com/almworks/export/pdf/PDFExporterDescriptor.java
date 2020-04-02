package com.almworks.export.pdf;

import com.almworks.api.gui.DialogManager;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.platform.ProductInformation;
import com.almworks.export.*;
import com.almworks.util.config.Configuration;
import org.jetbrains.annotations.*;

public class PDFExporterDescriptor implements ExporterDescriptor {
  private final DialogManager myDialogManager;
  private final ProductInformation myProductInfo;
  private final WorkArea myWorkArea;

  public PDFExporterDescriptor(DialogManager dialogManager, ProductInformation productInfo, WorkArea wa) {
    myDialogManager = dialogManager;
    myProductInfo = productInfo;
    myWorkArea = wa;
  }

  @NotNull
  public String getKey() {
    return "com.almworks.export.pdf";
  }

  @NotNull
  public String getDisplayableName() {
    return "PDF file";
  }

  @NotNull
  public Exporter createExporter(Configuration configuration, ExportParameters target, ExportedData data)
    throws ExporterNotApplicableException
  {
    return new PDFExporter(this, configuration, myDialogManager, myWorkArea, myProductInfo);
  }
}
