package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.integration.QueryURLBuilder;
import com.almworks.bugzilla.integration.data.BugInfoMinimal;
import com.almworks.integers.IntArray;
import com.almworks.integers.*;
import com.almworks.util.Env;
import com.almworks.util.TestEnvImpl;
import com.almworks.util.collections.*;
import com.almworks.util.commons.Function;
import com.almworks.util.progress.Progress;
import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;
import gnu.trove.TLongLongHashMap;
import org.almworks.util.Failure;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifecycle;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.almworks.util.collections.Functional.*;
import static com.almworks.util.commons.Condition.cond;
import static com.almworks.util.commons.Condition.isEqual;
import static org.almworks.util.Collections15.*;

public class QueryPagingTests extends BaseTestCase {
  private static final Pattern OFFSET_PATTERN = Pattern.compile("offset=(\\d+)");
  private QueryURLBuilder myQuery;
  private final CollectionsCompare compare = new CollectionsCompare();
  
  private static final int MAX_RET_LIST_ELEMENTS = 25;

  private final List<int[]> frames = arrayList();
  private final List<List<Function<List<BugInfoMinimal>, List<BugInfoMinimal>>>> procs = arrayList();
  
  private TestEnvImpl myEnv;

  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    setWriteToStdout(true);
    myQuery = QueryURLBuilder.createDefault();
    myQuery.addProductCondition(new String[] {"Test"});
    frames.clear();
    procs.clear();
    myEnv = new TestEnvImpl();
    Env.setImpl(myEnv);
  }

  @Override
  public void tearDown() throws Exception {
    Env.setImpl(null);
    frames.clear();
    procs.clear();
    myQuery = null;
    super.tearDown();
  }

  public void testOnePage() throws ConnectorException {
    QueryPaging qp = createQueryPaging(new TestQueryLoader(new int[][] {{0, 0, 10}, {9, 9, 10}}));
    compare.order(generateBugInfoRange(0, 10), qp.loadBugs(null));
  }
  
  public void testNoBugs() throws ConnectorException {
    compare.order(Collections.EMPTY_LIST, createQueryPaging(new TestQueryLoader(arrayList(new int[]{0, 0, 0}))).loadBugs(null));
  }
  
  public void testOneBug() throws ConnectorException {
    compare.order(generateBugInfoRange(0, 1), createQueryPaging(new TestQueryLoader(arrayList(new int[]{0, 0, 1}))).loadBugs(null));
  }
  
  public void testSimplePaging() throws ConnectorException {
    Iterable<int[]> frames =
      concat(convert(IntProgression.arithmetic(0, 11, 9).toList(), new Function<Integer, int[]>() {
        @Override
        public int[] invoke(Integer offset) {
          return new int[] {offset, offset, offset + 10};
        }
      }), iterableOnce(singltonIterator(new int[] {99, 99, 100})));
    compare.order(generateBugInfoRange(0, 100), createQueryPaging(new TestQueryLoader(frames)).loadBugs(null));
  }
  
  public void testBugsDisappearOnceFromPrevPage() throws ConnectorException {
    // Imagine there're 20 bugs in the query initially, and hard limit is 10
    addFrame(0, 0, 10);
    // After frame 1 is returned, bugs 7, 8 disappear from frame 1; 10 and 11 shift to the first frame from the second
    addFrame(9, 11, 21);
    // Frame 1 is re-requested (we need to adjust it to the removal)
    addFrame(0, 0, 10);
    appendProcs(remove(7, 8), addSeq(9, 10, 11));
//    appendProcs(remove(7, 8), add(new int[][] {{8, 10}, {9, 11}}));
    // Frame 2 is re-requested
    addFrame(9, 11, 20);
    // Last element
    addFrame(17, 19, 20);
    check(remove(7, 8).invoke(generateBugInfoRange(0, 20)));
  }
  
  public void testBugsDisappearOnceFromPrevPages() throws ConnectorException {
    // Initially there are 20 bugs in the query, hard limit is 5
    addFrame(0, 0, 5);
    addFrame(4, 4, 9);
    // Bugs 1, 2 get updated and sift to the last page
    addFrame(8, 10, 15);
    // Frame 2 is re-requested
    addFrame(4, 6, 11);
    // Frame 1 is re-requested; adjust to the removals
    addFrame(0, 0, 5);
    appendProcs(remove(1, 2), addSeq(4, 5, 6));
    // Other frames are ok
    addFrame(4, 6, 11);
    addFrame(8, 10, 15);
    addFrame(12, 14, 19);
    // Adjust the last frame to the added bugs
    addFrame(16, 18, 20);
    appendProcs(addSeq(19, 1, 2));
    // Final frame
    addFrame(19, 2, 3);
    check(applyResultProcessors(generateBugInfoRange(0, 20), remove(1, 2), add(19, 1), add(1, 2)));
  }
  
  public void testFirstBugDisappears() throws ConnectorException {
    // Initially 5 bugs, hard limit 3
    addFrame(0, 0, 3);
    addFrame(2, 2, 5);
    addFrame(4, 4, 6);
    // Suddenly, bug 0 disappears
    // Whoops -- last frame returns no bugs at all
    addFrame(5, -1, -1);
    // Re-requests chain
    addFrame(4, 5, 6);
    addFrame(2, 3, 6);
    addFrame(0, 1, 4);
    // Go forward
    addFrame(2, 3, 6);
    addFrame(4, 5, 6);
    check(generateBugInfoRange(1, 6));
  }
  
  public void testAllBugsDisappear() throws ConnectorException {
    // Initially 8 bugs, hard limit is 3
    addFrame(0, 0, 3);
    addFrame(2, 2, 5);
    addFrame(4, 4, 7);
    addFrame(6, 6, 8);
    // Suddenly all bugs disappear
    addFrame(7, -1, -1);
    addFrame(6, -1, -1);
    addFrame(4, -1, -1);
    addFrame(2, -1, -1);
    addFrame(0, -1, -1);
  }
  
  public void testSameBugDifferentMtime() throws ConnectorException {
    // Initially there are 2 bugs in query, hard limit is 2
    addFrame(0, 0, 2);
    // When second frame is asked (only bug 1 is to be returned), bug 1 changes modified time
    addFrame(1, 1, 2);
    appendProcs(changeMtime(1));
    // Frame 1 is rerequested; bug 1 is with modified mtime
    addFrameKeepProcs(0, 0, 2);
    // Bug 1 is modified again
    addFrame(1, 1, 2);
    appendProcs(changeMtime(1, 1));
    // Frame 1 is re-requested
    addFrameKeepProcs(0, 0, 2);
    // Bug 1 is to be reconfirmed, and this time it does not change
    addFrameKeepProcs(1, 1, 2);
    check(applyResultProcessors(generateBugInfoRange(0, 2), changeMtime(1, 1)));
  }
  
  public void testSameBugDifferentOffsets() throws ConnectorException {
    // Initially 7 bugs, hard limit 4
    addFrame(0, 0, 4);
    // R1@#3    
    addFrame(3, 3, 7);
    // Bug 6 is changed
    addFrame(6, 6, 7);
    appendProcs(changeMtime(6));
    // R2@#3
    // Bug 6 disappears
    addFrame(3, 3, 6);
    // Bugs 3, 4, 5 disappear from the query; 13, 14 are added to the end; then 5 is added; then 16, 17 is added; then 3, 4, are added back; so we have now:
    // 0 1 2 13 14 5 16 17 3 4
    addFrame(5, 5, 6);
    appendProcs(addSeq(5, 16, 17, 3));
    // R3@#3
    addFrame(8, 3, 5);
    // Bug 4 is changed
    addFrame(9, 4, 5);
    appendProcs(changeMtime(4));
    // R4@#3
    // Bug 3 is changed
    addFrameKeepProcs(8, 3, 5);
    appendProcs(changeMtime(3));
    // Bugs 5, 16, 17 are removed
    // R5@3
    addFrameKeepProcs(5, 3, 5);
    // Bugs 13, 14 are removed
    addFrameKeepProcs(3, 3, 5);
    addFrameKeepProcs(0, 0, 4);
    addFrameKeepProcs(3, 3, 5);
    addFrameKeepProcs(4, 4, 5);    
    check(applyResultProcessors(generateBugInfoRange(0, 5), changeMtime(3, 4)));
  }
  
  public void testRandom() throws ConnectorException {
    // Warning: takes ~40s to run the last test
    int[] sizes = new int[]{5, 10, 1000, 5000/*, 1000000*/};
    int[] limits = new int[]{5, 5, 100, 500/*, 1000*/};
    Lifecycle life = new Lifecycle();
    for (int i = 0; i < sizes.length; ++i) {
      life.cycle();
      final Progress p = new Progress();
      p.getModifiable().addChangeListener(life.lifespan(), new ChangeListener() {
        double lastProgress = Double.POSITIVE_INFINITY;
        @Override
        public void onChange() {
          double progress = p.getProgress() * 100;
          if (Math.abs(progress - lastProgress) > 0.01 - 1E-6) {
            System.out.format("progress %.2f%%\n", progress);
          }
          lastProgress = progress;
        }
      });
      RandomlyModifiyingBugList ql = new RandomlyModifiyingBugList(sizes[i], limits[i]);
      List<BugInfoMinimal> loadedBugs = createQueryPaging(ql).loadBugs(p);
      assertTrue(p.isDone());
      compare.order(ql.getBugList(ql.myBugList), loadedBugs);
      System.out.println("deepest stack: " + ql.getLargestStackDepth());
      System.out.println("\n================\n");
    }
  }
  
  protected void addFrame(int... frame) {
    assertEquals(3, frame.length);
    frames.add(frame);
    if (procs != null) procs.add(new ArrayList<Function<List<BugInfoMinimal>, List<BugInfoMinimal>>>());    
  }
  
  protected void addFrameKeepProcs(int... frame) {
    addFrame(frame);
    copyLastProcs();
  }
  
  protected void appendProcs(Function... procsToAdd) {
    procs.get(procs.size() - 1).addAll((List)arrayList(procsToAdd));
  }
  
  private void copyLastProcs() {
    procs.get(procs.size() - 1).addAll(procs.get(procs.size() - 2));
  }
  
  private void check(List<BugInfoMinimal> expectedBugs) throws ConnectorException {
    compare.order(expectedBugs, loadBugs()); 
  }

  protected List<BugInfoMinimal> loadBugs() throws ConnectorException {
    return createQueryPaging(new TestQueryLoader(frames, procs)).loadBugs(null);
  }

  protected static Function<List<BugInfoMinimal>, List<BugInfoMinimal>> remove(final int... ids) {
    return new Function<List<BugInfoMinimal>, List<BugInfoMinimal>>() {
      @Override
      public List<BugInfoMinimal> invoke(List<BugInfoMinimal> l) {
        final IntArray idxsToRemove = new IntArray(ids.length);
        for (int i = 0; i < ids.length; ++i) {
          int idx = cond(compose(isEqual(ids[i]).fun(), BugInfoMinimal.EXTRACT_ID.fun())).detectIndex(l);
          if (idx >= 0) idxsToRemove.add(idx);
        }
        idxsToRemove.sort();
        for (int i = 0, iEnd = idxsToRemove.size(); i < iEnd; ++i) l.remove(idxsToRemove.get(i) - i);
        return l;
      }
    };
  }

  /**
   * Function that adds to the specified list ID after the specified ID. If ID < 0, adds to the beginning. 
   */
  protected static Function<List<BugInfoMinimal>, List<BugInfoMinimal>> add(final int idAfterWhich, final int idToAdd) {
    return new Function<List<BugInfoMinimal>, List<BugInfoMinimal>>() {
      @Override
      public List<BugInfoMinimal> invoke(List<BugInfoMinimal> l) {
        int idx = -1;
        if (idAfterWhich < 0) idx = 0;
        else {
          int idxAfterWhich = cond(compose(isEqual(idAfterWhich).fun(), BugInfoMinimal.EXTRACT_ID.fun())).detectIndex(l);
          if (idAfterWhich >= 0) idx = idxAfterWhich + 1;
        }
        if (idx >= 0) l.add(idx, BugFromId.convert(idToAdd));
        return l;
      }
    };
  }
  
  protected static Function<List<BugInfoMinimal>, List<BugInfoMinimal>> addSeq(int idAfter, int... idsToAdd) {
    Function<List<BugInfoMinimal>, List<BugInfoMinimal>> ret = I();
    int lastId = idAfter;
    for (int i : idsToAdd) {
      ret = compose(add(lastId, i), ret);
      lastId = i;
    }
    return ret;
  }

  /** Changes modification timestamps for the next returned result by appending <TT>'</TT>.  */
  protected static Function<List<BugInfoMinimal>, List<BugInfoMinimal>> changeMtime(final int... IDs) {
    return new Function<List<BugInfoMinimal>, List<BugInfoMinimal>>() {
      @Override
      public List<BugInfoMinimal> invoke(List<BugInfoMinimal> list) {
        for (int id : IDs) {
          int idx = cond(compose(isEqual(id).fun(), BugInfoMinimal.EXTRACT_ID.fun())).detectIndex(list);
          if (idx >= 0) {
            BugInfoMinimal bug = list.get(idx);
            list.set(idx, new BugInfoMinimal(bug.getStringID(), bug.getStringMTime() + '\''));
          }
        }
        return list;
      }
    };
  }
  
  private static Function<List<BugInfoMinimal>, List<BugInfoMinimal>> failFun() {
    return new Function<List<BugInfoMinimal>, List<BugInfoMinimal>>() {
      @Override
      public List<BugInfoMinimal> invoke(List<BugInfoMinimal> argument) {
        fail();
        throw new Failure();
      }
    };
  }
  
  protected QueryPaging createQueryPaging(QueryLoader ql) {
    return new QueryPaging(ql, myQuery, false);
  }

  private static int getOffset(String urlSuffix) {
    Matcher m = OFFSET_PATTERN.matcher(urlSuffix);
    return m.find() ? Integer.parseInt(m.group(1)) : 0;
  }
  
  private static final Convertor<Integer, BugInfoMinimal> BugFromId = new Convertor<Integer, BugInfoMinimal>() {
    @Override
    public BugInfoMinimal convert(Integer id) {
      return new BugInfoMinimal(String.valueOf(id), String.valueOf(id));
    }
  };
  
  /** @param rangeStart inclusive 
   * @param  rangeEnd exclusive */
  private static List<BugInfoMinimal> generateBugInfoRange(int rangeStart, int rangeEnd) {
    return arrayList(convertList(IntProgression.arithmetic(rangeStart, rangeEnd - rangeStart).toList(), BugFromId));
  }

  private static List<BugInfoMinimal> applyResultProcessors(List<BugInfoMinimal> result, Function<List<BugInfoMinimal>, List<BugInfoMinimal>>... procs) {
    return applyResultProcessors(result, arrayList(procs));
  }
  
  private static List<BugInfoMinimal> applyResultProcessors(List<BugInfoMinimal> result, Iterable<Function<List<BugInfoMinimal>, List<BugInfoMinimal>>> procs) {
    if (procs == null) return result;
    result = arrayList(result);
    for (Function<List<BugInfoMinimal>, List<BugInfoMinimal>> proc : procs) {
      result = proc.invoke(result);
    }
    return result;
  }

  private static class TestQueryLoader implements QueryLoader {
    private final Iterator<int[]> myFrames;
    private final Iterator<List<Function<List<BugInfoMinimal>, List<BugInfoMinimal>>>> myResultProcessors;
    private int[] myNextFrame;
    private List<BugInfoMinimal> myNextResult;
    private static final int OFFSET = 0;
    private static final int RANGE_START = 1;
    private static final int RANGE_END = 2;

    /** Frame is a triad consisting of:
     * <ol>
     *   <li>expected offset,</li>
     *   <li>start of the returned range -- inclusive,</li>
     *   <li>end of the returned range -- exclusive</li>      
     * </ol>
     * */
    public TestQueryLoader(Iterator<int[]> frames, Iterator<List<Function<List<BugInfoMinimal>, List<BugInfoMinimal>>>> resultProcessors) {
      myFrames = frames;
      myResultProcessors = resultProcessors;
    }

    public TestQueryLoader(Iterator<int[]> frames) {
      this(frames, null);
    }
    
    public TestQueryLoader(Iterable<int[]> frames, @Nullable Iterable<List<Function<List<BugInfoMinimal>, List<BugInfoMinimal>>>> resultProcessors) {
      this(frames.iterator(), resultProcessors == null ? null : resultProcessors.iterator());
    }

    public TestQueryLoader(Iterable<int[]> frames) {
      this(frames, null);
    }

    public TestQueryLoader(int[][] frames) {
      this(new ArrayIterator<int[]>(frames, 0, frames.length, true), null);
    }
    

    @NotNull
    @Override
    public List<BugInfoMinimal> load(@NotNull String urlSuffix, @Nullable Progress progress) throws ConnectorException {
      int[] frame = getNextFrame(urlSuffix);
      assertEquals(urlSuffix, frame[OFFSET], getOffset(urlSuffix));
      List<BugInfoMinimal> result = getOrCreateNextResult();
      if (myResultProcessors != null && myResultProcessors.hasNext()) 
        result = applyResultProcessors(result, myResultProcessors.next());
      advanceFrame();
      Log.debug("loading " + urlSuffix + "\nreturned " + result);
      return result;
    }

    private void advanceFrame() {
      myNextFrame = null;
      myNextResult = null;
    }

    private int[] getNextFrame(String messageIfNoFrames) {
      if (myNextFrame == null) {
        assertTrue(messageIfNoFrames, myFrames.hasNext());
        myNextFrame = myFrames.next();
      }
      return myNextFrame;
    }

    private List<BugInfoMinimal> getOrCreateNextResult() {
      if (myNextResult == null) {
        int[] frame = getNextFrame("");
        myNextResult = generateBugInfoRange(frame[RANGE_START], frame[RANGE_END]);
      }
      return myNextResult;
    }
  }
  
  private class RandomlyModifiyingBugList implements QueryLoader {
    private final Random r;
    private final WritableIntList myBugList;
    private int myHardLimit;
    private final TLongLongHashMap myMtimeModifications;
    private int myLargestStackDepth;

    public RandomlyModifiyingBugList(int listSize, int initialHardLimit) {
      r = getRandom();
      myBugList = IntArray.copy(IntProgression.arithmetic(0, listSize));
      myHardLimit = initialHardLimit;
      myMtimeModifications = new TLongLongHashMap();
    }

    @NotNull
    @Override
    public List<BugInfoMinimal> load(@NotNull String urlSuffix, @Nullable Progress progress) throws ConnectorException {
      Log.debug(urlSuffix);
      perturbateBugList();
      int hardLimit = getHardLimit();
      int offset = getOffset(urlSuffix);
      IntList returnedIds = offset < myBugList.size() ? myBugList.subList(offset, Math.min(offset + hardLimit, myBugList.size())) : IntList.EMPTY;
      List<BugInfoMinimal> returnedList = getBugList(returnedIds);
      logReturnedList(returnedList);
      if (progress != null) progress.setDone();
      updateStackDepth();
      return returnedList;
    }

    public List<BugInfoMinimal> getBugList(IntList returnedIds) {
      List<BugInfoMinimal> returnedList = convertList(returnedIds.toList(), BugFromId);
//      returnedList = applyMtimeModifications(returnedList);
      return returnedList;
    }

/*
    private List<BugInfoMinimal> applyMtimeModifications(final List<BugInfoMinimal> returnedList) {
      final List<BugInfoMinimal>[] ret = new List[] {null};
      myMtimeModifications.forEachEntry(new TLongLongProcedure() {
        @Override
        public boolean execute(long id, long n) {
          int idx = cond(compose(isEqual((int)id).fun(), BugInfoMinimal.EXTRACT_ID.fun())).detectIndex(returnedList);
          if (idx >= 0) {
            List<BugInfoMinimal> list = ret[0];
            if (list == null) {
              list = arrayList(returnedList);
              ret[0] = list;
            }
            BugInfoMinimal bug = list.get(idx);
            StringBuilder sb = new StringBuilder(bug.getStringMTime());
            for (int i = 0, iEnd = (int)n; i < iEnd; ++i) sb.append('\'');
            list.set(idx, new BugInfoMinimal(bug.getStringID(), sb.toString()));
          }
          return true;
        }
      });
      return ret[0] == null ? returnedList : ret[0];
    }

*/
    private void logReturnedList(List<BugInfoMinimal> returnedList) {
      Log.debug(returnedList.subList(0, Math.min(MAX_RET_LIST_ELEMENTS, returnedList.size())) +
        (returnedList.size() > MAX_RET_LIST_ELEMENTS ? " ... (total " + returnedList.size() + ')' : ""));
    }

    private void perturbateBugList() {
      int action = r.nextInt(1000);
      int base;
      if (action < (base = 900)) return;
      // Perturbation probability is ~ 0.05 (wouldn't swear on it: don't know all nuances of uniformity of the Random.nextInt() distribution)
      // Other actions have to split 100
      else if (action < (base += 40)) perturbateRemove();
      else if (action < (base += 40)) perturbateAddToEnd();
      else if (action < (base += 20)) perturbateMoveToEnd();
      else fail();
    }

    private void perturbateRemove() {
      Log.debug("remove");
      int toRemove = r.nextInt(myBugList.size() - 1) + 1;
      for (int i = 0; i < toRemove; ++i) {
        int removedId = myBugList.removeAt(r.nextInt(myBugList.size()));
        clearMtimeMod(removedId);
      }
    }

    private void perturbateAddToEnd() {
      Log.debug("add");
      DynamicIntSet existingIds = new DynamicIntSet(myBugList.size());
      existingIds.addAll(myBugList);
      int toAdd = r.nextInt(myBugList.size());
      for (int i = 0; i < toAdd; ++i) {
        int id;
        do {
          id = r.nextInt(myBugList.size() * 10);
        } while (existingIds.contains(id));
        myBugList.add(id);
        existingIds.add(id);
        clearMtimeMod(id);
      }
    }

    private void clearMtimeMod(int id) {
//      myMtimeModifications.remove(id);
    }

    private void perturbateMoveToEnd() {
      Log.debug("move");
      int size = myBugList.size();
      int toMove = r.nextInt(size);
      for (int i = 0; i < toMove; ++i) {
        int idx = r.nextInt(size);
        int id = myBugList.removeAt(idx);
        myBugList.add(id);
      }
    }

    private int getHardLimit() {
      // On a rare occasion, change hard limit (probability is 0.5^6  ~= 0.016)
      boolean changeHardLimit = true;
      for (int i = 0; i < 6; ++i) changeHardLimit &= r.nextBoolean();
      if (changeHardLimit) {
        Log.debug("Change hard limit");
        // * random in [0.5; 2)
        myHardLimit = (int)(myHardLimit*(r.nextDouble()*1.5 + 0.5));
      }
      return myHardLimit;
    }
    
    private void updateStackDepth() {
      int depth = new Throwable().getStackTrace().length;
      if (myLargestStackDepth < depth) myLargestStackDepth = depth;
    }

    public int getLargestStackDepth() {
      return myLargestStackDepth;
    }
  }
}
