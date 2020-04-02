package com.almworks.spi.provider.wizard;

import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.CommonConfigurationConstants;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.*;
import com.almworks.api.platform.ProductInformation;
import com.almworks.gui.Wizard;
import com.almworks.gui.WizardPage;
import com.almworks.spi.provider.*;
import com.almworks.util.Pair;
import com.almworks.util.Terms;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.config.*;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.GlobalColors;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.AnAbstractAction;
import com.almworks.util.ui.actions.IdActionProxy;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.*;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static com.almworks.spi.provider.ConnectionTestInformationPlace.MessageHandle;

/**
 * Base New/Edit Connection wizard.
 */
public abstract class ConnectionWizard extends Wizard {
  public static final String URL_PAGE_ID = "wizard.connection.url";
  public static final String TEST_PAGE_ID = "wizard.connection.test";
  public static final String UNITS_PAGE_ID = "wizard.connection.units";
  public static final String NAME_PAGE_ID = "wizard.connection.name";
  public static final String INIT_PAGE_ID = "wizard.connection.init";
  public static final String WAIT_PAGE_ID = "wizard.connection.wait";

  protected static final String UNIT_L = Local.text("app.term.project");
  protected static final String UNITS_L = Local.text("app.term.projects");
  protected static final String UNITS_C = Local.text("app.term.Projects");
  protected static final String TRACKER = Local.text(Terms.key_ConnectionType);

  protected final ComponentContainer myContainer;
  protected final DialogManager myDialogManager;
  protected final ProductInformation myProductInfo;

  protected final Configuration myWizardConfig;
  protected final boolean myEditing;
  protected final Connection myEditedConnection;

  private Detach myOnClose;

  protected ConnectionWizard(String title, ComponentContainer container, Connection connection) {
    //noinspection ConstantConditions
    super(container.getActor(DialogManager.ROLE).createMainBuilder("ConnectionWizard"), title);

    myContainer = container;
    myDialogManager = myContainer.getActor(DialogManager.ROLE);
    myProductInfo = myContainer.getActor(ProductInformation.ROLE);

    if(connection != null) {
      myWizardConfig = ConfigurationUtil.copy(connection.getConfiguration());
      myEditing = true;
      myEditedConnection = connection;
    } else {
      myWizardConfig = MapMedium.createConfig();
      myEditing = false;
      myEditedConnection = null;
    }
  }

  public void showWizard(@Nullable Detach onClose) {
    myOnClose = onClose;
    showPage(URL_PAGE_ID);
  }

  @Override
  protected void wizardFinished() {
    cancelConnectionTest();
    if(myEditing) {
      editingFinished();
    } else {
      creationFinished();
    }
  }

  @Override
  protected void wizardCancelled() {
    cancelConnectionTest();
  }

  @Override
  protected void wizardClosed() {
    cancelConnectionTest();
  }

  @Override
  protected void wizardReleased() {
    if(myOnClose != null) {
      myOnClose.detach();
    }
  }

  protected abstract void cancelConnectionTest();

  private void creationFinished() {
    ConnectionSetupUtil.createConnection(myContainer, myWizardConfig, getNewConnectionSink());
  }

  private void editingFinished() {
    ConnectionSetupUtil.updateConnection(myContainer, myEditedConnection, myWizardConfig);
  }

  protected abstract boolean isIgnoreProxy();

  protected abstract String getUserName();

  protected abstract boolean isAnonymous();

  protected abstract String getUrl();

  protected abstract boolean areCredentialsChanged();

  protected abstract boolean areSelectedUnitsChanged();

  protected abstract NewConnectionSink getNewConnectionSink();

  private void sleepForAShortWhile() {
    try {
      Thread.sleep(1000);
    } catch(InterruptedException ignored) {}
  }

  private void showNextPage(final WizardPage page) {
    ThreadGate.AWT.execute(new Runnable() {
      @Override
      public void run() {
        page.nextInvoked();
        showPage(page.getNextPageID());
      }
    });
  }

