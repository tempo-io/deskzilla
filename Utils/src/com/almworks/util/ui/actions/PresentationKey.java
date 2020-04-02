package com.almworks.util.ui.actions;

import com.almworks.util.properties.PropertyMap;
import com.almworks.util.properties.StringLoader;
import com.almworks.util.ui.UIUtil;
import org.almworks.util.*;

import javax.swing.*;
import java.util.Map;

/**
 * @author : Dyoma
 */
public abstract class PresentationKey <T> extends TypedKey<T> implements StringLoader<T> {
  private static final Map<String, PresentationKey> ALL = Collections15.hashMap();

  public static final String ACTION_KEY_TOGGLED_ON = "toggledOn";

  public static final String ACTION_KEY_NOT_AVALIABLE = "notAvaliable";

  public static final PresentationKey<Icon> SMALL_ICON = createIcon(Action.SMALL_ICON);

  /**
   * Visible name of an action. & indicates mnemonic, && converts to &. <br> &&a&b&&c will be converted to &ab&c with mnemonoc at b.
   */
  public static final PresentationKey<String> NAME = new PresentationKey<String>(Action.NAME) {

    public String restoreFromString(String string) {
      return string;
    }
  };
  public static final PresentationKey<String> SHORT_DESCRIPTION = createText(Action.SHORT_DESCRIPTION);
  public static final PresentationKey<EnableState> ENABLE = new PresentationKey<EnableState>("enable") {
    public EnableState restoreFromString(String string) {
      if ("ENABLED".equals(string))
        return EnableState.ENABLED;
      else if ("DISABLED".equals(string))
        return EnableState.DISABLED;
      else if ("INVISIBLE".equals(string))
        return EnableState.INVISIBLE;
      throw new Failure(string);
    }

  };
  public static final PresentationKey<KeyStroke> SHORTCUT = new SimplePresentationKey<KeyStroke>(Action.ACCELERATOR_KEY) {
    public KeyStroke restoreFromString(String string) {
      return KeyStroke.getKeyStroke(string);
    }
  };

  public static final PresentationKey<Boolean> TOGGLED_ON = createBoolean(ACTION_KEY_TOGGLED_ON);
  public static final PresentationKey<String> LONG_DESCRIPTION = createText(Action.LONG_DESCRIPTION);
  public static final PresentationKey<Boolean> NOT_AVALIABLE = createBoolean(ACTION_KEY_NOT_AVALIABLE);
  public static final String ACTION_KEY_VISIBLE = "visible";
  public static final String ACTION_KEY_ENABLE = "enable";

  public PresentationKey(String name) {
    super(name, null, null);
    ALL.put(name, this);
  }

  public static PresentationKey<Boolean> createBoolean(String name) {
    return new SimplePresentationKey<Boolean>(name) {
      public Boolean restoreFromString(String string) {
        return Boolean.valueOf(string);
      }
    };
  }

  public static PresentationKey<Icon> createIcon(String name) {
    return new SimplePresentationKey<Icon>(name) {
      public Icon restoreFromString(String resourceName) {
        // todo migrate to Icons class
        return UIUtil.getIcon(resourceName);
      }
    };
  }

  public static PresentationKey<String> createText(String name) {
    return new SimplePresentationKey<String>(name) {
      public String restoreFromString(String string) {
        return string;
      }
    };
  }

  public static PresentationKey<?> forName(String name) {
    return ALL.get(name);
  }

  public final void load(String rawData, PropertyMap properties) {
    properties.put(this, restoreFromString(rawData));
  }

  private static abstract class SimplePresentationKey<T> extends PresentationKey<T> {
    protected SimplePresentationKey(String name) {
      super(name);
    }

  }
}
