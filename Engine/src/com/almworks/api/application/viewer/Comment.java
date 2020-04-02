package com.almworks.api.application.viewer;

import org.jetbrains.annotations.*;

import java.util.Date;

/**
 * @author dyoma
*/
public interface Comment {
  String getText();

  String getWhenText();

  Date getWhen();

  String getWhoText();

  @Nullable
  String getHeaderTooltipHtml();
}