  /**
   * Boilerplate URL and credentials page.
   */
  protected abstract class BaseUrlPage<F extends BaseUrlAndCredentialsForm> extends BasePage
    implements ChangeListener, javax.swing.event.ChangeListener, ItemListener
  {
    protected F myForm;

    private boolean myValuesLoaded = false;

    protected BaseUrlPage() {
      super("Specify URL and credentials", URL_PAGE_ID, CANCEL_ID, TEST_PAGE_ID);
    }

    @Override
    protected JComponent createContent() {
      if(myForm == null) {
        myForm = createForm();
        myForm.initialize();
      }
      return myForm.getComponent();
    }

    protected abstract F createForm();

    @Override
    public void aboutToDisplayPage(String prevPageID) {
      if(prevPageID == null) {
        prepareForFirstShowing();
      }
      loadInitialValuesIfNeeded();
    }

    private void prepareForFirstShowing() {
      setNextEnabled(myEditing);
    }

    private void loadInitialValuesIfNeeded() {
      if(myEditing && !myValuesLoaded) {
        myForm.loadValuesFromConfiguration(myWizardConfig);
        myValuesLoaded = true;
      }
    }

    @Override
    public void displayingPage(String prevPageID) {
      disableUrlIfEditing();
      beginInputValidation();
      setInitialFocusOwner();
    }

    private void setInitialFocusOwner() {
      myForm.getInitialFocusOwner().requestFocusInWindow();
    }

    private void disableUrlIfEditing() {
      myForm.setUrlFieldEnabled(!myEditing);
    }

    private void beginInputValidation() {
      addGuiListeners();
      validateInput();
    }

    private void addGuiListeners() {
      myForm.addFormListener(myLifecycle.lifespan(), this);
    }

    @Override
    public void onChange() {
      validateInput();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      validateInput();
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
      if(e.getStateChange() == ItemEvent.SELECTED) {
        validateInput();
      }
    }

    protected void validateInput() {
      checkForAnonymousAndAdjust();
      displayInfo();
      checkForErrorsAndAdjust();
    }

    private void checkForAnonymousAndAdjust() {
      myForm.setCredentialsEnabled(!isAnonymous());
    }

    private void displayInfo() {
      final Pair<String, String> info = getInfoAndDescription();
      myForm.showInfo(info.getFirst(), info.getSecond());
    }

    protected abstract Pair<String, String> getInfoAndDescription();

    private void checkForErrorsAndAdjust() {
      final Pair<String, String> error = getErrorAndDescription();
      myForm.showError(error.getFirst(), error.getSecond());
      setNextEnabled(error.getFirst() == null);
    }

    protected abstract Pair<String, String> getErrorAndDescription();

    @Override
    public void aboutToHidePage(String nextPageID) {
      super.aboutToHidePage(nextPageID);
      if(myNextPageID.equals(nextPageID)) {
        saveValuesToConfiguration();
      }
    }

    protected abstract void saveValuesToConfiguration();

    public boolean isAnonymous() {
      return myForm.isAnonymous();
    }

    public String getUserName() {
      return myForm.getUserName();
    }

    public String getPassword() {
      return myForm.getPassowrd();
    }

    @Nullable
    public String getNormalizedUrl() {
      final String urlString = myForm.getUrl();
      if(urlString == null || urlString.length() == 0) {
        return null;
      }

      URL url;
      try {
        url = new URL(urlString);
      } catch (MalformedURLException e) {
        try {
          url = new URL("http://" + urlString);
        } catch (MalformedURLException e1) {
          return null;
        }
      }

      return url.toExternalForm();
    }
  }

  /**
   * Boilerplate "Advanced" action for URL and credentials pages.
   */
  protected abstract class BaseAdvancedAction extends AnAbstractAction {
    protected BaseAdvancedAction() {
      super("Ad&vanced\u2026");
    }

