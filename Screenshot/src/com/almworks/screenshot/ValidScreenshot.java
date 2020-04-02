package com.almworks.screenshot;

import com.almworks.api.screenshot.Screenshot;

import java.awt.image.BufferedImage;

public class ValidScreenshot implements Screenshot {
  private final BufferedImage myImage;
  private final String myDescription;

  public ValidScreenshot(BufferedImage image, String descr) {
    myImage = image;
    myDescription = descr;
  }

  public BufferedImage getImage() {
    return myImage;
  }

  public String getDescription() {
    return myDescription;
  }
}
