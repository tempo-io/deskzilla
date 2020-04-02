package com.almworks.api.database;

import com.almworks.util.collections.Convertor;
import com.almworks.util.config.ReadonlyConfiguration;
import org.almworks.util.TypedKey;

import javax.swing.*;

@Deprecated
public interface ArtifactAspects {
  /**
   * @deprecated
   */
  TypedKey<ReadonlyConfiguration> ASPECT_DEFAULT_COLUMNS_CONFIG = TypedKey.create("ASPECT_DEFAULT_COLUMNS_CONFIG");

  TypedKey<HostedString> ASPECT_STRING_REPRESENTATION = TypedKey.create("ASPECT_STRING_REPRESENTATION");

  TypedKey<Icon> ASPECT_ICON_REPRESENTATION = TypedKey.create("ASPECT_ICON_REPRESENTATION");

  TypedKey<String> ASPECT_UNIQUE_KEY = TypedKey.create("ASPECT_UNIQUE_KEY");

  Convertor<Aspected, String> STRING_REPRESENTATION = new AspectConvertor(ASPECT_STRING_REPRESENTATION);

  public static class AspectConvertor extends Convertor<Aspected, String> {
    private final TypedKey<HostedString> myAspect;

    public AspectConvertor(TypedKey<HostedString> aspect) {
      myAspect = aspect;
    }

    public String convert(Aspected value) {
      if (value == null)
        return null;
      HostedString hostedString = value.getAspect(myAspect);
      return hostedString == null ? null : hostedString.getFullString();
    }
  }
}