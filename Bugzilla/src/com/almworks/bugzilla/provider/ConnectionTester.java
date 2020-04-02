package com.almworks.bugzilla.provider;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.http.HttpClientProvider;
import com.almworks.api.http.HttpLoaderFactory;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.integration.data.BugzillaLists;
import com.almworks.spi.provider.util.BasicHttpAuthHandler;
import com.almworks.util.English;
import com.almworks.util.L;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.config.Configuration;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.threads.Marshaller;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.ColorUtil;
import org.almworks.util.*;
import org.almworks.util.detach.Lifecycle;

import java.awt.*;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 * @author pzvyagin
 */
class ConnectionTester {
  public static final Convertor<String, Product> STRING_TO_PRODUCT = new Convertor<String, Product>() {
    @Override
    public Product convert(String value) {
      return new Product(value, -1);
    }
  };

  public static Product[] createProducts(String[] names) {
    return STRING_TO_PRODUCT.collectArray(names, Product.class);
  }

  private static int threadCount = 0;

  private final ComponentContainer mySubcontainer;

  private final Object myThreadLock = new Object();
  private Attempt myAttempt;

  private final Lifecycle myTestCycle = new Lifecycle();

  private volatile BugzillaIntegration myIntegration;
  private volatile List<String> myProductNames;
  private volatile Map<String, Integer> myTotalCounts;
  private volatile OrderListModel<Product> myProductsModel;

  private final TestProgressIndicator myMainIndicator;

  public ConnectionTester(ComponentContainer subcontainer, BugzillaConnection connection,
    TestProgressIndicator mainIndicator)
  {
    mySubcontainer = subcontainer;
    myMainIndicator = createAwtIndicator(mainIndicator);
  }

  private TestProgressIndicator createAwtIndicator(TestProgressIndicator indicator) {
    return Marshaller.createMarshalled(indicator, TestProgressIndicator.class, ThreadGate.AWT_IMMEDIATE);
  }

  public void cancelTesting() {
    final Attempt cancelledAttempt;

    synchronized(myThreadLock) {
      cancelledAttempt = myAttempt;
      myAttempt = null;
      clearTestResults();
    }

    if(cancelledAttempt != null) {
      cancelledAttempt.cancel();
    }

    myTestCycle.cycle();
    myMainIndicator.showTesting(false);
  }

  public void test(
    Configuration testConfiguration, boolean liteUrl)
  {
    final Attempt cancelledAttempt;
    final Attempt workingAttempt;

    synchronized(myThreadLock) {
      cancelledAttempt = myAttempt;
      workingAttempt = new Attempt(testConfiguration, mySubcontainer, liteUrl);
      myAttempt = workingAttempt;
      clearTestResults();
    }

    if(cancelledAttempt != null) {
      cancelledAttempt.cancel();
    }

    myTestCycle.cycle();
    myMainIndicator.showTesting(true);
    workingAttempt.start();
  }

  private void clearTestResults() {
    myIntegration = null;
    myProductNames = null;
    myTotalCounts = null;
    myProductsModel = null;
  }

  protected void acceptIntegration(Attempt source, BugzillaIntegration integration) {
    if(checkAttempt(source)) {
      myIntegration = integration;
    }
  }

  protected void acceptProductList(Attempt source, List<String> products) {
    if(checkAttempt(source)) {
      myProductNames = products;
    }
  }

  protected void acceptTotalBugCounts(Attempt source, Map<String, Integer> map) {
    if(checkAttempt(source)) {
      myTotalCounts = map;
    }
  }

  protected void logTestingMessage(Attempt source, boolean problem, String message, String description) {
    if(checkAttempt(source)) {
      myMainIndicator.showMessage(problem, message, description);
      Log.debug("CT: test " + (problem ? "failed" : "ok") + ' ' + message + " | " + description);
    }
  }

  protected void testSucceeded(Attempt source) {
    if(clearAttempt(source)) {
      ThreadGate.AWT.execute(new Runnable() {
        public void run() {
          fillProductModel();
          showSuccess(myMainIndicator);
        }
      });
    }
  }

  private void showSuccess(TestProgressIndicator indicator) {
    indicator.showTesting(false);
    indicator.showSuccessful();
  }

