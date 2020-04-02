package com.almworks.api.screenshot;

import com.almworks.util.commons.Procedure;
import com.almworks.util.properties.Role;

import java.awt.*;

public interface ScreenShooter {
  Role<ScreenShooter> ROLE = Role.role(ScreenShooter.class);

  void shoot(Component contextComponent, Procedure<Screenshot> result);

  void edit(Image image, Procedure<Screenshot> result);
}
