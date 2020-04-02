package com.almworks.api.application;

import com.almworks.api.misc.FileCollectionBasedIcon;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.PresentationMapping;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.File;

public class TagsUtil {
  public static final String NO_ICON = ":none:";
  public static final String TAG_ICONS_COLLECTION = "tagicons";
  public static final String FAVORITES_ICONPATH = ":favorites:";
  public static final String UNREAD_ICONPATH = ":unread:";

  @Nullable
  public static Icon getTagIcon(String iconPath, boolean fixSize) {
    if (iconPath == null || "".equals(iconPath)) {
      return Icons.TAG_DEFAULT;
    } else if (FAVORITES_ICONPATH.equals(iconPath)) {
      return Icons.TAG_FAVORITES;
    } else if (UNREAD_ICONPATH.equals(iconPath)) {
      return Icons.TAG_UNREAD;
    } else if (NO_ICON.equals(iconPath) || isPathInvalid(iconPath)) {
      return PresentationMapping.EMPTY_ICON;
    } else {
      return new FileCollectionBasedIcon(TAG_ICONS_COLLECTION, iconPath, fixSize);
    }
  }

  public static boolean isPathInvalid(String iconPath) {
    return iconPath.indexOf(File.separatorChar) >= 0;
  }
}
