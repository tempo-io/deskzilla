package com.almworks.export.html;

import com.almworks.api.gui.DialogManager;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.platform.ProductInformation;
import com.almworks.export.*;
import com.almworks.util.config.Configuration;
import org.jetbrains.annotations.*;

public class HTMLExporterDescriptor implements ExporterDescriptor {
  private final DialogManager myDialogManager;
  private final WorkArea myWorkArea;
  private final ProductInformation myProductInfo;

  public HTMLExporterDescriptor(DialogManager dialogManager, WorkArea workArea, ProductInformation productInfo) {
    myDialogManager = dialogManager;
    myWorkArea = workArea;
    myProductInfo = productInfo;
  }

  @NotNull
  public String getKey() {
    return "com.almworks.export.html";
  }

  @NotNull
  public String getDisplayableName() {
    return "HTML file";
  }

  @NotNull
  public Exporter createExporter(Configuration configuration, ExportParameters target, ExportedData data)
    throws ExporterNotApplicableException
  {
    return new HTMLExporter(this, myDialogManager, configuration, myWorkArea, myProductInfo);
  }
}
