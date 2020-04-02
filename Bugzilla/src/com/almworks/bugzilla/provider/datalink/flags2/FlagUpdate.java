package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.FrontPageData;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.integers.IntArray;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.sync.util.SyncUtils;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.util.*;

class FlagUpdate {
  private final WriteAccess myAccess;
  private final long myComponent;
  private final BugInfo myInfo;
  private final ItemVersionCreator myBug;
  private final FlagTypes myTypes;


  public FlagUpdate(ItemVersionCreator bug, long component, BugInfo info, BugzillaContext context) {
    myBug = bug;
    myAccess = WriteAccess.getInstance(bug, context);
    myComponent = component;
    myInfo = info;
    myTypes = FlagTypes.getInstance(myBug);
  }

  public void update() {
    List<BugInfo.Flag> infoFlags = myInfo.getBugFlags();
    updateTypes();
    if (infoFlags == null || infoFlags.isEmpty()) {
      SyncUtils.deleteAllSlaves(myBug, Flags.AT_FLAG_MASTER);
    } else {
      new FlagsMatch(infoFlags, LoadedFlag.loadAll(myAccess.getPrivateMD(), myBug, true)).perform();
    }
    updateTypeApplicability();
  }

  private void updateTypeApplicability() {
    List<BugInfo.Flag> list = myInfo.getBugFlags();
    if (list == null) list = Collections.emptyList();
    new TypeApplicability(list, WritableFlagTypes.getInstance(myAccess), myInfo.hasNoFlagsOnServer(), myComponent).perform();
  }

  private void updateFlag(LoadedFlag flag, BugInfo.Flag iFlag) {
    flag.update(iFlag.getSetter(), iFlag.getStatus(), iFlag.getRequestee(), iFlag.getFlagId());
  }

  private void updateTypes() {
    List<BugInfo.Flag> infoFlags = myInfo.getBugFlags();
    if (infoFlags == null) return;
    WritableFlagTypes types = WritableFlagTypes.getInstance(myAccess);
    for (BugInfo.Flag f : infoFlags) {
      if (!f.isType()) continue;
      if (f instanceof FrontPageData.FlagInfo) {
        FrontPageData.FlagInfo t = (FrontPageData.FlagInfo) f;
        types.createOrUpdate(t.getTypeId(), t.getName(), t.isAllowsRequestee(), t.getDescription(), t.getAllStatuses());
      }
    }
  }

  private class FlagsMatch {
    private final List<LoadedFlag> dbIds;
    private final List<LoadedFlag> dbNoId;
    private final List<BugInfo.Flag> sIds;
    private final List<BugInfo.Flag> sNoId;

    private FlagsMatch(List<BugInfo.Flag> serverFlags, List<LoadedFlag> dbFlags) {
      dbIds = LoadedFlag.selectWithId(dbFlags);
      dbNoId = Collections15.arrayList(dbFlags);
      dbNoId.removeAll(dbIds);
      sIds = selectFlagsWithId(serverFlags);
      sNoId = Collections15.arrayList(serverFlags);
      sNoId.removeAll(sIds);
      for (Iterator<BugInfo.Flag> it = sNoId.iterator(); it.hasNext();) {
        BugInfo.Flag flag = it.next();
        if (flag.isType()) it.remove();
      }
    }

    private List<BugInfo.Flag> selectFlagsWithId(List<BugInfo.Flag> flags) {
      List<BugInfo.Flag> result = Collections15.arrayList();
      for (BugInfo.Flag flag : flags) if (!flag.isType() && flag.getFlagId() >= 0) result.add(flag);
      return result;
    }

    public void perform() {
      // First match all flags from DB with ID set to server values. This flags can be edited by user so accurate matching required.
      explicitMatch(); // First exclude sure matching - the flags that match by id
      ignoreAlreadyDeleted(); // Do not revive flags with ID when not sure

      sureMatchByName(dbIds, sNoId); // Process by name most probable matches
      unsureMatchByName(dbIds, sNoId); // Last attempt to match by name
      ignoreAmbiguous(); // Do not sync ambiguous flags. Sync next time when more IDs available.
      assert dbIds.isEmpty();

      List<BugInfo.Flag> allServer = Collections15.arrayList();
      allServer.addAll(sIds);
      allServer.addAll(sNoId);

      sureMatchByName(dbNoId, allServer); // Match flags with same state to reduce changes
      matchByName(dbNoId); // Match flags with equal name
      assert dbNoId.isEmpty(); // All db flags are matched or deleted (and some are ignored)
      createNewServer(allServer, myBug);
    }

