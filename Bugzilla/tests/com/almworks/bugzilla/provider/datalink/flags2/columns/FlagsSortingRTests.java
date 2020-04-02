package com.almworks.bugzilla.provider.datalink.flags2.columns;

import com.almworks.api.application.NameResolver;
import com.almworks.bugzilla.integration.data.BugzillaUser;
import com.almworks.bugzilla.provider.BugzillaConnectionFixture;
import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.collections.Convertor;
import com.almworks.util.tests.CollectionsCompare;
import org.almworks.util.StringUtil;
import org.jetbrains.annotations.*;
import util.concurrent.SynchronizedBoolean;

import java.util.Collections;
import java.util.List;

import static org.almworks.util.Collections15.arrayList;

public class FlagsSortingRTests extends BugzillaConnectionFixture implements EditCommit {
  private OneTypeComparator comparator;
  private static final int typeId = 42;
  private static final BugzillaUser userId = BugzillaUser.longEmailName("user@user.com", null);
  private long myUser;
  private List<List<FlagVersion>> bugs;
  private FlagTypeItem myFlagType;

  private DBDrain myDrain;
  private final SynchronizedBoolean myTransactionFinished = new SynchronizedBoolean(false);
  private UIFlagData myUIFlagData;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myUIFlagData = new UIFlagData(metadata(), metadata().getActor(NameResolver.ROLE).getCache());
    myManager.commitEdit(this);
  }

  @Override
  public void performCommit(EditDrain drain) throws DBOperationCancelledException {
    myFlagType = createFlagType(typeId, "testType", drain);
    comparator = new OneTypeComparator(myFlagType);
    myUser = User.getOrCreate(drain, userId, privateMetadata());
    myDrain = drain;
    bugs = arrayList();
    fillBugs();
  }

  @NotNull
  private FlagTypeItem createFlagType(int typeId, String name, EditDrain drain) {
    ItemVersionCreator creator = drain.createItem();
    creator.setValue(Flags.AT_TYPE_ID, typeId);
    creator.setValue(Flags.AT_TYPE_NAME, name);
    creator.setValue(SyncAttributes.CONNECTION, myConnection.getConnectionItem());

    int flg = LoadedFlagType.setFlag(0, LoadedFlagType.FLG_TYPE_MINUS, true);
    flg = LoadedFlagType.setFlag(flg, LoadedFlagType.FLG_TYPE_PLUS, true);
    flg = LoadedFlagType.setFlag(flg, LoadedFlagType.FLG_TYPE_REQUEST, true);
    creator.setValue(Flags.AT_TYPE_FLAGS, flg);

    LoadedFlagType loaded = LoadedFlagType.load(creator);
    assertNotNull(loaded);
    FlagTypeItem flagTypeItem = FlagTypeItem.create(loaded, myUIFlagData);
    return flagTypeItem;
  }

  private void fillBugs() {
    bug("???");
    bug("??");
    bug("????+");
    bug("???++");
    bug("??+--");
    bug("????-");
    bug("??-");
    bug("--");
    bug("-");
    bug("+++---");
    bug("+++--");
    bug("++---");
    bug("+----");
    bug("++++");
    bug("+");
  }

  private void bug(String statusesEncoded) {
    bugs.add(bug(statusesEncoded.toCharArray()));
  }

  private List<FlagVersion> bug(char... statuses) {
    ItemVersionCreator bug = createEmptyBug(myDrain);
    long bugItem = bug.getItem();
    for (char s : statuses) {
      addBugFlag(bugItem, s);
    }
    return FlagVersion.load(bug, privateMetadata());
  }

  private void addBugFlag(long bug, char status) {
    ItemVersionCreator creator = myDrain.createItem();
    creator.setValue(DBAttribute.TYPE, Flags.KIND_FLAG);
    creator.setValue(Flags.AT_FLAG_MASTER, bug);
    creator.setValue(SyncAttributes.CONNECTION, myConnection.getConnectionItem());
    creator.setValue(Flags.AT_FLAG_TYPE, myFlagType.getResolvedItem());
    creator.setValue(Flags.AT_FLAG_STATUS, status);
    creator.setValue(Flags.AT_FLAG_SETTER, myUser);
  }

  @Override
  public void onCommitFinished(boolean success) {
    myTransactionFinished.set(success);
  }

  private void waitForTransaction() throws InterruptedException {
    myTransactionFinished.waitForValue(true);
  }

  public void test() throws InterruptedException {
    waitForTransaction();
    List<List<FlagVersion>> shuffled = arrayList(bugs);
    int guard = 0;
    while (bugs.equals(shuffled)) {
      Collections.shuffle(shuffled);
      ++guard;
      assertTrue(guard < 10);
    }
    print("SHUFFLED", shuffled);
    List<List<FlagVersion>> actual = arrayList(shuffled);
    Collections.sort(actual, comparator);
    assertFalse(actual.equals(shuffled));
    new CollectionsCompare().order(TO_STATUS_STRING.collectList(bugs), TO_STATUS_STRING.collectList(actual));
  }

  private static void print(String pre, List<List<FlagVersion>> flags) {
    System.out.println(pre + "\r\n======================================\r\n" +
      StringUtil.implode(TO_STATUS_STRING.collectList(flags), "\r\n"));
  }

  private static final Convertor<List<FlagVersion>, String> TO_STATUS_STRING = new Convertor<List<FlagVersion>, String>() {
    @Override
    public String convert(List<FlagVersion> flags) {
      StringBuilder sb = new StringBuilder();
      for (FlagVersion flag : flags) {
        sb.append(flag.getStatus());
      }
      return sb.toString();
    }
  };
}