    protected DialogBuilder createBuider() {
      final DialogBuilder builder = myDialogManager.createBuilder("NewJiraConnection.Advanced");
      builder.setTitle("Advanced Settings");
      builder.setModal(true);
      builder.setIgnoreStoredSize(true);
      builder.setEmptyCancelAction();
      builder.setEmptyOkAction();
      return builder;
    }

    protected ButtonModel createIgnoreModel() {
      final JToggleButton.ToggleButtonModel ignoreModel = new JToggleButton.ToggleButtonModel();
      ignoreModel.setSelected(isIgnoreProxy());
      return ignoreModel;
    }

    protected JComponent createProxySection(ButtonModel ignoreModel) {
      final JCheckBox ignore = new JCheckBox();
      NameMnemonic
        .parseString(String.format(
          "&Local %s server (ignore global proxy setings)", TRACKER))
        .setToButton(ignore);
      ignore.setModel(ignoreModel);

      final AActionButton config = new AActionButton(new IdActionProxy(MainMenu.Tools.CONFIGURE_PROXY));

      final JComponent proxy = Box.createVerticalBox();
      proxy.add(ignore);
      proxy.add(Box.createVerticalStrut(5));
      proxy.add(config);

      return ASeparator.wrap("HTTP Proxy", proxy);
    }
  }

  /**
   * Boilerplate connection test page.
   */
  public abstract class BaseTestPage extends BasePage {
    private JProgressBar myProgress;
    private ConnectionTestInformationPlace myInfoPlace;

    private volatile boolean myCanProceed;
    private volatile boolean myHadProblem;

    protected BaseTestPage() {
      this(TEST_PAGE_ID, URL_PAGE_ID, UNITS_PAGE_ID);
    }

    protected BaseTestPage(String pageID, String prevID, String nextID) {
      super("Testing connection settings", pageID, prevID, nextID);
    }

    @Override
    protected JComponent createContent() {
      myProgress = new JProgressBar();
      myInfoPlace = new ConnectionTestInformationPlace();

      // This one seems to be the longest message there is.
      // It is added here for preferred size calculation.
      final String message = "User account is not specified. No changes to issues is allowed in anonymous mode.";
      myInfoPlace.addMessage(null, message, message);

      final JPanel panel = new JPanel(new BorderLayout(0, 10));
      panel.add(myProgress, BorderLayout.NORTH);
      panel.add(myInfoPlace.getComponent(), BorderLayout.CENTER);
      return panel;
    }

    @Override
    public void aboutToDisplayPage(String prevPageID) {
      clearMessages();
    }

    @Override
    public void displayingPage(String prevPageID) {
      setNextEnabled(false);
      focusBack();
      doBeginConnectionTest();
    }

    private void doBeginConnectionTest() {
      beginConnectionTest();
      myCanProceed = true;
      myHadProblem = false;
    }

    protected abstract void beginConnectionTest();

    @Override
    public void aboutToHidePage(String nextPageID) {
      super.aboutToHidePage(nextPageID);
      if(myPrevPageID.equals(nextPageID)) {
        doCancelConnectionTest();
      }
    }

    private void doCancelConnectionTest() {
      cancelConnectionTest();
      myCanProceed = false;
      myHadProblem = false;
    }

    protected void cancelConnectionTest() {
      ConnectionWizard.this.cancelConnectionTest();
    }

    public void clearMessages() {
      myInfoPlace.clearMessages();
    }

    public void showTesting(boolean inProgress) {
      myProgress.setIndeterminate(inProgress);
    }

    public void showInfo(boolean problem, String shortMessage, String longMessageHtml) {
      myInfoPlace.addMessage(problem ? Icons.ATTENTION : null, shortMessage, longMessageHtml);
      if(problem) {
        myHadProblem = true;
      }
    }

    public void showSuccessful() {
      myInfoPlace.addMessage(Icons.BLUE_TICK, "Connection tested successfully.", null);
      if(myCanProceed) {
        setNextEnabled(true);
        focusNext();
        if(!myHadProblem) {
          showNextPageAfterDelay();
        }
      }
    }

