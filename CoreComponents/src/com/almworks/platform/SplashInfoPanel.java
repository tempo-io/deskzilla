package com.almworks.platform;

import com.almworks.api.platform.ProductInformation;

class SplashInfoPanel {
  private final ProductInformation myProductInfo;

  public SplashInfoPanel(ProductInformation productInformation) {
    assert productInformation != null;
    myProductInfo = productInformation;
  }

  String getVersionText() {
    StringBuilder builder = new StringBuilder();
    builder
      .append(myProductInfo.getName())
      .append(" ")
      .append(myProductInfo.getVersion());
    return builder.toString();
  }
}
