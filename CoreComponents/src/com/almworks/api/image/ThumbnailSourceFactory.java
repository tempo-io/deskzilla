package com.almworks.api.image;

import org.jetbrains.annotations.*;

import java.awt.*;

public interface ThumbnailSourceFactory {
  @Nullable
  Image createSourceImage(String imageId);
}