    private void showNextPageAfterDelay() {
      ThreadGate.NEW_THREAD.execute(new Runnable() {
        @Override
        public void run() {
          sleepForAShortWhile();
          if(isWizardShowing() && myCanProceed) {
            showNextPage(BaseTestPage.this);
          }
        }
      });
    }
  }
  
  /**
   * Bolerplate projects/products selection page.
   */
  protected abstract class BaseUnitsPage<U extends CanvasRenderable> extends BasePage
    implements ChangeListener, ItemListener
  {
    private JToggleButton myFilterButton;
    private JToggleButton myUseAllButton;
    private ACheckboxList<U> myUnitsList;
    private ALabelWithExplanation myMessageLabel;

    private boolean mySelectionLoaded = false;
    private boolean myForceSingleSelection = false;
    private int myMaxUnits = 0;
    private boolean myUseAllSelected = false;

    protected BaseUnitsPage() {
      super("Select " + UNITS_L, UNITS_PAGE_ID, URL_PAGE_ID, NAME_PAGE_ID);
    }

    public ACheckboxList<U> getUnitsList() {
      return myUnitsList;
    }

    public JToggleButton getFilterButton() {
      return myFilterButton;
    }

    @Override
    protected JComponent createContent() {
      createRadioButtons();
      createUnitsList();
      createMessageLabel();
      return createWholePanel();
    }

    private void createRadioButtons() {
      myFilterButton = new JRadioButton();
      myUseAllButton = new JRadioButton();

      final ButtonGroup group = new ButtonGroup();
      group.add(myFilterButton);
      group.add(myUseAllButton);
    }

    private void createUnitsList() {
      myUnitsList = new ACheckboxList<U>();
      myUnitsList.setCanvasRenderer(Renderers.defaultCanvasRenderer());
      SpeedSearchController.install(myUnitsList).setSearchSubstring(true);
      myUnitsList.setVisibleRowCount(8);
    }

    private void createMessageLabel() {
      myMessageLabel = new ALabelWithExplanation();
      myMessageLabel.setPositioner(new UIUtil.IndependentPositioner(UIUtil.ALIGN_START, UIUtil.BEFORE));
    }

    private JPanel createWholePanel() {
      final JPanel panel = new JPanel(new BorderLayout(0, 5));
      panel.add(myFilterButton, BorderLayout.NORTH);
      panel.add(createListPanel(), BorderLayout.CENTER);
      panel.add(myUseAllButton, BorderLayout.SOUTH);
      return panel;
    }

    private JPanel createListPanel() {
      final JPanel listPanel = new JPanel(new BorderLayout(0, 5));
      listPanel.add(new JScrollPane(myUnitsList), BorderLayout.CENTER);
      listPanel.add(myMessageLabel, BorderLayout.SOUTH);
      listPanel.setBorder(new EmptyBorder(0, new JRadioButton().getPreferredSize().width, 0, 0));
      return listPanel;
    }

    public void setMaximumUnits(int max) {
      myMaxUnits = max;
    }

    public void setForceSingleSelection(boolean force) {
      myForceSingleSelection = force;
    }

    @Override
    public void aboutToDisplayPage(String prevPageID) {
      adjustRadioButtonNames();
      if(TEST_PAGE_ID.equals(prevPageID)) {
        adjustToAvailableUnits();
        loadInitialSelectionIfNeeded();
      }
    }

    private void adjustRadioButtonNames() {
      final String filterName =
        String.format("Work only with the &selected %s (recommended)", UNITS_L);
      NameMnemonic.parseString(filterName).setToButton(myFilterButton);

      final String useAllName = String.format("Work with &all %s", UNITS_L);
      NameMnemonic.parseString(useAllName).setToButton(myUseAllButton);
    }

    private void adjustToAvailableUnits() {
      final boolean mustSelect = myForceSingleSelection || myMaxUnits > 0;
      myUseAllButton.setEnabled(!mustSelect);

      final int total = getTotalCount();
      myFilterButton.setEnabled(total > 0);

      if(mustSelect) {
        myFilterButton.setSelected(true);
      } else if(total == 0) {
        myUseAllButton.setSelected(true);
      } else {
        (myUseAllSelected ? myUseAllButton : myFilterButton).setSelected(true);
      }

      if(total == 1) {
        myUnitsList.getCheckedAccessor().setSelectedIndex(0);
      }
    }

    protected int getTotalCount() {
      return myUnitsList.getCollectionModel().getSize();
    }

    private void loadInitialSelectionIfNeeded() {
      if(myEditing && !mySelectionLoaded) {
        loadSelectionFromConfiguration();
        adjustToLoadedSelection();
        mySelectionLoaded = true;
      }
    }

    protected abstract void loadSelectionFromConfiguration();

    private void adjustToLoadedSelection() {
      if(getSelectedCount() > 0) {
        myFilterButton.setSelected(true);
      } else {
        myUseAllButton.setSelected(true);
      }
    }

    protected int getSelectedCount() {
      return myUnitsList.getCheckedAccessor().getSelectedCount();
    }

    @Override
    public void displayingPage(String prevPageID) {
      setInitialFocusOwner();
      adjustListToSelection();
      beginInputValidation();
    }

    private void setInitialFocusOwner() {
      if(myFilterButton.isSelected()) {
        myUnitsList.requestFocusInWindow();
      } else {
        myUseAllButton.requestFocusInWindow();
      }
    }

    private void adjustListToSelection() {
      if(getSelectedCount() > 0) {
        myUnitsList.scrollSelectionToView();
      } else {
        myUnitsList.getSelectionAccessor().ensureSelectionExists();
        myUnitsList.scrollListSelectionToView();
      }
    }

    private void beginInputValidation() {
      addGuiListeners();
      validateInput();
    }

    private void addGuiListeners() {
      final Lifespan life = myLifecycle.lifespan();
      life.add(UIUtil.addItemListener(myFilterButton, this));
      life.add(UIUtil.addItemListener(myUseAllButton, this));
      myUnitsList.getCollectionModel().addAWTChangeListener(life, this);
      myUnitsList.getCheckedAccessor().addChangeListener(life, this);
    }

    @Override
    public void onChange() {
      validateInput();
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
      if(e.getStateChange() == ItemEvent.SELECTED) {
        validateInput();
      }
    }

    private void validateInput() {
      adjustToFilteringState();
      checkAndDisplayMessage();
    }

    private void adjustToFilteringState() {
      final boolean filtering = myFilterButton.isSelected();
      myUnitsList.setEnabled(myFilterButton.isEnabled() && filtering);
      myUseAllSelected = !filtering;
    }

    private void checkAndDisplayMessage() {
      final Pair<String, String> error = checkForErrors();
      if(error != null) {
        displayError(error);
        setNextEnabled(false);
      } else {
        displayWarningOrInfo(checkForWarnings());
        setNextEnabled(true);
      }
    }

    @Nullable
    protected Pair<String, String> checkForErrors() {
      try {
        doCheckForErrors();
        return null;
      } catch(CheckFailed failure) {
        return failure.asStringPair();
      }
    }

    private void doCheckForErrors() throws CheckFailed {
      checkForcedSingleSelection();
      checkForMaxUnits();
      checkSomethingIsSelectedIfFiltering();
    }

    private void checkForcedSingleSelection() throws CheckFailed {
      if(myForceSingleSelection && getSelectedCount() != 1) {
        throw new CheckFailed(
          String.format("Please select a single %s.", UNIT_L));
      }
    }

    private void checkForMaxUnits() throws CheckFailed {
      if(myMaxUnits > 0) {
        final int selected = getSelectedCount();
        if(selected < 1 || selected > myMaxUnits) {
          throw new CheckFailed(myMaxUnits == 1
            ? String.format("Please select a single %s.", UNIT_L)
            : String.format("Please select up to %d %s.", myMaxUnits, UNITS_L));
        }
      }
    }

    private void checkSomethingIsSelectedIfFiltering() throws CheckFailed {
      if(myFilterButton.isSelected() && getSelectedCount() < 1) {
        throw new CheckFailed(
          String.format("Please select one or more %s.", UNITS_L));
      }
    }

    @Nullable
    protected Pair<String, String> checkForWarnings() {
      return null;
    }

    private void displayError(Pair<String, String> error) {
      myMessageLabel.setTextForeground(GlobalColors.ERROR_COLOR);
      myMessageLabel.setTextAndExplanation(error);
      myMessageLabel.setEnabled(true);
    }

    private void displayWarningOrInfo(Pair<String, String> warning) {
      if(warning != null) {
        displayWarning(warning);
      } else {
        displayInfo();
      }
    }

    private void displayWarning(Pair<String, String> warning) {
      myMessageLabel.setTextForeground(GlobalColors.ERROR_COLOR);
      myMessageLabel.setTextAndExplanation(warning);
      myMessageLabel.setEnabled(true);
    }

    private void displayInfo() {
      if(getTotalCount() > 5) {
        displayCounts();
      } else {
        displayEmpty();
      }
      adjustMessageLabelToFilteringState();
    }

    private void displayCounts() {
      myMessageLabel.setTextForeground(UIManager.getColor("Label.foreground"));
      myMessageLabel.setTextAndExplanation(getCountInfo(), null);
    }

    private String getCountInfo() {
      final int total = getTotalCount();
      final int selected = getSelectedCount();
      return String.format("%d of %d %s selected", selected, total, UNITS_L);
    }

    private void displayEmpty() {
      myMessageLabel.setTextAndExplanation(" ", null);
    }

    private void adjustMessageLabelToFilteringState() {
      myMessageLabel.setEnabled(myFilterButton.isEnabled() && myFilterButton.isSelected());
    }

    @Override
    public void aboutToHidePage(String nextPageID) {
      super.aboutToHidePage(nextPageID);
      if(myPrevPageID.equals(nextPageID)) {
        cancelConnectionTest();
      }
      saveSelectionToConfiguration();
    }

    protected abstract void saveSelectionToConfiguration();

    public boolean isUnitFilterOk() {
      return checkForErrors() == null;
    }

    protected boolean isFiltering() {
      return myFilterButton.isSelected();
    }

    protected List<U> getAllUnits() {
      return Collections15.arrayList(myUnitsList.getCollectionModel().toList());
    }

    protected List<U> getSelectedUnits() {
      return Collections15.arrayList(myUnitsList.getCheckedAccessor().getSelectedItems());
    }

    protected List<U> getUnselectedUnits() {
      final List<U> units = getAllUnits();
      units.removeAll(getSelectedUnits());
      return units;
    }
  }

