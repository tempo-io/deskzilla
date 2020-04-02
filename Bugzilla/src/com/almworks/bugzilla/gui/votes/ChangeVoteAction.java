package com.almworks.bugzilla.gui.votes;

import com.almworks.api.actions.*;
import com.almworks.api.application.*;
import com.almworks.api.application.util.ValueKey;
import com.almworks.api.edit.EditLifecycle;
import com.almworks.api.edit.WindowItemEditor;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.ItemModelRegistry;
import com.almworks.api.gui.DialogBuilder;
import com.almworks.bugzilla.provider.datalink.VotesLink;
import com.almworks.bugzilla.provider.meta.BugzillaVoteKeys;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.integers.LongArray;
import com.almworks.integers.*;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.Pair;
import com.almworks.util.Terms;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.collections.*;
import com.almworks.util.components.ASortedTable;
import com.almworks.util.components.ATable;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.models.TableColumnBuilder;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.*;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;
import java.awt.event.*;
import java.text.ParseException;
import java.util.Comparator;
import java.util.List;

import static org.almworks.util.Collections15.arrayList;

public class ChangeVoteAction extends BaseEditAction.EditInWindowAction implements ExplorerComponentActionNotOnToolbar {
  private final BugzillaVoteKeys myKeys;
  private final ValueKey<Integer> myId;
  private final MyEditor myEditor = new MyEditor();

  public ChangeVoteAction(BugzillaVoteKeys keys, ValueKey<Integer> id) {
    super("Advanced Voting\u2026", Icons.ADVANCED_VOTE_ACTION, SINGLE_ITEM);
    myKeys = keys;
    myId = id;
  }

  @Override
  protected void updateUnconditional(UpdateContext context, List<ItemWrapper> items) throws CantPerformException {
    ToggleVoteAction.checkVotingIsEnabled(items, context);
  }

  @NotNull
  @Override
  public EditorWindowCreator getWindowCreator() {
    return myEditor;
  }

  private static boolean isEqualLengthAssert(List<? extends Object> newVoters, List<? extends Object> newIntegerList) {
    boolean b = newVoters.size() == newIntegerList.size();
    if (!b) {
      String s = newVoters.size() + "!=" + newIntegerList.size();
      assert false : s;
      Log.warn(s);
    }
    return b;
  }

  private class MyEditor implements EditorWindowCreator {
    @NotNull
    @Override
    public LongList getItemsToLock(List<ItemWrapper> primaryItems, ActionContext context) throws CantPerformException {
      return LongSet.collect(UiItem.GET_ITEM, primaryItems);
    }

    @Override
    public ItemEditor createItemEditor(@NotNull LongList lockedItems, @NotNull final List<ItemWrapper> primaryItems, @NotNull DBReader reader, @NotNull EditPrepare prepare,
      @NotNull ItemModelRegistry registry, @NotNull final ActionContext context)
    {
      if (lockedItems.size() != 1) {
        Log.warn("CVA: should have locked 1 item " + lockedItems);
        return null;
      }
      final ItemUiModelImpl item = registry.createNewModel(lockedItems.get(0), reader);
      return item == null ? null : new WindowItemEditor<DialogBuilder>(prepare, WindowItemEditor.dialog(context, "changeVote")) {
        @Override
        protected void setupWindow(DialogBuilder builder, EditLifecycleImpl editLife) throws CantPerformException {
          PropertyMap map = item.getLastDBValues();

          builder.setTitle(createTitle(map, item));
          final ModelKey<Integer> ourVote = myKeys.votesMy;

          ChangeForm form = new ChangeForm();
          OkAction action = new OkAction(form.mySpinnerEditor, form.myVoteSpinner);

          builder.setOkAction(action);
          builder.setEmptyCancelAction();

          Integer value = ourVote.getValue(map);
          Integer perProduct = myKeys.productMaxVotes.getValue(map);
          Integer perBug = myKeys.productMaxVotesPerBug.getValue(map);
          form.setupMyVotes(value, perProduct, perBug);

          List<ItemKey> voters = myKeys.votesUserList.getValue(map);
          List<Integer> valueList = myKeys.votesValueList.getValue(map);
          form.setupAllVotes(voters, valueList);

          builder.setContent(form.myPanel);
          // todo :refactoring: get rid of modality
          builder.setModal(true);
          builder.setInitialFocusOwner(form.mySpinnerEditor);

          builder.addProvider(ConstProvider.singleData(ItemUiModelImpl.ROLE, item));
        }
      };
    }

    private String createTitle(PropertyMap map, ItemUiModelImpl item) {
      Integer iid = myId.getValue(map);
      String sid = iid != null ? iid.toString() : item.getItemUrl();
      if (sid == null) sid = "<New>";
      return "Vote for " + Local.parse(Terms.ref_Artifact) + " " + sid;
    }
  }

  private class OkAction implements AnAction {
    private final JSpinner mySpinner;
    private final JTextComponent mySpinnerEditor;

