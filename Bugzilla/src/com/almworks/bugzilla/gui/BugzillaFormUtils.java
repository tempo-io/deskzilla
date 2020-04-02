package com.almworks.bugzilla.gui;

import com.almworks.api.actions.StdItemActions;
import com.almworks.api.application.*;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.explorer.ReadOnlyTextController;
import com.almworks.api.explorer.gui.ItemModelKey;
import com.almworks.api.explorer.util.UserChooseController;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.bugzilla.gui.comments.DeleteCommentAction;
import com.almworks.bugzilla.gui.comments.EditCommentAction;
import com.almworks.bugzilla.integration.BugzillaHTMLConstants;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.BugzillaProvider;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.bugzilla.provider.meta.groups.BugGroupInfo;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.spi.provider.AbstractConnection;
import com.almworks.util.L;
import com.almworks.util.advmodel.AComboboxModel;
import com.almworks.util.collections.*;
import com.almworks.util.components.*;
import com.almworks.util.components.completion.CompletingComboBox;
import com.almworks.util.components.recent.CBRecentSynchronizer;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.files.ExternalBrowser;
import com.almworks.util.model.ValueModel;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.PresentationMapping;
import com.almworks.util.ui.swing.AwtUtil;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.*;

public class BugzillaFormUtils {
  static void setupReadOnlyFields(JPanel wholePanel, final boolean makeBold) {
    final Set<JTextField> skip = Collections15.hashSet();
    UIUtil.visitComponents(wholePanel, JTextField.class, new ElementVisitor<JTextField>() {
      public boolean visit(JTextField field) {
        if (!field.isEditable()) {
          ReadOnlyTextController.install(field);
          ReadOnlyTextFields.setupReadOnlyTextField(field);
          if (makeBold)
            UIUtil.adjustFont(field, -1, Font.BOLD, false);
          skip.add(field);
        }
        return true;
      }
    });
    if (skip.size() > 0) {
      wholePanel.setFocusCycleRoot(true);
      wholePanel.setFocusTraversalPolicy(new SkippingFocusTraversalPolicy(new LayoutFocusTraversalPolicy(), skip));
    }
  }

  public static JComponent createViewerToolbar() {
    // todo #825
    ToolbarBuilder builder = ToolbarBuilder.smallVisibleButtons();
    builder.setCommonPresentation(PresentationMapping.VISIBLE_NONAME);
    builder.addAction(StdItemActions.ADD_COMMENT);
    builder.addAction(StdItemActions.REPLY_TO_COMMENT);
    builder.addAction(EditCommentAction.EDIT_COMMENT);
    builder.addAction(DeleteCommentAction.INSTANCE);
    return builder.createHorizontalToolbar();
  }

  public static void openComment(String baseURL, Integer bugId, int commentIndex) {
    ExternalBrowser browser = new ExternalBrowser();
    browser.setUrl(baseURL + BugzillaHTMLConstants.URL_FRONT_PAGE + bugId + "#c" + commentIndex, false);
    browser.setDialogHandler(L.dialog("Open Browser"), L.content("Failed to open browser"));
    browser.openBrowser();
  }

  static UserChooseController<ItemKey> setupUserField(CompletingComboBox<ItemKey> combobox,
    ItemModelKey<ItemKey> key, BaseEnumConstraintDescriptor descriptor,
    Convertor<ModelMap, ItemHypercube> cube, @Nullable String noneUser)
  {
    combobox.setCasesensitive(false);
    combobox.setPrototypeDisplayValue(new ItemKeyStub("user@server.com", "Mrs. Name SecondName", ItemOrder.NO_ORDER));
    return UserChooseController.install(combobox, key, descriptor, cube, false)
      .setStringConvertors(ItemKey.DISPLAY_NAME, key.getResolver(), ItemKey.DISPLAY_NAME_EQUALITY)
      .setCanvasRenderer(User.createRenderer(noneUser));
  }

  static void setupGroupsPanel(JPanel panel, final ModelKey<BugGroupInfo> groups) {
    UIController<JPanel> controller = new UIController<JPanel>() {
      public void connectUI(@NotNull final Lifespan lifespan, @NotNull ModelMap model, @NotNull JPanel component) {
        if (lifespan.isEnded())
          return;
        new BugGroupsEditor(lifespan, model, component, groups).attach();
      }
    };
    UIController.CONTROLLER.putClientValue(panel, controller);
  }

  public static void setupDateField(ADateField field, final ModelKey<Date> modelKey) {
    UIController.CONTROLLER.putClientValue(field, new UIController<ADateField>() {
      public void connectUI(@NotNull Lifespan lifespan, @NotNull final ModelMap model, @NotNull ADateField component) {
        final ValueModel<Date> vm = new ValueModel<Date>();
        vm.setValue(modelKey.getValue(model));
        boolean[] updated = {false};
        model.addAWTChangeListener(lifespan, new JointChangeListener(updated) {
          protected void processChange() {
            vm.setValue(modelKey.getValue(model));
          }
        });
        vm.addAWTChangeListener(lifespan, new JointChangeListener(updated) {
          protected void processChange() {
            PropertyMap props = new PropertyMap();
            modelKey.setValue(props, vm.getValue());
            modelKey.copyValue(model, props);
          }
        });
        component.setDateModel(vm);
      }
    });
  }

