package com.almworks.bugzilla.provider;

import com.almworks.api.container.ComponentContainer;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.spi.provider.DefaultInformationPanel;
import com.almworks.util.L;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.ui.UIComponentWrapper;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;

import java.util.Collection;

public class BugzillaConnectionPanel extends DefaultInformationPanel {
  public BugzillaConnectionPanel(BugzillaContext context) {
    super(context);
    setupForm();
  }

  @Override
  protected Modifiable getConnectionModifiable(Lifespan life) {
    final SimpleModifiable modifiable = new SimpleModifiable();
    final BugzillaContext context = (BugzillaContext) myContext;
    context.getConfiguration().getEventSource().addAWTListener(life, new ScalarModel.Adapter<OurConfiguration>() {
      @Override
      public void onScalarChanged(ScalarModelEvent<OurConfiguration> event) {
        modifiable.fireChanged();
      }
    });
    return modifiable;
  }

  @Override
  protected ConnectionInfo getConnectionInfo() {
    final BugzillaContext context = (BugzillaContext) myContext;
    final OurConfiguration config = context.getConfiguration().getValue();

    return new ConnectionInfo(
      config.getConnectionName(), extractUrl(config), "The connection is online.",
      "Login:", extractLogin(config), "Products:", extractProducts(config));
  }

  private String extractUrl(OurConfiguration config) {
    String url = null;
    try {
      url = config.getBaseURL();
      return BugzillaIntegration.normalizeURL(url);
    } catch(Exception e) {
      return L.content(url == null
        ? "Invalid URL (" + e.getMessage() + ")"
        : url + " (invalid URL: " + e.getMessage() + ")");
    }
  }

  private String extractStatus() {
    return "The connection is online.";
  }

  private String extractLogin(OurConfiguration config) {
    if(config.isAnonymousAccess()) {
      return "<html>Anonymous access";
    }
    return config.getUsername();
  }

  private Collection<String> extractProducts(OurConfiguration config) {
    if(!config.isLimitByProduct()) {
      return Collections15.arrayList("All available products");
    }
    return Collections15.arrayList(config.getLimitingProducts());
  }

  public static UIComponentWrapper getLazyWrapper(ComponentContainer container) {
    return getLazyWrapper(container, BugzillaConnectionPanel.class);
  }
}