  /**
   * Boilerplate Name & Review page.
   */
  protected abstract class BaseNamePage extends BasePage implements ChangeListener {
    private ConnectionNameForm myForm;
    private boolean myNameIsChangedByUser = false;

    protected BaseNamePage() {
      super("Name your connection", NAME_PAGE_ID, UNITS_PAGE_ID, FINISH_ID);
    }

    @Override
    protected JComponent createContent() {
      if(myForm == null) {
        myForm = new ConnectionNameForm();
      }
      return myForm.myWholePanel;
    }

    @Override
    public void aboutToDisplayPage(String prevPageID) {
      adjustUnitsLabelText();
      displayValuesForReview();
      if(!myNameIsChangedByUser) {
        setInitialConnectionName();
      }
    }

    private void adjustUnitsLabelText() {
      myForm.myUnitsLabel.setText(UNITS_C + ":");
    }

    private void displayValuesForReview() {
      displayUrlAndUsername();
      displaySelectedUnits();
    }

    private void displayUrlAndUsername() {
      myForm.myUrlLink.setUrl(getUrl());
      myForm.myUserLabel.setText(isAnonymous() ? "Anonymous access" : getUserName());
    }

    private void displaySelectedUnits() {
      final List<String> units = getSelectedUnitNames();
      if(units == null || units.isEmpty()) {
        myForm.myUnitsArea.setText("All " + UNITS_L);
      } else {
        myForm.myUnitsArea.setText(StringUtil.implode(units, StringUtil.LINE_SEPARATOR));
      }
      myForm.myUnitsArea.setCaretPosition(0);
    }