  public static void setupArtifactComboBox(Lifespan life, AComboBox<ItemKey> combobox, AComboboxModel<ItemKey> model,
    Configuration config) {
    CBRecentSynchronizer.setupComboBox(life, combobox, model, config, ItemKey.GET_ID, DefaultUIController.ITEM_KEY_RENDERER);
  }

  public static void setupArtifactComboBox(Lifespan life, AComboBox<ItemKey> combobox, AComboboxModel<ItemKey> model, ModelMap map, String configKey) {
    BugzillaConnection connection = BugzillaConnection.getInstance(map);
    if (connection == null) {
      assert false;
      return;
    }
    Configuration config = connection.getConnectionConfig(AbstractConnection.RECENTS, configKey);
    setupArtifactComboBox(life, combobox, model, config);
  }

  static void setupKeywords(final ModelKey<? extends Collection<ItemKey>> key, FieldWithMoreButton field) {
    KeywordsController.setup(key, field);
  }

  public static List<String> convertSeeAlsoToDisplayable(ModelMap model, List<String> urls) {
    BugzillaConnection connection = BugzillaConnection.getInstance(model);
    if (connection == null || urls == null || urls.isEmpty()) return urls;
    List<String> result = Collections15.arrayList(urls.size());
    for (String url : urls) {
      Integer id = connection.getBugIdFromUrl(url);
      result.add(id != null ? String.valueOf(id) : url);
    }
    return result;
  }
  
  public static void setupSeeAlso(final ModelKey<List<String>> key, JTextField field) {
    UIController.CONTROLLER.putClientValue(field, new UIController<JTextField>() {
      public void connectUI(@NotNull Lifespan lifespan, @NotNull final ModelMap model,
        @NotNull final JTextField component)
      {
        final List<String> originalValue = key.getValue(model);
        boolean[] updateFlag = {false};
        final Document document = component.getDocument();
        JointChangeListener modelListener = new JointChangeListener(updateFlag) {
          @Override
          protected void processChange() {
            List<String> value = convertSeeAlsoToDisplayable(model, key.getValue(model));
            String s = value == null ? "" : TextUtil.separate(value, ", ");
            DocumentUtil.setDocumentText(document, s);
            component.setCaretPosition(0);
          }
        };
        model.addAWTChangeListener(lifespan, modelListener);
        modelListener.onChange();
        DocumentUtil.addChangeListener(lifespan, document, new JointChangeListener(updateFlag) {
          @Override
          protected void processChange() {
            String s = DocumentUtil.getDocumentText(document);
            List<String> urls = parseSeeAlsoValues(model, s);
            if (urls == null) {
              component.setForeground(GlobalColors.ERROR_COLOR);
              urls = originalValue;
            } else {
              component.setForeground(AwtUtil.getTextComponentForeground());
            }
            PropertyMap props = new PropertyMap();
            key.takeSnapshot(props, model);
            key.setValue(props, urls);
            key.copyValue(model, props);
            model.valueChanged(key);
          }
        });
      }
    });
  }

  private static List<String> parseSeeAlsoValues(ModelMap model, String s) {
    s = Util.NN(s).trim();
    if (s.length() == 0) return Collections.emptyList();
    List<String> ss = Collections15.arrayList(s.split("[\\s,]+"));
    BugzillaConnection connection = BugzillaConnection.getInstance(model);
    BugzillaProvider provider = Context.require(BugzillaProvider.class);
    for (int i = 0, ssSize = ss.size(); i < ssSize; i++) {
      String url = ss.get(i);
      if (provider.isItemUrl(url)) continue;
      if (connection != null) {
        try {
          int bugId = Integer.parseInt(url.trim());
          String bugUrl = connection.getBugUrlById(bugId);
          if (bugUrl != null) {
            ss.set(i, bugUrl);
            continue;
          }
        } catch (NumberFormatException e) {
          // Ignore
        }
      }
      String goodUrl = checkUrl(url);
      if (goodUrl != null) ss.set(i, goodUrl);
      else return null;
    }
    return ss;
  }

  @Nullable
  private static String checkUrl(String url) {
    URL urlObj = null;
    try {
      urlObj = new URL(url);
    } catch (MalformedURLException e) {
      if (!url.toLowerCase(Locale.US).startsWith("http:")) {
        try {
          urlObj = new URL("http://" + url);
        } catch (MalformedURLException e1) {
          // Ignore
        }
      }
    }
    return urlObj != null ? urlObj.toExternalForm() : null;
  }

  public static void addRecentSupport(AComboBox priority, AComboBox severity, AComboBox platform, AComboBox os,
    AComboBox status) {
    DefaultUIController.RECENT_CONFIG.putClientValue(priority, "priority");
    DefaultUIController.RECENT_CONFIG.putClientValue(severity, "severity");
    DefaultUIController.RECENT_CONFIG.putClientValue(platform, "platform");
    DefaultUIController.RECENT_CONFIG.putClientValue(os, "os");
    DefaultUIController.RECENT_CONFIG.putClientValue(status, "status");
  }
}