  private void fillProductModel() {
    assert myProductNames != null;

    final List<Product> list = Collections15.arrayList();
    for(final String name : myProductNames) {
      final int total = getTotalCount(name);
      list.add(new Product(name, total));
    }
    myProductsModel = OrderListModel.create(list);
  }

  private int getTotalCount(String name) {
    final int total;
    if(bugCountsAreAvailable()) {
      total = Util.NN(myTotalCounts.get(name), 0);
    } else {
      total = -1;
    }
    return total;
  }

  public boolean bugCountsAreAvailable() {
    // A null map means we didn't try to count (not Lite).
    // An empty map here most probably means that access to Bugzilla's
    // reporting is restricted, so we can't count bugs this way.
    return myTotalCounts != null && !myTotalCounts.isEmpty();
  }

  protected void testCancelled(Attempt source) {
    if(clearAttempt(source)) {
      myMainIndicator.showTesting(false);
    }
  }

  protected void testFailed(Attempt source) {
    if(clearAttempt(source)) {
      showFailure(myMainIndicator);
    }
  }

  private void showFailure(TestProgressIndicator indicator) {
    indicator.showTesting(false);
    indicator.showMessage(true, "Connection test failed.", null);
  }

  private boolean clearAttempt(Attempt source) {
    synchronized(myThreadLock) {
      if(myAttempt == source) {
        myAttempt = null;
        return true;
      }
    }
    return false;
  }

  private boolean checkAttempt(Attempt source) {
    synchronized(myThreadLock) {
      return myAttempt == source;
    }
  }

  public AListModel<Product> getProductsModel() {
    return myProductsModel;
  }

  private class Attempt implements Runnable {
    private final BasicScalarModel<Boolean> myCancelFlag = BasicScalarModel.createWithValue(Boolean.FALSE, true);
    private final Thread myTestThread;
    private final OurConfiguration myConfig;
    private final ComponentContainer mySubcontainer;
    private final HttpClientProvider myHttpClientProvider;
    private final HttpLoaderFactory myHttpLoaderFactory;
    private final boolean myLiteUrl;

    private BugzillaIntegration myIntegration;

    public Attempt(Configuration testConfiguration, ComponentContainer subcontainer, boolean liteUrl) {
      myConfig = new OurConfiguration(testConfiguration);
      mySubcontainer = subcontainer;
      myTestThread = Threads.createThread("test#" + (++threadCount), this);
      myHttpClientProvider = subcontainer.getActor(HttpClientProvider.ROLE);
      myHttpLoaderFactory = subcontainer.getActor(HttpLoaderFactory.ROLE);
      myLiteUrl = liteUrl;
    }

    public void run() {
      try {
        doInitIntegration();
        doCheckAuthentication();
        doLoadProducts();
        doLoadBugCounts();
        testSucceeded(this);
      } catch(TestCancelled testCancelled) {
        testCancelled(this);
      } catch(TestFailed testFailed) {
        logTestingMessage(this, true, testFailed.getMessage(), null);
        testFailed(this);
      } catch(Exception e) {
        Log.warn("cannot test connection", e);
        testFailed(this);
      }
    }

    public void cancel() {
      myCancelFlag.setValue(Boolean.TRUE);
    }

    public void start() {
      myTestThread.start();
    }

    private void checkCancelled() throws TestCancelled {
      if(myCancelFlag.getValue() == Boolean.TRUE) {
        throw new TestCancelled();
      }
    }

    private void doInitIntegration() throws TestFailed {
      checkCancelled();

      try {
        myIntegration = BugzillaUtil.createIntegration(
          myHttpLoaderFactory, myHttpClientProvider, getURL(), true,
          null, null, mySubcontainer == null ? null : mySubcontainer.instantiate(BasicHttpAuthHandler.class),
          myCancelFlag, myConfig.isCharsetSpecified() ? myConfig.getCharset() : "UTF-8", null,
          myConfig.isIgnoreProxy(), null, null, myConfig.getEmailSuffixIfUsing(), null, null);
        myIntegration.checkConnection();
        acceptIntegration(this, myIntegration);
        logTestingMessage(this, false, "Connected to Bugzilla.", null);
      } catch (MalformedURLException e) {
        Log.warn("connection test failed", e);
        throw new TestFailed(L.content("Bad URL"));
      } catch (ConnectorException e) {
        Log.warn("Connection test failed", e);
        throw new TestFailed(e.getShortDescription());
      }
    }