    @Nullable
    protected abstract List<String> getSelectedUnitNames();

    private void setInitialConnectionName() {
      final String name = myEditing
        ? loadNameFromConfiguration()
        : suggestBetterName(suggestConnectionNameByUrl());

      myForm.myNameField.setText(Util.NN(name));
    }

    private String loadNameFromConfiguration() {
      return myWizardConfig.getSetting(CommonConfigurationConstants.CONNECTION_NAME, null);
    }

    protected abstract String suggestConnectionNameByUrl();

    private String suggestBetterName(String name) {
      if(name == null || name.isEmpty()) {
        return name;
      }

      final List<String> units = getSelectedUnitNames();
      if(units != null && units.size() == 1) {
        return name + " - " + units.get(0);
      }

      return name;
    }

    @Override
    public void displayingPage(String prevPageID) {
      decideAboutNextPage();
      setInitialFocusOwner();
      beginInputValidation();
    }

    private void decideAboutNextPage() {
      if(initPageIsNeeded()) {
        myNextPageID = INIT_PAGE_ID;
      } else if(waitPageIsNeeded()) {
        myNextPageID = WAIT_PAGE_ID;
      } else {
        myNextPageID = FINISH_ID;
      }
    }

    private boolean waitPageIsNeeded() {
      return myEditing && (areCredentialsChanged() || areSelectedUnitsChanged());
    }