    private void createNewServer(List<BugInfo.Flag> allServer, ItemVersionCreator bug) {
      for (BugInfo.Flag flag : allServer) {
        LoadedFlag db = LoadedFlag.createNew(myAccess, flag.getTypeId(), flag.getName(), bug);
        updateFlag(db, flag);
      }
    }

    private void matchByName(List<LoadedFlag> db) {
      List<BugInfo.Flag> variants = Collections15.arrayList();
      for (Iterator<LoadedFlag> it = db.iterator(); it.hasNext();) {
        LoadedFlag flag = it.next();
        variants.clear();
        findByName(flag.getName(myTypes), sNoId, variants);
        if (variants.isEmpty()) {
          flag.delete();
          it.remove();
          continue;
        }
        BugInfo.Flag sFlag = variants.get(0);
        updateFlag(flag, sFlag);
        sNoId.remove(sFlag);
        it.remove();
      }
    }

    private void findByName(String name, List<BugInfo.Flag> flags, List<BugInfo.Flag> target) {
      for (BugInfo.Flag flag : flags) if (name.equals(flag.getName())) target.add(flag);
    }

    private void ignoreAmbiguous() {
      List<BugInfo.Flag> variants = Collections15.arrayList();
      for (LoadedFlag flag : dbIds) findByName(flag.getName(myTypes), sNoId, variants);
      if (variants.isEmpty()) for (LoadedFlag flag : dbIds) flag.delete();
      else {
        Log.warn("Can not match flags " + variants);
        sNoId.removeAll(variants);
      }
      dbIds.clear();
    }

    private void unsureMatchByName(List<LoadedFlag> db, List<BugInfo.Flag> server) {
      List<BugInfo.Flag> variants = Collections15.arrayList();
      for (Iterator<LoadedFlag> it = db.iterator(); it.hasNext();) {
        LoadedFlag flag = it.next();
        variants.clear();
        findByName(flag.getName(myTypes), server, variants);
        if (variants.isEmpty()) {
          flag.delete();
          it.remove();
          continue;
        }
        if (variants.size() == 1) {
          BugInfo.Flag iFlag = variants.get(0);
          updateFlag(flag, iFlag);
          it.remove();
          server.remove(iFlag);
        }
      }
    }

    private void sureMatchByName(List<LoadedFlag> db, List<BugInfo.Flag> server) {
      List<BugInfo.Flag> variants = Collections15.arrayList();
      for (Iterator<LoadedFlag> it = db.iterator(); it.hasNext();) {
        LoadedFlag flag = it.next();
        variants.clear();
        findByName(flag.getName(myTypes), server, variants);
        if (variants.isEmpty()) {
          flag.delete();
          it.remove();
          continue;
        }
        BugInfo.Flag iFlag = findByState(variants, flag);
        if (iFlag != null) {
          updateFlag(flag, iFlag);
          it.remove();
          server.remove(iFlag);
        }
      }
    }

    private BugInfo.Flag findByState(List<BugInfo.Flag> flags, LoadedFlag sample) {
      for (BugInfo.Flag flag : flags)
        if (sample.matchesState(flag.getSetter(), flag.getStatus(), flag.getRequestee())) return flag;
      return null;
    }

    private void ignoreAlreadyDeleted() {
      for (Iterator<LoadedFlag> iterator = dbIds.iterator(); iterator.hasNext();) {
        LoadedFlag flag = iterator.next();
        if (flag.isDeleted()) iterator.remove();
      }
    }

    private void explicitMatch() {
      for (Iterator<BugInfo.Flag> iterator = sIds.iterator(); iterator.hasNext();) {
        BugInfo.Flag flag = iterator.next();
        if (flag.isType()) continue;
        int id = flag.getFlagId();
        if (id < 0) continue;
        LoadedFlag loaded = LoadedFlag.findById(dbIds, id);
        if (loaded != null) {
          updateFlag(loaded, flag);
          dbIds.remove(loaded);
          iterator.remove();
        }
      }
    }
  }

  private static class TypeApplicability {
    private final List<BugInfo.Flag> myFlags;
    private final WritableFlagTypes myTypes;
    private final boolean myNoFlags;
    private final long myComponent;

