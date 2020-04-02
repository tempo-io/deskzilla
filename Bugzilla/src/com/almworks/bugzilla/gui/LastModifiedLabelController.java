package com.almworks.bugzilla.gui;

import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.UIController;
import com.almworks.bugzilla.BugzillaBook;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.i18n.LText1;
import com.almworks.util.text.RODocument;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Date;

class LastModifiedLabelController implements UIController<JTextField> {
  private static final String X = "Bugzilla.BugViewer.Header.";
  private static final LText1<Date> LAST_MODIFIED_TOOLTIP = BugzillaBook.text(X + "last-modified-tooltip",
    "Last modified on {0,date,long} at {0,time,long}", new Date(0));

  public void connectUI(@NotNull Lifespan lifespan, @NotNull final ModelMap model, @NotNull final JTextField component) {
    ChangeListener listener = new ChangeListener() {
      public void onChange() {
        Date value = BugzillaKeys.modificationTime.getValue(model);
        component.setVisible(value != null);
        if (value == null)
          return;
        String dateText = DateUtil.toLocalDateOrTime(value);
        RODocument.setComponentText(component, dateText);
        component.setToolTipText(LAST_MODIFIED_TOOLTIP.format(value));
      }
    };
    model.addAWTChangeListener(lifespan, listener);
    listener.onChange();
  }
}