    private boolean initPageIsNeeded() {
      return !myEditing;
    }

    private void setInitialFocusOwner() {
      myForm.myNameField.requestFocusInWindow();
      myForm.myNameField.selectAll();
    }

    private void beginInputValidation() {
      addGuiListeners();
      validateInput();
    }

    private void addGuiListeners() {
      DocumentUtil.addChangeListener(myLifecycle.lifespan(), myForm.myNameField.getDocument(), this);
    }

    @Override
    public void onChange() {
      myNameIsChangedByUser = true;
      validateInput();
    }

    private void validateInput() {
      setNextEnabled(!getConnectionName().isEmpty());
    }

    private String getConnectionName() {
      return myForm.myNameField.getText().trim();
    }

    @Override
    public void aboutToHidePage(String nextPageID) {
      super.aboutToHidePage(nextPageID);
      saveNameToConfiguration();
      if(!FINISH_ID.equals(nextPageID) && !myPrevPageID.equals(nextPageID)) {
        // Calling it "manually" here to start background work,
        // because the wizard is not being closed yet.
        // It won't be called again by Wizard, because we'll
        // never show() a FINISH_ID.
        wizardFinished();
      }
    }

    private void saveNameToConfiguration() {
      myWizardConfig.setSetting(CommonConfigurationConstants.CONNECTION_NAME, getConnectionName());
    }
  }

  /**
   * Connection initialization page.
   */
  protected class InitPage extends BasePage implements NewConnectionSink {
    private final int SMALL_STEP = 5;

    private JProgressBar myProgress;
    private ConnectionTestInformationPlace myInfoPlace;
    private MessageHandle myActivity;
    private MessageHandle myStatus;

    private final InitializationTracker myTracker = new InitializationTracker() {
      @Override
      protected void display(boolean error, String message) {
        if(isWizardShowing() && message != null && !message.isEmpty()) {
          if(error) {
            showError(message);
          } else {
            showPleaseWait();
            showActivity(null, message);
          }
        }
      }

      @Override
      protected void progress(double progress) {
        if(isWizardShowing()) {
          myProgress.setValue((int) (100 * progress) + SMALL_STEP);
        }
      }
    };

    public InitPage() {
      super("Initializing connection", INIT_PAGE_ID, NAME_PAGE_ID, CLOSE_ID);
      myMoreAction = myTracker.getRetryAction();
    }

