package com.almworks.bugzilla.gui.flags;

import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.UIController;
import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.engine.gui.AbstractFormlet;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.Highlightable;
import com.almworks.util.components.layout.WidthDrivenComponent;
import com.almworks.util.config.Configuration;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class BugFlagsFormlet extends AbstractFormlet {
  private final Configuration myConfig;
  private boolean myVisible;
  private BugFlagsView myView = new BugFlagsView();
  @Nullable
  private String myCollapsedCaption;
  private boolean myInitialized;

  public BugFlagsFormlet(Configuration config) {
    super(config, true);
    myConfig = config;
  }

  private void init() {
    myView.getComponent().putClientProperty(UIController.CONTROLLER, new BugFlagsController());
    myView.init(myConfig);
  }

  private void ensureInitialized() {
    if (myInitialized)
      return;
    init();
    myInitialized = true;
  }

  @Override
  public boolean isVisible() {
    ensureInitialized();
    return myVisible;
  }

  @NotNull
  @Override
  public WidthDrivenComponent getContent() {
    ensureInitialized();
    return myView;
  }

  @Override
  public String getCaption() {
    ensureInitialized();
    return isCollapsed() ? myCollapsedCaption : null;
  }

  public Highlightable getHighlightable() {
    return myView;
  }

  private void updateFormletState(ModelMap model) {
    List<FlagVersion> flags = FlagsModelKey.getAllFlags(model, false);
    if (flags.isEmpty()) {
      myVisible = false;
    } else {
      myVisible = true;
      Collections.sort(flags, Flag.ORDER);
      // FOREVER is ok since detach is used only to clear rows
      myView.setRows(Lifespan.FOREVER, flags);
      myCollapsedCaption = FlagVersion.getSummaryString(flags);
    }
    fireFormletChanged();
  }

  private class BugFlagsController implements UIController {
    @Override
    public void connectUI(final @NotNull Lifespan life, @NotNull final ModelMap model, @NotNull JComponent component) {
      ChangeListener modelUpdate = new ChangeListener() {
        @Override
        public void onChange() {
          updateFormletState(model);
        }
      };
      model.addChangeListener(life, modelUpdate);
      modelUpdate.onChange();
    }
  }
}