    private void doCheckAuthentication() throws TestFailed {
      if(myConfig.isAnonymousAccess()) {
        return;
      }

      checkCancelled();

      try {
        myIntegration.setCredentials(myConfig.getUsername(), myConfig.getPassword(), null);
        myIntegration.checkAuthentication();
        logTestingMessage(this, false, "Login and password are accepted.", null);
      } catch (ConnectorException e) {
        Log.warn("check auth failed", e);
        throw new TestFailed(e.getShortDescription());
      }
    }

    private void doLoadProducts() throws TestFailed {
      checkCancelled();

      final BugzillaLists bugzillaLists = getBugzillaLists(myIntegration);
      final List<String> products = bugzillaLists.getStringList(BugzillaAttribute.PRODUCT);
      acceptProductList(this, products);
      reportProductCount(products);
    }

    private BugzillaLists getBugzillaLists(BugzillaIntegration myIntegration) throws TestFailed {
      try {
        return myIntegration.getBugzillaLists(null, null);
      } catch (ConnectorException e) {
        throw new TestFailed(e.getShortDescription());
      }
    }

    private void reportProductCount(List<String> products) {
      logTestingMessage(this, false, "Accessible products: " + products.size(), null);
    }

    private void doLoadBugCounts() throws TestFailed {
      if(!myLiteUrl) {
        return;
      }

      checkCancelled();
      try {
        final Map<String, Integer> map = myIntegration.loadProductBugCounts();
        acceptTotalBugCounts(this, map); 
        reportBugCounts(map);
      } catch (ConnectorException e) {
        Log.warn("bug count check failed", e);
        throw new TestFailed(e.getShortDescription());
      }
    }

    private void reportBugCounts(Map<String, Integer> map) {
      if(map.isEmpty()) {
        logTestingMessage(this, false, "Bug counts are not available.", null);
      } else {
        logTestingMessage(this, false, "Accessible bugs: " + getTotalCount(map), null);
      }
    }

    private int getTotalCount(Map<String, Integer> map) {
      int total = 0;
      for(final int i : map.values()) {
        total += i;
      }
      return total;
    }

    private String getURL() throws TestFailed {
      String baseURL;
      try {
        baseURL = myConfig.getBaseURL();
      } catch (ConfigurationException e) {
        throw new TestFailed(L.content("No URL specified"));
      }
      return baseURL;
    }
  }

  private static class TestFailed extends Exception {
    public TestFailed(String message) {
      super(message);
    }
  }

  private static final class TestCancelled extends TestFailed {
    public TestCancelled() {
      super("");
    }
  }

  public static class Product implements CanvasRenderable {
    private final String myName;
    private final int myTotalCount;

    private Product(String name, int totalCount) {
      myName = name;
      myTotalCount = totalCount;
    }

    @Override
    public void renderOn(Canvas canvas, CellState state) {
      renderName(canvas, state);
      if(myTotalCount >= 0) {
        renderTotal(canvas, state);
      }
    }

    private void renderName(Canvas canvas, CellState state) {
      canvas.appendText(myName);
    }

    private void renderTotal(Canvas canvas, CellState state) {
      makeCountSection(canvas, state).appendText(
        String.format("(%d %s)", myTotalCount, English.getSingularOrPlural("bug", myTotalCount)));
    }

    private CanvasSection makeCountSection(Canvas canvas, CellState state) {
      final CanvasSection section = canvas.newSection();
      final Color halfway = ColorUtil.between(state.getForeground(), state.getOpaqueBackground(), 0.5f);
      section.setForeground(halfway);
      section.appendText(" ");
      return section;
    }

    public String getName() {
      return myName;
    }

    public int getTotalCount() {
      return myTotalCount;
    }

    @Override
    public boolean equals(Object o) {
      if(this == o) {
        return true;
      }
      if(o == null || getClass() != o.getClass()) {
        return false;
      }
      return Util.equals(myName, ((Product)o).myName);
    }

    @Override
    public int hashCode() {
      return myName != null ? myName.hashCode() : 0;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  public static interface TestProgressIndicator {
    void showMessage(boolean problem, String message, String explanation);
    void showTesting(boolean inProgress);
    void showSuccessful();
  }
}