    public TypeApplicability(List<BugInfo.Flag> list, WritableFlagTypes writableFlagTypes, boolean noFlags,
      long component) {
      myFlags = list;
      myTypes = writableFlagTypes;
      myNoFlags = noFlags;
      myComponent = component;
    }

    public void perform() {
      updateApplicable();
      updateInapplicable();
      updateMultiplicability();
    }

    private void updateMultiplicability() {
      if (myNoFlags) return;
      Map<Integer, FrontPageData.FlagInfo> typesById = Collections15.hashMap();
      Map<String, FrontPageData.FlagInfo> typesByName = Collections15.hashMap();
      Collection<FrontPageData.FlagInfo> flags = Collections15.arrayList();
      boolean fullDownload = false;
      for (BugInfo.Flag flag : myFlags) {
        if (!(flag instanceof FrontPageData.FlagInfo)) {
          fullDownload = false;
          break;
        }
        FrontPageData.FlagInfo info = (FrontPageData.FlagInfo) flag;
        fullDownload = true;
        if (info.isType()) {
          typesById.put(info.getTypeId(), info);
          typesByName.put(info.getName(), info);
        } else flags.add(info);
      }
      if (fullDownload) {
        for (FrontPageData.FlagInfo flag : flags) {
          int typeId = flag.getTypeId();
          if (typeId >= 0) {
            FrontPageData.FlagInfo type = typesById.get(typeId);
            setMultiplicability(typeId, type != null);
          } else {
            String name = flag.getName();
            FrontPageData.FlagInfo type = typesByName.get(name);
            List<LoadedFlagType> typesWithName = LoadedFlagType.selectWithTypeId(myTypes.findAllByName(name));
            if (type == null) {
              if (typesWithName.size() == 1) setMultiplicability(typesWithName.get(0).getTypeId(), false);
            } else if (typesByName.size() == typesById.size() && typesWithName.size() == 1 && typesWithName.get(0).getTypeId() == type.getTypeId())
              setMultiplicability(type.getTypeId(), true);
          }
        }
      }
      markDuplicatesAsMulitplicable();
    }

    private void markDuplicatesAsMulitplicable() {
      IntArray typeIds = new IntArray();
      for (BugInfo.Flag flag : myFlags) {
        int typeId = flag.getTypeId();
        if (typeId < 0) continue;
        if (typeIds.contains(typeId)) setMultiplicability(typeId, true);
        typeIds.add(typeId);
      }
    }

    private void setMultiplicability(int typeId, boolean sure) {
      LoadedFlagType type = myTypes.findByTypeId(typeId);
      if (type != null) type.updateMultiplicable(myTypes.getWriteAccess(), sure);
    }

    private void updateInapplicable() {
      List<LoadedFlagType> types = Collections15.arrayList(myTypes.getTypes());
      if (!canUpdateInapplicable()) return;
      for (BugInfo.Flag f : myFlags) {
        LoadedFlagType byTypeId = myTypes.findByTypeId(f.getTypeId());
        if (byTypeId != null) {
          for (Iterator<LoadedFlagType> it = types.iterator(); it.hasNext();) {
            LoadedFlagType type = it.next();
            if (type == byTypeId) it.remove();
            else if (type.getTypeId() <= 0 && byTypeId.getName().equals(type.getName())) it.remove();
          }
        } else {
          for (Iterator<LoadedFlagType> it = types.iterator(); it.hasNext();) {
            LoadedFlagType type = it.next();
            if (f.getName().equals(type.getName())) it.remove();
          }
        }
      }
      for (LoadedFlagType type : types) {
        type.updateComponent(myTypes.getWriteAccess(), myComponent, false);
      }
    }

    private void updateApplicable() {
      if (myNoFlags) return;
      Set<LoadedFlagType> applicables = Collections15.hashSet();
      for (BugInfo.Flag f : myFlags) {
        LoadedFlagType type = myTypes.findByTypeId(f.getTypeId());
        if (type != null) applicables.add(type);
      }
      for (LoadedFlagType type : applicables) {
        type.updateComponent(myTypes.getWriteAccess(), myComponent, true);
      }
    }

    private boolean canUpdateInapplicable() {
      for (BugInfo.Flag f : myFlags) {
        if (f instanceof FrontPageData.FlagInfo && ((FrontPageData.FlagInfo)f).getAllStatuses().length > 1) {
          return true;
        }
      }
      return myNoFlags;
    }
  }
}