    public OkAction(JTextComponent spinnerEditor, JSpinner spinner) {
      mySpinnerEditor = spinnerEditor;
      mySpinner = spinner;
      watchModifiableRole(EditLifecycle.MODIFIABLE);
    }

    public void update(UpdateContext context) throws CantPerformException {
      context.updateOnChange(mySpinnerEditor.getDocument());
      int integer = Util.toInt(mySpinnerEditor.getText(), -1);
      context.setEnabled(integer >= 0);
      context.putPresentationProperty(PresentationKey.NAME, "OK");
      context.getSourceObject(EditLifecycle.ROLE).checkCommitAction();
    }

    public void perform(final ActionContext context) throws CantPerformException {
      try {
        mySpinner.commitEdit();
      } catch (ParseException e) {
        return;
      }
      Integer vote = (Integer) mySpinner.getValue();
      if (vote == null || vote < 0) {
        assert false;
        return;
      }
      ItemUiModelImpl model = ItemActionUtils.getModel(context);
      AggregatingEditCommit commit = new AggregatingEditCommit();
      SavedToLocalDBMessage.addTo(context, commit, "changeBugVote");
      commit.addProcedure(null, new CommitMyVotes(LongArray.singleton(model.getItem()), vote, true));
      context.getSourceObject(EditLifecycle.ROLE).commit(context, commit);
    }
  }


  public class ChangeForm {
    private JLabel myPerBug;
    private JLabel myPerProduct;
    private JPanel myPanel;
    private JSpinner myVoteSpinner;
    private ASortedTable myVotesTable;
    private JPanel myContainer;
    private JLabel myTableLabel;
    private SpinnerNumberModel mySpinnerModel;
    private JTextComponent mySpinnerEditor;

    private void createUIComponents() {
      myVoteSpinner = new JSpinner(new SpinnerNumberModel());
      mySpinnerModel = (SpinnerNumberModel) myVoteSpinner.getModel();
      mySpinnerEditor = ((JSpinner.DefaultEditor) myVoteSpinner.getEditor()).getTextField();

      class Listener extends FocusAdapter implements Runnable {
        @Override
        public void focusGained(FocusEvent e) {
          ThreadGate.AWT_QUEUED.execute(this);
        }
        @Override
        public void run() {
          mySpinnerEditor.selectAll();
          mySpinnerEditor.removeFocusListener(this);
        }
      }
      mySpinnerEditor.addFocusListener(new Listener());
    }

    public void setupMyVotes(Integer value, Integer perProduct, Integer perBug) {
      this.mySpinnerModel.setMinimum(0);
      this.mySpinnerModel.setStepSize(1);

      mySpinnerModel.setValue(value != null && value > 0 ? value : 0);

      int maximum = Integer.MAX_VALUE;
      if (perProduct != null && perProduct > 0) {
        myPerProduct.setText("Maximum votes for this product: " + perProduct + " ");
        maximum = perProduct;
      } else {
        myPerProduct.setVisible(false);
      }

      if (perBug != null && perBug > 0) {
        myPerBug.setText("Maximum votes for this bug: " + perBug + " ");
        maximum = Math.min(maximum, perBug);
      } else {
        myPerBug.setVisible(false);
      }

      if(maximum != Integer.MAX_VALUE) {
        mySpinnerModel.setMaximum(maximum);
      }
    }

    public void setupAllVotes(List<ItemKey> voters, List<Integer> valueList) {
      if (valueList == null || voters == null || voters.size() != valueList.size()) {
        myContainer.remove(myTableLabel);
        myContainer.remove(myVotesTable);
        FormLayout layout = (FormLayout) myContainer.getLayout();
        CellConstraints cc = layout.getConstraints(myVotesTable);
        if (cc != null) {
          layout.removeRow(cc.gridY);
          layout.removeRow(cc.gridY - 1);
        }
        return;
      }

      setupTable(voters.size());
      setVotesList(voters, valueList);
    }

