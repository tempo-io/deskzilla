package com.almworks.api.tray;

import com.almworks.util.properties.Role;
import org.jetbrains.annotations.*;

import java.awt.*;

public interface TrayIconService {
  Role<TrayIconService> ROLE = Role.anonymous();

  void displayMessage(String caption, String text);

  void setTrayImage(@Nullable Image icon);
}
