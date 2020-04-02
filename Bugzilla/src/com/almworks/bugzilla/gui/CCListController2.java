package com.almworks.bugzilla.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.explorer.gui.*;
import com.almworks.api.explorer.util.ItemKeys;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.spi.provider.AbstractConnection;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.JointChangeListener;
import com.almworks.util.components.*;
import com.almworks.util.config.Configuration;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.swing.AwtUtil;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public class CCListController2 implements UIController<CompactUserSubsetEditor<ItemKey>> {
  private final CompactUserSubsetEditor<ItemKey> myEditor;

  public static JComponent createEditor(JLabel label) {
    final CompactUserSubsetEditor<ItemKey> editor = new CompactUserSubsetEditor<ItemKey>() {
      @Override
      protected AScrollPane createScrollPane(AList<ItemKey> list) {
        return new BoundedScrollPane(list);
      }
    };
    editor.setVisibleRowCount(8);
    label.setLabelFor(editor.getJList());
    UIController.CONTROLLER.putClientValue(editor, new CCListController2(editor));
    return editor;
  }

  private CCListController2(CompactUserSubsetEditor<ItemKey> editor) {
    myEditor = editor;
  }

  @Override
  public void connectUI(
    @NotNull Lifespan lifespan, @NotNull final ModelMap model,
    @NotNull CompactUserSubsetEditor<ItemKey> component)
  {
    assert component == myEditor;
    component.setCanvasRenderer(User.RENDERER);
    component.setIdentityConvertor(ItemKey.GET_ID);

    final ItemModelKey<Collection<ItemKey>> key = BugzillaKeys.cc;
    installFullUsersModel(lifespan, model, key, component);
    setupChangeForwarding(lifespan, model, key, component);
    connectAddMeButton(lifespan, model, key, component);

    component.getSelectionAccessor().clearSelection();
  }

  private void installFullUsersModel(
    Lifespan lifespan, ModelMap model, ItemModelKey<Collection<ItemKey>> key,
    CompactUserSubsetEditor<ItemKey> component)
  {
    final AListModel<ItemKey> users = key.getModelVariants(model, lifespan);
    lifespan.add(component.setFullModel(users, getRecentsConfig(model, key)));
  }

  private Configuration getRecentsConfig(ModelMap model, ItemModelKey<Collection<ItemKey>> key) {
    LoadedItemServices lis = LoadedItemServices.VALUE_KEY.getValue(model);
    AbstractConnection conn = lis.getConnection(AbstractConnection.class);
    return conn == null ? null : conn.getConnectionConfig(AbstractConnection.RECENTS, key.getName());
  }

  private void setupChangeForwarding(
    Lifespan lifespan, final ModelMap model, final ItemModelKey<Collection<ItemKey>> key,
    CompactUserSubsetEditor<ItemKey> component)
  {
    final OrderListModel subset = key.getModel(lifespan, model, OrderListModel.class);
    if(subset == null) {
      return;
    }

    final SelectionAccessor<ItemKey> accessor = component.getSubsetAccessor();
    accessor.setSelected(subset.toList());

    final boolean[] flag = { false };
    subset.addAWTChangeListener(lifespan, new JointChangeListener(flag) {
      @Override
      protected void processChange() {
        accessor.setSelected(subset.toList());
      }
    });
    accessor.addAWTChangeListener(lifespan, new JointChangeListener(flag) {
      @Override
      protected void processChange() {
        ((ItemsListKey)key).replaceValues(model, accessor.getSelectedItems());
      }
    });
  }

  private void connectAddMeButton(
    Lifespan lifespan, final ModelMap model, final ItemModelKey<Collection<ItemKey>> key,
    final CompactUserSubsetEditor<ItemKey> component)
  {
    BugzillaContext context = BugzillaUtil.getContext(model);
    ScalarModel<OurConfiguration> configuration = context == null ? null : context.getConfiguration();
    if(configuration == null) {
      component.setMyself(null);
    } else {
      beginTrackingMyself(lifespan, component, configuration, key.getResolver());
    }
  }

  private void beginTrackingMyself(
    Lifespan lifespan, final CompactUserSubsetEditor<ItemKey> component,
    ScalarModel<OurConfiguration> configuration, final TextResolver resolver)
  {
    configuration.getEventSource().addAWTListener(
      lifespan,
      new ScalarModel.Adapter<OurConfiguration>() {
        public void onScalarChanged(ScalarModelEvent<OurConfiguration> event) {
          findAndInstallMyself(event.getNewValue(), resolver, component);
        }
      });
  }

  private void findAndInstallMyself(
    OurConfiguration config, TextResolver resolver,
    CompactUserSubsetEditor<ItemKey> component)
  {
    final String username = config.getUserFullEmail();
    if(username != null) {
      final ItemKey myself = resolver.getItemKey(username);
      component.setMyself(ItemKeys.findInModel(myself, component.getFullModel()));
    }
  }

  public void addChangeListener(Lifespan life, ChangeListener listener) {
    myEditor.getUserModifiable().addAWTChangeListener(life, listener);
  }

  private static class BoundedScrollPane extends AScrollPane {
    private final AList<?> myList;
    private final int myMinWidth;
    private final int myMaxWidth;
    private final int myPrefHeight;

    public BoundedScrollPane(AList<?> list) {
      super(list);
      myList = list;
      myMinWidth = UIUtil.getColumnWidth(list) * 10;
      myMaxWidth = UIUtil.getColumnWidth(list) * 20;
      myPrefHeight = UIUtil.getLineHeight(myList.getSwingComponent()) * 8;
      setAdaptiveVerticalScroll(true);
      setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }

    @Override
    public Dimension getPreferredSize() {
      int width =
        myList.getPreferredSize().width
          + AwtUtil.getInsetWidth(this)
          + AwtUtil.getInsetWidth(getViewport());

      width = Math.max(width, myMinWidth);
      width = Math.min(width, myMaxWidth);

      final JScrollBar scrollBar = getVerticalScrollBar();
      if(scrollBar.isVisible()) {
        width += scrollBar.getPreferredSize().width;
      }

      return new Dimension(width, myPrefHeight);
    }
  }
}
