package com.almworks.tags;

import com.almworks.api.application.*;
import com.almworks.api.application.tree.TagNode;
import com.almworks.api.gui.*;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.util.Terms;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

public class TagItemsAction extends SimpleAction {
  private TagItemsForm myForm;
  private static final String SELECTED_TAG = "selectedTag";

  public TagItemsAction() {
    super("Ta&gs\u2026", Icons.TAG_DEFAULT);
    setDefaultPresentation(PresentationKey.SHORTCUT, KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK));
    watchRole(ItemWrapper.ITEM_WRAPPER);
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    context.setEnabled(false);
    List<ItemWrapper> wrappers = DBDataRoles.selectExisting(context.getSourceCollection(ItemWrapper.ITEM_WRAPPER));
    int count = wrappers.size();
    if (count == 0)
      throw new CantPerformException();
    if (count == 1)
      context.putPresentationProperty(PresentationKey.SHORT_DESCRIPTION, "Change " + Terms.ref_artifact + " tags");
    else
      context.putPresentationProperty(PresentationKey.SHORT_DESCRIPTION, "Tag/untag selected " + Terms.ref_artifacts);
    context.setEnabled(EnableState.ENABLED);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    List<ItemWrapper> wrappers = DBDataRoles.selectExisting(
      CantPerformException.ensureNotEmpty(context.getSourceCollection(ItemWrapper.ITEM_WRAPPER)));
    List<ResolvedTag> initialData = getSameTags(wrappers);
    createDialog(wrappers, initialData).showWindow();
  }

  private BasicWindowBuilder createDialog(final List<ItemWrapper> wrappers, List<ResolvedTag> commonTags) {
    DialogManager dialogManager = Context.require(DialogManager.class);
    final DialogBuilder builder = dialogManager.createBuilder("tagItems");
    ExplorerComponent explorerComponent = Context.require(ExplorerComponent.class);
    Map<DBIdentifiedObject, TagNode> tagNodes = explorerComponent.getTags();

    builder.setTitle(Local.parse(createTitle(wrappers, commonTags)));
    builder.setModal(true);
    builder.setIgnoreStoredSize(true);
    final TagItemsForm form = getForm(commonTags, tagNodes.values());
    final Configuration config = builder.getConfiguration();
    form.selectTags(config.getAllSettings(SELECTED_TAG));
    builder.setContent(form);
    builder.setInitialFocusOwner(form.getInitiallyFocused());
    builder.setEmptyCancelAction();
    form.setAcceptAction(new Runnable() {
      public void run() {
        builder.pressOk();
      }
    });
    builder.setOkAction(new EnabledAction("OK") {
      protected void doPerform(ActionContext context) throws CantPerformException {
        config.setSettings(SELECTED_TAG, form.getSelectedIds());
        form.applyTo(wrappers, context);
      }
    });

    return builder;
  }

  private String createTitle(List<ItemWrapper> wrappers, List<ResolvedTag> commonTags) {
    if (commonTags != null)
      if (wrappers.size() == 1)
        return "Tag " + Terms.ref_Artifact;
      else
        return "Tag Selected " + Terms.ref_Artifacts;
    else
      return "Change Tags for " + wrappers.size() + " Selected " + Terms.ref_Artifacts;
  }

  private TagItemsForm getForm(List<ResolvedTag> commonTags, Collection<TagNode> tagNodes) {
    if (myForm == null)
      myForm = new TagItemsForm();
    myForm.attach(commonTags, tagNodes);
    return myForm;
  }

  @Nullable
  private List<ResolvedTag> getSameTags(List<ItemWrapper> wrappers) {
    ItemWrapper wrapper = wrappers.get(0);
    List<ResolvedTag> tags = getTags(wrapper);
    for (int i = 1; i < wrappers.size(); i++) {
      List<ResolvedTag> otherTags = getTags(wrappers.get(i));
      if (!tags.equals(otherTags))
        return null;
    }
    return tags;
  }

  private List<ResolvedTag> getTags(ItemWrapper wrapper) {
    List<ResolvedTag> data = wrapper.getModelKeyValue(TagsModelKey.INSTANCE);
    List<ResolvedTag> tags = data == null ? Collections15.<ResolvedTag>emptyList() : data;
    return tags;
  }
}
