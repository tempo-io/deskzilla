package com.almworks.spi.provider;

import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.*;
import com.almworks.items.api.*;
import com.almworks.util.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.components.*;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.i18n.LText2;
import com.almworks.util.i18n.Local;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.ui.*;
import com.almworks.util.ui.swing.AwtUtil;
import org.almworks.util.StringUtil;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Collection;

public abstract class DefaultInformationPanel implements UIComponentWrapper {
  private static final String PREFIX = "InformationPanel.";
  private static final LText2<Long, Long> REPORT = AppBook.text(PREFIX + "REPORT",
    "<html><body>{0,number,######} total<br>" +
    "{1,number,######} modified", 0L, 0L);

  private final DetachComposite myDetach = new DetachComposite();

  private final JScrollPane myWholePanel = new JScrollPane() {
    public void addNotify() {
      super.addNotify();
      attach();
    }
  };

  protected Form myForm;
  protected final ConnectionContext myContext;

  public DefaultInformationPanel(ConnectionContext context) {
    myContext = context;
  }

  protected void setupForm() {
    myForm = new Form();
    JPanel p = myForm.myFormPanel;
    p.setAlignmentX(0F);
    p.setAlignmentY(0F);
    p.setBorder(new EmptyBorder(19, 19, 19, 19));
    ScrollablePanel scrollable = new ScrollablePanel(p);
    new DocumentFormAugmentor().augmentForm(myDetach, scrollable, true);
    myWholePanel.setViewportView(scrollable);
    Aqua.setLightNorthBorder(myWholePanel);
    Aero.cleanScrollPaneBorder(myWholePanel);
  }

  protected void attach() {
    myForm.calculateStats();
    final Modifiable modifiable = getConnectionModifiable(myDetach);
    modifiable.addAWTChangeListener(myDetach, new ChangeListener() {
      @Override
      public void onChange() {
        myForm.updateInfo(getConnectionInfo());
      }
    });
     myForm.updateInfo(getConnectionInfo());
  }

  public void dispose() {
    myDetach.detach();
  }

  public JComponent getComponent() {
    return myWholePanel;
  }

  protected abstract Modifiable getConnectionModifiable(Lifespan life);

  protected abstract ConnectionInfo getConnectionInfo();

  protected static UIComponentWrapper getLazyWrapper(
    final ComponentContainer container,
    final Class<? extends DefaultInformationPanel> clazz)
  {
    return new LazyWrapper() {
      protected UIComponentWrapper initialize() {
        return container.instantiate(clazz);
      }
    };
  }

  class Form {
    private JLabel myStateValue;
    private JPanel myFormPanel;
    private JLabel myBugsLabel;
    private JLabel myProductsLabel;
    private JLabel myLoginLabel;
    private ALabel myNameLabel;
    private URLLink myUrlLink;
    private JLabel myLoginValue;
    private ALabel myBugsValue;
    private ALabel myProductsValue;
    private JLabel myStatusValue;

    public Form() {
      setupFonts();
      setupAlignments();
      listenConnection();
    }

    public void updateInfo(ConnectionInfo info) {
      myNameLabel.setText(info.connectionName);
      myUrlLink.setUrl(info.connectionUrl, false);
      myStatusValue.setText(info.status);
      myLoginLabel.setText(info.loginName);
      myLoginValue.setText(info.loginValue);
      myProductsLabel.setText(info.productsName);
      myProductsValue.setText("<html>" + StringUtil.implode(info.productsValue, "<br>"));
      myBugsLabel.setText(Local.text(Terms.key_Artifacts) + ":");
    }

    public void calculateStats() {
      final Connection connection = myContext.getConnection();
      final ConnectionViews views = connection.getViews();
      final DBFilter total = views.getConnectionItems();
      final DBFilter changed = views.getOutbox();

      final DBLiveQuery.Listener updater = new DBLiveQuery.Listener() {
        @Override
        public void onICNPassed(long icn) {
        }

        @Override
        public void onDatabaseChanged(DBEvent event, DBReader reader) {
          final long totalCount = total.query(reader).count();
          final long changedCount = changed.query(reader).count();
          ThreadGate.AWT.execute(new Runnable() {
            @Override
            public void run() {
              myBugsValue.setText(Local.parse(REPORT.format(totalCount, changedCount)));
            }
          });
        }
      };

      total.liveQuery(myDetach, updater);
      changed.liveQuery(myDetach, updater);
    }

    private void listenConnection() {
      final ScalarModel.Consumer updater = new ScalarModel.Adapter() {
        @Override
        public void onScalarChanged(ScalarModelEvent objectScalarModelEvent) {
          final Connection connection = myContext.getConnection();
          final ConnectionState cState = connection.getState().getValue();
          final InitializationState iState = connection.getInitializationState().getValue();
          myStateValue.setText(getStateText(cState, iState));
        }
      };
      final Connection connection = myContext.getConnection();
      connection.getState().getEventSource().addAWTListener(myDetach, updater);
      connection.getInitializationState().getEventSource().addAWTListener(myDetach, updater);
    }

    private String getStateText(ConnectionState cState, InitializationState iState) {
      final String text;
      if (cState == null || iState == null) {
        text = "";
      } else if (cState.isDegrading()) {
        text = cState.getName();
      } else if (!iState.isInitialized()) {
        text = iState.getName();
      } else if (iState == InitializationState.REINITIALIZING) {
        text = iState.getName();
      } else if (iState == InitializationState.REINITIALIZATION_REQUIRED && cState == ConnectionState.READY) {
        text = cState.getName() + ", " + iState;
      } else {
        text = cState.getName();
      }
      return English.humanizeEnumerable(text);
    }

    private void setupFonts() {
      myNameLabel.setBorder(UIUtil.createSouthBevel(AwtUtil.getPanelBackground()));
      UIUtil.adjustFont(myNameLabel, 1.35F, Font.BOLD, true);
    }

    private void setupAlignments() {
      myUrlLink.setAlignmentY(0f);
      protect(myNameLabel, myStatusValue, myLoginValue, myProductsValue, myBugsValue, myStateValue);
      UIUtil.setDefaultLabelAlignment(myFormPanel);
    }

    private void protect(JComponent... labels) {
      for(final JComponent c : labels) {
        c.putClientProperty(UIUtil.SET_DEFAULT_LABEL_ALIGNMENT, false);
      }
    }
  }

  protected static class ConnectionInfo {
    public final String connectionName;
    public final String connectionUrl;
    public final String status;
    public final String loginName;
    public final String loginValue;
    public final String productsName;
    public final Collection<String> productsValue;

    public ConnectionInfo(
      String connectionName, String connectionUrl, String status,
      String loginName, String loginValue,
      String productsName, Collection<String> productsValue)
    {
      this.connectionName = connectionName;
      this.connectionUrl = connectionUrl;
      this.status = status;
      this.loginName = loginName;
      this.loginValue = loginValue;
      this.productsName = productsName;
      this.productsValue = productsValue;
    }
  }
}
