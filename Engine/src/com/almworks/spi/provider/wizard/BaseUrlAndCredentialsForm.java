package com.almworks.spi.provider.wizard;

import com.almworks.util.advmodel.SelectionInListModel;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Convertors;
import com.almworks.util.commons.Condition;
import com.almworks.util.commons.Factory1;
import com.almworks.util.components.ALabelWithExplanation;
import com.almworks.util.components.completion.CompletingComboBox;
import com.almworks.util.components.completion.CompletingComboBoxController;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.ui.*;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ItemListener;
import java.util.Collections;

/**
 * Implements GUI logic for URL and credentials page atop
 * simple component accessors.
 */
public abstract class BaseUrlAndCredentialsForm implements Factory1<Condition<String>, String> {
  private static final UIUtil.Positioner POPUP_POSITIONER =
    new UIUtil.IndependentPositioner(UIUtil.ALIGN_START, UIUtil.NOTICE_POPUP_Y);

  public void initialize() {
    adjustToPlaform();
    configureUrlFieldController();
    configureMessageLabels();
    configureMessagePlace();
    clearMessages();
  }

  private void adjustToPlaform() {
    UIUtil.setDefaultLabelAlignment(getWholePanel());
    Aqua.disableMnemonics(getWholePanel());
  }

  private void configureUrlFieldController() {
    final CompletingComboBoxController controller = getUrlField().getController();
    controller.setFilterFactory(this);
    controller.setConvertors(Convertors.<String>identity(), Convertors.<String>identity());
    controller.setCanvasRenderer(Renderers.canvasToString());
    controller.setMaxMatchesToShow(10);
    controller.setMinCharsToShow(3);
  }

  private void configureMessageLabels() {
    getInfoLabel().setBorder(
      new EmptyBorder(0, 0, Math.round(UIUtil.getLineHeight(getUsernameField()) / 4f), 0));
    getInfoLabel().setPositioner(POPUP_POSITIONER);
    getErrorLabel().setTextForeground(GlobalColors.ERROR_COLOR);
    getErrorLabel().setPositioner(POPUP_POSITIONER);
  }

  private void configureMessagePlace() {
    final Border border = getUsernameField().getBorder();
    final JPanel messagePlace = getMessagePlace();
    if(border != null) {
      final Insets insets = border.getBorderInsets(getUsernameField());
      messagePlace.setBorder(
        new EmptyBorder(UIUtil.getLineHeight(getUsernameField()), insets.left, 0, insets.right));
    }
    messagePlace.setLayout(new InlineLayout(InlineLayout.VERTICAL));
    messagePlace.add(getInfoLabel());
    messagePlace.add(getErrorLabel());
  }

  private void clearMessages() {
    showInfo(null, null);
    showError(null, null);
  }

  public JComponent getComponent() {
    return getWholePanel();
  }

  public String getUrl() {
    final CompletingComboBox myUrl = getUrlField();
    if(myUrl.isEditable()) {
      return String.valueOf(myUrl.getEditor().getItem()).trim();
    } else {
      return String.valueOf(myUrl.getSelectedItem());
    }
  }

  public boolean isAnonymous() {
    return getAnonymousCheckBox().isSelected();
  }

  public String getUserName() {
    return isAnonymous() ? null : getUsernameField().getText().trim();
  }

  public String getPassowrd() {
    return isAnonymous() ? null : getPasswordField().getText();
  }

  public void initUrls(java.util.List<String> lockedUrls) {
    final CompletingComboBox url = getUrlField();
    if(lockedUrls != null && !lockedUrls.isEmpty()) {
      url.setEditable(false);
      url.getController().setModel(SelectionInListModel.create(lockedUrls, null));
      url.setSelectedIndex(0);
    } else {
      url.setEditable(true);
      url.getController().setModel(
        SelectionInListModel.create(Collections.emptyList(), null));
      url.setSelectedItem("");
    }
  }

  public void setUrlFieldEnabled(boolean enabled) {
    getUrlField().setEnabled(enabled);
  }

  public void setCredentialsEnabled(boolean enabled) {
    getUsernameField().setEnabled(enabled);
    getPasswordField().setEnabled(enabled);
  }

  @Override
  public Condition<String> create(String argument) {
    final String typed = argument.toLowerCase();
    return new Condition<String>() {
      @Override
      public boolean isAccepted(String value) {
        return value.toLowerCase().indexOf(typed) >= 0;
      }
    };
  }

  public void showError(String text, String descr) {
    final ALabelWithExplanation error = getErrorLabel();
    error.setTextAndExplanation(text, descr);
    error.setVisible(text != null);
  }

  public void showInfo(String text, String descr) {
    final ALabelWithExplanation info = getInfoLabel();
    info.setTextAndExplanation(text, descr);
    getInfoLabel().setVisible(text != null);
  }

  public void addFormListener(Lifespan life, Object listener) {
    if(listener instanceof ItemListener) {
      life.add(UIUtil.addItemListener(getUrlField(), (ItemListener)listener));
    }

    if(listener instanceof ChangeListener) {
      DocumentUtil.addChangeListener(life, getUrlDocument(), (ChangeListener)listener);
      DocumentUtil.addChangeListener(life, getUsernameField().getDocument(), (ChangeListener)listener);
      DocumentUtil.addChangeListener(life, getPasswordField().getDocument(), (ChangeListener)listener);
    }

    if(listener instanceof javax.swing.event.ChangeListener) {
      UIUtil.addChangeListeners(life, (javax.swing.event.ChangeListener)listener, getAnonymousCheckBox());
    }
  }

  private Document getUrlDocument() {
    return ((JTextComponent)getUrlField().getEditor().getEditorComponent()).getDocument();
  }

  @NotNull
  public JComponent getInitialFocusOwner() {
    final CompletingComboBox url = getUrlField();
    if(url.isEnabled()) {
      return url;
    }
    final JTextField username = getUsernameField();
    return username.isEnabled() ? username : getAnonymousCheckBox();
  }

  protected abstract JPanel getWholePanel();
  protected abstract CompletingComboBox getUrlField();
  protected abstract JTextField getUsernameField();
  protected abstract JPasswordField getPasswordField();
  protected abstract JCheckBox getAnonymousCheckBox();
  protected abstract JPanel getMessagePlace();
  protected abstract ALabelWithExplanation getInfoLabel();
  protected abstract ALabelWithExplanation getErrorLabel();

  public abstract void loadValuesFromConfiguration(ReadonlyConfiguration config);
}
