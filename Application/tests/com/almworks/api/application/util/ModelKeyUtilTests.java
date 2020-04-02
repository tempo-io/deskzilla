package com.almworks.api.application.util;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.UIController;
import com.almworks.engine.gui.TextController;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.TODO;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.tests.GUITestCase;
import org.almworks.util.detach.Lifespan;
import util.external.BitSet2;

import javax.swing.*;

/**
 * @author dyoma
 */
public class ModelKeyUtilTests extends GUITestCase {
  protected void setUp() throws Exception {
    super.setUp();
    ModelKeySetUtil.cleanupForTest();
  }

  protected void tearDown() throws Exception {
    ModelKeySetUtil.cleanupForTest();
    super.tearDown();
  }

  public void test() {
    BaseKeyBuilder<String> builder = BaseKeyBuilder.create();
    builder.setName("keyName");
    builder.setDisplayName("displayName");
    builder.setComparator(String.CASE_INSENSITIVE_ORDER);
    builder.setIO(new MockDataIO());
    ModelKey<String> key = builder.getKey();
    PropertyMap values = new PropertyMap();
    BitSet2 addKeys = new BitSet2();
    ModelKeySetUtil.addKey(addKeys, key);
    ModelKeySetUtil.addKeysToMap(values, addKeys);
    key.setValue(values, "value");
    ItemUiModelImpl model = new ItemUiModelImpl(ItemWrapper.DBStatus.DB_NOT_CHANGED);
    model.setValues(values);
    JTextField field = new JTextField();
    TextController.installTextEditor(field, key, false);
    UIController.CONTROLLER.getClientValue(field).connectUI(Lifespan.FOREVER, model.getModelMap(), field);
    assertEquals("value", field.getText());
    key.setValue(values, "new");
    model.setValues(values);
    assertEquals("new", field.getText());
  }

  private class MockDataIO implements BaseModelKey.DataIO<String> {
    public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values, ModelKey<String> key) {
      throw TODO.notImplementedYet();
    }

    public void addChanges(UserChanges changes, ModelKey<String> key) {
      throw TODO.notImplementedYet();
    }

    public <SM>SM getModel(Lifespan life, ModelMap model, ModelKey<String> key, Class<SM> aClass) {
      throw TODO.notImplementedYet();
    }
  }
}
