package com.almworks.api.explorer.gui;

import com.almworks.api.application.util.ValueKey;
import com.almworks.explorer.loader.DBThreadConstraints;
import com.almworks.explorer.loader.ModelMapImpl;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.collections.Containers;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.GUITestCase;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.DocumentAdapter;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.detach.DetachComposite;

import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;

/**
 * @author dyoma
 */
public class KeysRTests extends BaseTestCase {
  public static final DBAttribute<String> TEXT = DBAttribute.String("text", "text");
  public void testValueKeyDocument() {
    ValueKey<String> key = new ValueKey<String>(TEXT, Containers.<String>comparablesComparator(), false, "attrDispName");
    ModelMapImpl model = new ModelMapImpl();
    PropertyMap values = new PropertyMap();
    key.setValue(values, "1");
    key.copyValue(model, values);

    DetachComposite lifespan = new DetachComposite();
    Document document = key.getModel(lifespan, model, Document.class);
    assertEquals("1", DocumentUtil.getDocumentText(document));
    key.setValue(values, "2");
    final boolean[] changed = new boolean[]{false};
    document.addDocumentListener(new DocumentAdapter() {
      protected void documentChanged(DocumentEvent e) {
        Threads.assertAWTThread();
        changed[0] = true;
      }
    });
    key.copyValue(model, values);
    GUITestCase.flushAWTQueue();
    assertTrue(changed[0]);
    assertEquals("2", DocumentUtil.getDocumentText(document));

    changed[0] = false;
    key.setValue(values, "3");
    GUITestCase.flushAWTQueue();
    lifespan.detach();
    key.copyValue(model, values);
    assertFalse(changed[0]);
    assertEquals("2", DocumentUtil.getDocumentText(document));
  }

  static {
    DBThreadConstraints.register();
  }
}