    @Override
    protected JComponent createContent() {
      myProgress = new JProgressBar();
      myProgress.setMaximum(myProgress.getMaximum() + SMALL_STEP);
      myInfoPlace = new ConnectionTestInformationPlace();
      final JPanel panel = new JPanel(new BorderLayout(0, 10));
      panel.add(myProgress, BorderLayout.NORTH);
      panel.add(myInfoPlace.getComponent(), BorderLayout.CENTER);
      return panel;
    }

    @Override
    public void aboutToDisplayPage(String prevPageID) {
      myInfoPlace.clearMessages();
      setBackEnabled(false);
    }

    @Override
    public void displayingPage(String prevPageID) {
      focusNext();
      myProgress.setIndeterminate(true);
      showPleaseWait();
    }

    @Override
    public void connectionCreated(Connection connection) {
      Threads.assertAWTThread();
      if(isWizardShowing()) {
        myProgress.setIndeterminate(false);
        myProgress.setValue(SMALL_STEP);
        if(connection != null) {
          myTracker.attachTo(myLifecycle.lifespan(), connection);
        } else {
          showError("Cannot create connection.");
        }
      }
    }

    private void showPleaseWait() {
      showStatus(null, "Please wait\u2026");
    }

    private void showStatus(Icon icon, String text) {
      if(myStatus == null) {
        myStatus = myInfoPlace.addMessage(icon, text, null);
      } else {
        myStatus.setIcon(icon);
        myStatus.setMessage(text);
      }
    }

    private void showActivity(Icon icon, String text) {
      if(myActivity == null) {
        myActivity = myInfoPlace.addMessage(icon, text, null);
      } else {
        myActivity.setIcon(icon);
        myActivity.setMessage(text);
      }
    }

    private void showError(String text) {
      showStatus(Icons.ATTENTION, text);
      showActivity(null, null);
    }

    private void showSuccess(String text) {
      showStatus(Icons.BLUE_TICK, text);
      showActivity(null, null);
    }

    @Override
    public void showMessage(String message) {
      Threads.assertAWTThread();
      if(isWizardShowing()) {
        showActivity(null, message);
      }
    }

    @Override
    public void initializationComplete() {
      Threads.assertAWTThread();
      if(isWizardShowing()) {
        showSuccess("Connection is ready.");
        closeAfterDelay();
      }
    }

    private void closeAfterDelay() {
      ThreadGate.NEW_THREAD.execute(new Runnable() {
        @Override
        public void run() {
          sleepForAShortWhile();
          if(isWizardShowing()) {
            showNextPage(InitPage.this);
          }
        }
      });
    }
  }

  /**
   * "Please Wait" page.
   */
  protected class WaitPage extends BasePage {
    private ALabel myLabel;

    public WaitPage() {
      super(String.format("Reloading %s configuration", TRACKER), WAIT_PAGE_ID, NAME_PAGE_ID, CLOSE_ID);
    }

    @Override
    protected JComponent createContent() {
      if(myLabel == null) {
        myLabel = new ALabel();
        myLabel.setVerticalAlignment(SwingConstants.TOP);
        myLabel.setPreferredWidth(UIUtil.getColumnWidth(myLabel) * 30);
      }
      return myLabel;
    }

    @Override
    public void aboutToDisplayPage(String prevPageID) {
      assert myEditing;
      setExplanationText();
      setBackEnabled(false);
    }

    private void setExplanationText() {
      final boolean creds = areCredentialsChanged();
      final boolean projs = areSelectedUnitsChanged();
      assert creds || projs;

      final String what;
      if(creds) {
        what = "username and password";
      } else {
        what = "selected " + Local.text("app.term.projects");
      }

      final String appName = myProductInfo.getName();

      final String explanation = String.format(
        "<html>You have changed %s, so %s is now reloading %s configuration." +
        "<br><br>Once reloading is finished, your queries will be returning correct results. " +
        "If you have any open tabs, they might show outdated results.",
        what, appName, TRACKER);

      myLabel.setText(explanation);
    }
  }
}
