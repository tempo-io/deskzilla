package com.almworks.platform;

import com.almworks.api.gui.BuildNumber;
import com.almworks.api.platform.ProductDistributionType;
import com.almworks.api.platform.ProductInformation;

import java.util.Date;

public class MockProductInformation implements ProductInformation {
  private final BuildNumber myBuildNumber;
  private final String myProductVersionType;
  private final Date myBuildDate = new Date();

  public MockProductInformation(String build, String versionType) {
    myBuildNumber = new BuildNumber(build);
    myProductVersionType = versionType;
  }

  public Date getBuildDate() {
    return myBuildDate;
  }

  public BuildNumber getBuildNumber() {
    return myBuildNumber;
  }

  public String getName() {
    throw new UnsupportedOperationException();
  }

  public String getVersion() {
    throw new UnsupportedOperationException();
  }

  public String getVersionType() {
    return myProductVersionType;
  }

  public ProductDistributionType getDistributionType() {
    return ProductDistributionType.FULL;
  }
}
