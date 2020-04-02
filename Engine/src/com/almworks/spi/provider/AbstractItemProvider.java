package com.almworks.spi.provider;

import com.almworks.api.engine.*;
import com.almworks.api.http.HttpUtils;
import com.almworks.api.platform.ProductInformation;
import com.almworks.spi.provider.wizard.ConnectionWizard;
import com.almworks.util.GlobalLogPrivacy;
import com.almworks.util.LogPrivacyPolizei;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.model.ScalarModel;
import org.almworks.util.*;
import org.almworks.util.detach.Detach;
import org.jetbrains.annotations.*;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractItemProvider implements ItemProvider {
  private static String ourUserAgent;

  protected final String myPrefix;
  protected final BasicScalarModel<ItemProviderState> myState = BasicScalarModel.createWithValue(ItemProviderState.NOT_STARTED, true);
  private final Configuration myConnectionConfigs;

  private final Set<String> myEditedConenctions = Collections15.hashSet();

  public AbstractItemProvider(Configuration connectionsConfig) {
    myPrefix = getProviderID();
    myConnectionConfigs = connectionsConfig;
  }

  public String getPrefix() {
    return myPrefix;
  }

  public void start() {
    ThreadGate.LONG(this).execute(new Runnable() {
      public void run() {
        if (!myState.commitValue(ItemProviderState.NOT_STARTED, ItemProviderState.STARTING))
          return;
        boolean success = false;
        try {
          longStart();
          success = true;
        } finally {
          if (!success) {
            Log.warn(this + " was not properly started");
          }
          // set flag anyway because others will wait for it
          myState.commitValue(ItemProviderState.STARTING, ItemProviderState.STARTED);
        }
      }
    });
  }

  protected abstract void longStart();

  public ScalarModel<ItemProviderState> getState() throws ProviderDisabledException {
    return myState;
  }

  public boolean isStarted() {
    return myState.getValue() == ItemProviderState.STARTED;
  }

  public Configuration createDefaultConfiguration(String itemUrl) {
    return null;
  }

  public boolean isItemUrl(String url) {
    return false;
  }

  public boolean isEnabled() {
    return true;
  }

  public static String getUserAgent() {
    if (ourUserAgent == null) {
      String userAgent = HttpUtils.getEngineVersion();
      ProductInformation product = Context.get(ProductInformation.class);
      if (product != null) {
        String build = product.getBuildNumber().toDisplayableString();
        userAgent += " (" + product.getName() + "/" + product.getVersion() + "." + build + ")";
      }
      ourUserAgent = userAgent;
    }
    return ourUserAgent;
  }

  public Configuration getConnectionConfig(String connectionID) {
    return myConnectionConfigs.getOrCreateSubset(connectionID);
  }

  static {
    GlobalLogPrivacy.installPolizei(new HTTPAuthorizationPolizei());
  }

  private static class HTTPAuthorizationPolizei implements LogPrivacyPolizei {
    private static final Pattern AUTH_PATTERN = Pattern.compile("(Authorization: \\w+ )([^\\s]+)\\s*");
    private static final Pattern PROXY_AUTH_PATTERN = Pattern.compile("(Proxy-Authorization: \\w+ )([^\\s]+)\\s*");

    @NotNull
    public String examine(@NotNull String message) {
      if (message == null) {
        assert false;
        return message;
      }
      if (!message.startsWith("Authorization: ") && !message.startsWith("Proxy-Authorization: "))
        return message;
      String r = repl(message, AUTH_PATTERN);
      if (r != null)
        return r;
      r = repl(message, PROXY_AUTH_PATTERN);
      if (r != null)
        return r;
      return message;
    }

    private String repl(String message, Pattern pattern) {
      Matcher matcher = pattern.matcher(message);
      if (!matcher.matches())
        return null;
      StringBuilder b = new StringBuilder();
      b.append(matcher.group(1));
      b.append(StringUtil.repeatCharacter('x', matcher.group(2).length()));
      return b.toString();
    }
  }

  @Override
  public void showNewConnectionWizard() {
    createNewConnectionWizard().showWizard(null);
  }

  protected abstract ConnectionWizard createNewConnectionWizard();

  @Override
  public void showEditConnectionWizard(Connection connection) {
    synchronized(myEditedConenctions) {
      final String id = connection.getConnectionID();
      if(!myEditedConenctions.contains(id)) {
        final ConnectionWizard wizard = createEditConnectionWizard(connection);
        if(wizard != null) {
          myEditedConenctions.add(id);
          wizard.showWizard(new Detach() {
            @Override
            protected void doDetach() throws Exception {
              synchronized(myEditedConenctions) {
                myEditedConenctions.remove(id);
              }
            }
          });
        }
      }
    }
  }

  protected abstract ConnectionWizard createEditConnectionWizard(Connection connection);

  @Override
  public boolean isEditingConnection(Connection connection) {
    synchronized(myEditedConenctions) {
      return myEditedConenctions.contains(connection.getConnectionID());
    }
  }
}