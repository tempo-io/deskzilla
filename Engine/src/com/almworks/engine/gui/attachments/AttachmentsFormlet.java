package com.almworks.engine.gui.attachments;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.engine.Connection;
import com.almworks.engine.gui.*;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.SortedListDecorator;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.Highlightable;
import com.almworks.util.components.layout.WidthDrivenComponent;
import com.almworks.util.config.Configuration;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.regex.Pattern;

public class AttachmentsFormlet<T extends Attachment> extends AbstractFormlet implements Highlightable {
  private final ModelKey<? extends Collection<T>> myKey;
  private final AttachmentsPanel<T> myView;

  private List<ToolbarEntry> myToolbarActions;
  private List<ToolbarEntry> myAdditionalToolbarActions;

  private Comparator<? super T> myOrder;

  private int myLastAttachmentCount;
  private boolean myVisible;

  private boolean myInitialized;

  public AttachmentsFormlet(Configuration configuration, ModelKey<? extends Collection<T>> key) {
    super(configuration);
    myKey = key;
    myView = new AttachmentsPanel<T>(configuration);
  }

  public AttachmentsFormlet<T> setOrder(Comparator<? super T> order) {
    if (myInitialized) {
      assert false;
      return this;
    }
    myOrder = order;
    return this;
  }

  public AttachmentsFormlet<T> addProperties(AttachmentProperty<? super T, ?>... properties) {
    if (myInitialized) {
      assert false;
      return this;
    }
    for (AttachmentProperty<? super T, ?> property : properties) {
      myView.addProperty(property);
    }
    return this;
  }

  public AttachmentsFormlet<T> setLabelProperty(AttachmentProperty<? super T, ?> property) {
    if (myInitialized) {
      assert false;
      return this;
    }
    myView.setLabelProperty(property);
    return this;
  }

  public AttachmentsFormlet<T> setTooltipProvider(AttachmentTooltipProvider<T> provider) {
    if (myInitialized) {
      assert false;
      return this;
    }
    myView.setTooltipProvider(provider);
    return this;
  }

  public AttachmentsFormlet<T> addAction(AnAction action, boolean contextMenu, boolean toolbar) {
    if (myInitialized) {
      assert false;
      return this;
    }
    if (contextMenu) {
      myView.addAction(action);
    }
    if (toolbar) {
      if (myAdditionalToolbarActions == null)
        myAdditionalToolbarActions = Collections15.arrayList();
      myAdditionalToolbarActions.add(new ActionToolbarEntry(action));
    }
    return this;
  }

  private void initialize() {
    myView.initialize();
    UIController.CONTROLLER.putClientValue(myView.getComponent(), new MyPanelController());
    myToolbarActions = Collections15.arrayList(myView.createToolbar());
    if (myAdditionalToolbarActions != null) {
      myToolbarActions.addAll(myAdditionalToolbarActions);
    }
  }

  private void updateFormlet(Collection<T> attachments) {
    ensureInitialized();
    myVisible = attachments != null && attachments.size() > 0;
    myLastAttachmentCount = myVisible ? attachments.size() : 0;
    fireFormletChanged();
  }

  public String getCaption() {
    ensureInitialized();
    return isCollapsed() ? String.valueOf(myLastAttachmentCount) : null;
  }

  public List<? extends ToolbarEntry> getActions() {
    ensureInitialized();
    return isCollapsed() ? null : myToolbarActions;
  }

  @NotNull
  public WidthDrivenComponent getContent() {
    ensureInitialized();
    return myView;
  }

  public boolean isVisible() {
    return myVisible;
  }

  private void ensureInitialized() {
    if (myInitialized)
      return;
    initialize();
    myInitialized = true;
  }

  public void setHighlightPattern(Pattern pattern) {
    myView.setHighlightPattern(pattern);
  }

  private class MyPanelController implements UIController {
    public void connectUI(@NotNull Lifespan lifespan, @NotNull final ModelMap modelMap, @NotNull JComponent component) {
      ChangeListener listener = new ChangeListener() {
        public void onChange() {
          updateFormlet(myKey.getValue(modelMap));
        }
      };
      listener.onChange();
      modelMap.addAWTChangeListener(lifespan, listener);
      LoadedItemServices itemServices = LoadedItemServices.VALUE_KEY.getValue(modelMap);
      if (itemServices != null) {
        Connection connection = itemServices.getConnection();
        if (connection != null) {
          AListModel<T> model = myKey.getModel(lifespan, modelMap, AListModel.class);
          if (myOrder != null) {
            model = SortedListDecorator.create(lifespan, model, myOrder);
          }
          lifespan.add(myView.show(model, connection));
          DefaultUIController.ROOT.connectUI(lifespan, modelMap, component);
        }
      }
    }
  }
}
