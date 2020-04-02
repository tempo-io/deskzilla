package com.almworks.bugzilla.gui;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.ModelMap;
import com.almworks.bugzilla.provider.meta.groups.BugGroupInfo;
import com.almworks.util.Pair;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.ScrollablePanel;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.UIUtil;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

public class BugGroupsEditor implements ChangeListener, ActionListener {
  private final Lifespan myLifespan;
  private final ModelMap myModel;
  private final JPanel myPanel;
  private final ModelKey<BugGroupInfo> myKey;
  private final JScrollPane myScrollPane;
  private final Box myCheckBoxes;

  @Nullable
  private BugGroupInfo myLastGroupInfo;

  private final Map<String, Boolean> myUserChanges = Collections15.hashMap();
  private boolean myChanging;

  public BugGroupsEditor(Lifespan lifespan, ModelMap model, JPanel component, ModelKey<BugGroupInfo> key) {
    myLifespan = lifespan;
    myModel = model;
    myPanel = component;
    myKey = key;

    myPanel.setVisible(false);
    myPanel.removeAll();
    myPanel.setLayout(new BorderLayout(0, 2));
    myPanel.add(new JLabel("Groups:"), BorderLayout.NORTH);

    assert checkInsertedIntoCenter();
    myPanel.setPreferredSize(new Dimension(0, 0));

    myCheckBoxes = new Box(BoxLayout.Y_AXIS);

    ScrollablePanel scr = ScrollablePanel.create(myCheckBoxes);
    scr.setOpaque(false);
    myScrollPane = new JScrollPane(scr);
    JViewport viewport = myScrollPane.getViewport();
    Color bg = UIUtil.getEditorBackground();
    viewport.setBackground(bg);
    viewport.setOpaque(true);
    myScrollPane.setBackground(bg);
    
    myPanel.add(myScrollPane);
  }

  private boolean checkInsertedIntoCenter() {
    // myPanel should be inserted into CENTER of BorderLayout, because of its setPreferredSize
    Container container = myPanel.getParent();
    assert container != null : myPanel;
    LayoutManager layout = container.getLayout();
    assert layout instanceof BorderLayout : layout;
    Object contraints = ((BorderLayout) layout).getConstraints(myPanel);
    assert BorderLayout.CENTER.equals(contraints) : contraints;
    return true;
  }

  public void attach() {
    myModel.addAWTChangeListener(myLifespan, this);
    onChange();
  }

  public void onChange() {
    if (myChanging) {
      return;
    }
    BugGroupInfo groupInfo = myKey.getValue(myModel);
//    Set<String> newKeys = groupInfo == null ? null : groupInfo.getKeys();
//    Set<String> oldKeys = myLastGroupInfo == null ? null : myLastGroupInfo.getKeys();
    if (Util.equals(groupInfo, myLastGroupInfo))
      return;
    myLastGroupInfo = groupInfo;
    myCheckBoxes.removeAll();
    Map<String, Pair<String, Boolean>> groups = myLastGroupInfo == null ? null : myLastGroupInfo.getAll();
    if (groups == null || groups.size() == 0) {
      myPanel.setVisible(false);
    } else {
      myPanel.setVisible(true);
      addCheckboxes(groups);
    }
    Container container = myPanel.getParent();
    if (container != null) {
      container.validate();
      myPanel.repaint();
    }
  }

  private void addCheckboxes(Map<String, Pair<String, Boolean>> groups) {
    for (Map.Entry<String, Pair<String, Boolean>> entry : groups.entrySet()) {
      String groupName = entry.getValue().getFirst();
      boolean selected = Boolean.TRUE.equals(entry.getValue().getSecond());
      String groupId = entry.getKey();

      JCheckBox checkbox = new JCheckBox(groupName, selected);
      checkbox.setName(groupId);
      checkbox.addActionListener(this);
      checkbox.setOpaque(false);
      checkbox.setAlignmentX(0F);

      myCheckBoxes.add(checkbox);
    }
  }

  private void onCheckbox(JCheckBox checkBox) {
    String id = checkBox.getName();
    if (id == null) {
      assert false : checkBox;
      return;
    }
    myUserChanges.put(id, checkBox.isSelected());

    PropertyMap props = new PropertyMap();
    myKey.takeSnapshot(props, myModel);
    myKey.setValue(props, createChangedValue());

    myChanging = true;
    try {
      myKey.copyValue(myModel, props);
    } finally {
      myChanging = false;
    }
  }

  private BugGroupInfo createChangedValue() {
    BugGroupInfo copy = new BugGroupInfo(myLastGroupInfo);
    for (Map.Entry<String, Boolean> entry : myUserChanges.entrySet()) {
      copy.updateGroup(entry.getKey(), Boolean.TRUE.equals(entry.getValue()));
    }
    copy.freeze();
    return copy;
  }

  public void actionPerformed(ActionEvent e) {
    Object source = e.getSource();
    if (source instanceof JCheckBox) {
      onCheckbox((JCheckBox) source);
    }
  }
}
