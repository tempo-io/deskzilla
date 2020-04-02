package com.almworks.bugzilla.gui;

import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.UIController;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.comments.CommentListModelKey;
import com.almworks.bugzilla.provider.comments.LocalLoadedComment;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.bugzilla.provider.timetrack.BugzillaTimeTrackingCustomizer;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ValueModel;
import com.almworks.util.ui.DocumentAdapter;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;

class NewCommentController implements UIController<JTextComponent> {
  private final JCheckBox myPrivacyCheckbox;
  private final ValueModel<BigDecimal> myAddedHoursModel;
  private JTextComponent myComponent;
  private MyListener myListener;

  NewCommentController(JCheckBox privacyCheckbox, ValueModel<BigDecimal> addedHoursModel) {
    myPrivacyCheckbox = privacyCheckbox;
    myAddedHoursModel = addedHoursModel;
  }

  public void connectUI(Lifespan lifespan, final ModelMap map, JTextComponent component) {
    myComponent = component;
    
    final CommentListModelKey key = BugzillaKeys.comments;
//    assert key != null;
//    final OrderListModel<LoadedCommentKey> model = key.getModel(lifespan, map, OrderListModel.class);

    // remember new comment in holder that we create in the model and update it.
    // if user deletes text, remove comment.

    final LocalLoadedComment[] holder = {null};
    final Document document = component.getDocument();

    final BugzillaContext context = BugzillaUtil.getContext(map);
    final boolean privacyVisible = context != null && context.isCommentPrivacyAccessible();
    myPrivacyCheckbox.setVisible(privacyVisible);
    myPrivacyCheckbox.setSelected(false);

    myListener = new MyListener(document, holder, key, map);
    DocumentUtil.addListener(lifespan, document, myListener);
    UIUtil.addActionListener(lifespan, myPrivacyCheckbox, myListener);
    myAddedHoursModel.addChangeListener(lifespan, myListener);
  }

  private void update(Document document, LocalLoadedComment[] holder, CommentListModelKey key, ModelMap map) {
    final String text = getText(document);

    if (text.trim().length() == 0) {
      if(myAddedHoursModel.getValue() == null) {
        if (holder[0] != null) {
          key.removeValue(map, holder[0].getCommentKey());
          holder[0] = null;
        }
      } else {
        ThreadGate.AWT_QUEUED.execute(myListener);
      }
    } else {
      final Boolean privacy = myPrivacyCheckbox.isVisible() ? myPrivacyCheckbox.isSelected() : null;
      if (holder[0] == null) {
        final LocalLoadedComment comment = key.createNewComment(text, privacy, null, map);
        holder[0] = comment;
      } else {
        final LocalLoadedComment comment = holder[0];
        comment.setText(text);
        comment.setPrivacy(privacy);
        map.valueChanged(key);
      }
    }
  }

  private String getText(Document doc) {
    String text = "";
    final int length = doc.getLength();
    try {
      if (length > 0) {
        text = doc.getText(0, length);
      }
      assert text != null;
    } catch (BadLocationException e1) {
      // ignore
    }
    return text;
  }

  private class MyListener extends DocumentAdapter implements ActionListener, ChangeListener, Runnable {
    private final Document myDocument;
    private final LocalLoadedComment[] myHolder;
    private final CommentListModelKey myKey;
    private final ModelMap myMap;

    public MyListener(Document document, LocalLoadedComment[] holder, CommentListModelKey key, ModelMap map) {
      myDocument = document;
      myHolder = holder;
      myKey = key;
      myMap = map;
    }

    protected void documentChanged(DocumentEvent e) {
      update(myDocument, myHolder, myKey, myMap);
    }

    public void actionPerformed(ActionEvent e) {
      update(myDocument, myHolder, myKey, myMap);
    }

    public void onChange() {
      final BigDecimal hours = myAddedHoursModel.getValue();
      if(hours != null) {
        if(getText(myDocument).trim().length() == 0) {
          DocumentUtil.setDocumentText(myDocument, BugzillaTimeTrackingCustomizer.DUMMY_COMMENT);
          myComponent.selectAll();
        }
      } else {
        if(getText(myDocument).equals(BugzillaTimeTrackingCustomizer.DUMMY_COMMENT)) {
          DocumentUtil.setDocumentText(myDocument, "");
        }
      }

      final LocalLoadedComment comment = myHolder[0];
      if(comment != null) {
        comment.setWorkTime(hours);
        myMap.valueChanged(myKey);
      }
    }

    public void run() {
      onChange();
    }
  }
}