    private void setupTable(int voterCount) {
      OrderListModel columns = OrderListModel.create();
      TableColumnBuilder builder = TableColumnBuilder.<Pair<ItemKey, Integer>, String>create("user", "User")
        .setConvertor(new Convertor<Pair<ItemKey, Integer>, String>() {
          public String convert(Pair<ItemKey, Integer> value) {
            return value.getFirst().getDisplayName();
          }
        })
        .setValueCanvasRenderer(Renderers.<String>canvasToString())
        .setReorderable(false)
        .setSizePolicy(ColumnSizePolicy.FREE)
        .setComparator(new Comparator<Pair<ItemKey, Integer>>() {
          public int compare(Pair<ItemKey, Integer> o1, Pair<ItemKey, Integer> o2) {
            return ItemKey.COMPARATOR.compare(o1.getFirst(), o2.getFirst());
          }
        });
      columns.addElement(builder.createColumn());

      builder = TableColumnBuilder.<Pair<ItemKey, Integer>, Integer>create("votes", "Votes")
        .setConvertor(Pair.<Integer>convertorGetSecond())
        .setValueCanvasRenderer(Renderers.<Integer>canvasToString())
        .setSizePolicy(ColumnSizePolicy.FREE)
        .setReorderable(false)
        .setComparator(new Comparator<Pair<ItemKey, Integer>>() {
          public int compare(Pair<ItemKey, Integer> o1, Pair<ItemKey, Integer> o2) {
            return -Containers.<Integer>comparablesComparator().compare(o1.getSecond(), o2.getSecond());
          }
        });
      columns.addElement(builder.createColumn());

      myVotesTable.setColumnModel(columns);
      myVotesTable.setGridHidden();
      myVotesTable.setPreferredSize(
        UIUtil.getRelativeDimension(myVotesTable, 15, Math.max(5, Math.min(15, voterCount)) + 2));
      ATable.addHeaderActions(myVotesTable.getTable());

      UIUtil.addComponentListener(Lifespan.FOREVER, myVotesTable, new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          myVotesTable.forcePreferredColumnWidths();
          TableColumnModel tcm = myVotesTable.getTable().getTableColumnModel();
          TableColumn c0 = tcm.getColumn(0);
          TableColumn c1 = tcm.getColumn(1);
          int totalWidth = c0.getWidth() + c1.getWidth();
          int minW = UIUtil.getColumnWidth(myVotesTable) * 6;
          if (c1.getWidth() < minW && minW * 3 < totalWidth) {
            int diff = minW - c1.getWidth();
            int z = c0.getWidth() - diff;
            c0.setWidth(z);
            c0.setPreferredWidth(z);
            z = c1.getWidth() + diff;
            c1.setWidth(z);
            c1.setPreferredWidth(z);
          }
        }
      });
    }

    private void setVotesList(List<ItemKey> voters, List<Integer> valueList) {
      assert voters != null && valueList != null;
      assert voters.size() == valueList.size();

      OrderListModel<Pair<ItemKey, Integer>> model = new OrderListModel<Pair<ItemKey, Integer>>();
      for (int i = 0; i < voters.size(); i++) {
        ItemKey artifactKey = voters.get(i);
        Integer integer = valueList.get(i);
        model.addElement(Pair.create(artifactKey, integer));
      }

      myVotesTable.setCollectionModel(model);
    }
  }


  static class CommitMyVotes extends EditCommit.Adapter {
    private final LongList myItems;
    private final int myVote;
    private final boolean myChangeOnlyIfMyVotesDiffer;

    public CommitMyVotes(LongList items, int vote, boolean changeOnlyIfMyVotesDiffer) {
      myItems = items;
      myVote = vote;
      myChangeOnlyIfMyVotesDiffer = changeOnlyIfMyVotesDiffer;
    }

    @Override
    public void performCommit(EditDrain drain) throws DBOperationCancelledException {
      for (LongListIterator i = myItems.iterator(); i.hasNext();) {
        long bug = i.next();
        ItemVersionCreator creator = drain.changeItem(bug);

        Integer oldMyVote = creator.getValue(VotesLink.votesByUser);
        if (!myChangeOnlyIfMyVotesDiffer || !Util.equals(oldMyVote, myVote)) {
          creator.setValue(VotesLink.votesByUser, myVote);
          Integer total = creator.getValue(VotesLink.votes);
          if (total != null) {
            int updatedTotal = total - (oldMyVote != null ? oldMyVote : 0) + myVote;
            creator.setValue(VotesLink.votes, updatedTotal);
          }
          Long me = getMe(creator);
          if (me == null) continue;
          updateVotersLists(myVote, creator, me);
        }
      }
    }

    private static Long getMe(ItemVersionCreator creator) {
      Long connection = creator.getValue(SyncAttributes.CONNECTION);
      if (connection == null) {
        Log.warn("CVA: item without connection " + creator.getItem());
        return null;
      }
      return creator.getReader().getValue(connection, Connection.USER);
    }

    private static void updateVotersLists(int vote, ItemVersionCreator bug, long me) {
      List<Long> userList = bug.getValue(VotesLink.votesUserList);
      List<Integer> valueList = bug.getValue(VotesLink.votesValueList);
      int meIndex = userList == null ? -1 : userList.indexOf(me);
      List<Long> newUserList = arrayList(userList);
      List<Integer> newValueList = arrayList(valueList);

      if (!isEqualLengthAssert(newUserList, newValueList)) return;
      if (vote != 0) {
        if (meIndex > -1) {
          newValueList.set(meIndex, vote);
        } else {
          newUserList.add(me);
          newValueList.add(vote);
        }
      } else {
        if (meIndex != -1) {
          newValueList.remove(meIndex);
          newUserList.remove(meIndex);
        }
      }
      bug.setValue(VotesLink.votesUserList, newUserList);
      bug.setValue(VotesLink.votesValueList, newValueList);
    }
  }
}
