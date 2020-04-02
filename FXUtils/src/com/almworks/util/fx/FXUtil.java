package com.almworks.util.fx;

import com.almworks.util.LogHelper;
import javafx.collections.ObservableList;
import javafx.fxml.*;
import javafx.scene.Parent;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;

public class FXUtil {
  public static void loadFxml(Object controller, String fxmlName, @Nullable String resourceName) {
    try {
      loadFxmlWithException(controller, fxmlName, resourceName);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void loadFxmlWithException(Object controller, String fxmlName, @Nullable String resourceName) throws IOException {
    Class<?> clazz = controller.getClass();
    URL fxmlUrl = clazz.getResource(fxmlName);
    ResourceBundle i18n = null;
    try {
      String path = Util.getClassPath(clazz);
      ClassLoader classLoader = clazz.getClassLoader();
      if (resourceName != null) i18n = ResourceBundle.getBundle(path + resourceName, Locale.getDefault(), classLoader);
      FXMLLoader loader = new FXMLLoader(fxmlUrl, i18n, new JavaFXBuilderFactory(classLoader));
      loader.setClassLoader(classLoader);
      loader.setController(controller);
      loader.load();
      fixCssStylesheetsReferences(controller);
    } catch (IOException | RuntimeException e) {
      LogHelper.warning("Failed to load FXML:", fxmlName, resourceName, fxmlUrl, i18n, controller);
      throw e;
    }
  }

  private static void fixCssStylesheetsReferences(Object controller) {
    if (controller == null) return;
    List<Field> fields = new ArrayList<>();
    Class<?> clazz = controller.getClass();
    while (clazz != null) {
      fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
      clazz = clazz.getSuperclass();
    }
    for (Field field : fields) {
      if (field.getAnnotation(FXML.class) == null) return;
      field.setAccessible(true);
      Object val;
      try {
        val = field.get(controller);
      } catch (IllegalAccessException e) {
        continue;
      }
      Parent parent = Util.castNullable(Parent.class, val);
      if (parent == null) continue;
      for (int i = 0; i < parent.getStylesheets().size(); i++) {
        ObservableList<String> stylesheets = parent.getStylesheets();
        String cssRef = stylesheets.get(i);
        stylesheets.set(i, fixCssReference(cssRef));
      }
    }
  }

  public static String loadCssRef(Object controller, String path) {
    if (controller == null) return null;
    URL uri = controller.getClass().getResource(path);
    if (uri == null) return null;
    return fixCssReference(uri.toExternalForm());
  }

  /**
   * Use this method to add stylesheets to {@link Parent#getStylesheets()}.
   * Replaces whitespace character with "%20". Otherwise the stylesheet reference won't loaded.<br>
   * See: http://stackoverflow.com/questions/32757086/load-javafx-css-with-whitespaces-within-path
   * @param cssUri css URI (resource or file)
   * @return fixed css URI (with whitespace characters)
   *
   */
  private static String fixCssReference(String cssUri) {
    if (cssUri == null) return null;
    return cssUri.replaceAll(" ", "%20").replace('\\', '/');
  }
}
