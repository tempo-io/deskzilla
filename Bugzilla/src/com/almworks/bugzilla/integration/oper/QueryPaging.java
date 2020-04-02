package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.integration.QueryURL;
import com.almworks.bugzilla.integration.data.BugInfoMinimal;
import com.almworks.util.Env;
import com.almworks.util.collections.Convertor;
import com.almworks.util.progress.Progress;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.almworks.bugzilla.integration.QueryURL.Column.ID;
import static com.almworks.bugzilla.integration.QueryURL.Column.MODIFICATION_DATE;
import static com.almworks.util.collections.Functional.first;
import static com.almworks.util.collections.Functional.selectMany;
import static java.lang.Math.*;
import static org.almworks.util.Collections15.*;

/** <p>Wraps query loading into paging process. This is mainly to overcome hard limit on the number of returned search results imposed by Bugzilla 4.2.</p>
 * <p>To guarantee consistency, pages are re-requested if the expected first result is not the same. 
 * To guard from possible loops, there is a {@link #MAX_LOADS_PER_OFFSET limit} on the number of times that the same offset is requested from server.</p> */
public class QueryPaging {
  private final QueryLoader myQueryLoader;
  private final QueryURL.Changeable myQuery;
  private final boolean myOverrideSoftLimit;
  private final Map<Integer, Integer> myLoadsByOffsets = hashMap();

  public static final String MAX_LOADS_PER_OFFSET_SETTING = "bugzilla.query.paging.maxLoadsPerOffset";
  private static final int MAX_LOADS_PER_OFFSET_DEFAULT = 20;
  /** 0 and 1 mean "no rerequests allowed", or "every offset can be loaded only once" */
  static final int MAX_LOADS_PER_OFFSET = Env.getInteger(MAX_LOADS_PER_OFFSET_SETTING, 0, Integer.MAX_VALUE, MAX_LOADS_PER_OFFSET_DEFAULT);

  public QueryPaging(QueryLoader queryLoader, QueryURL.Changeable query, boolean overrideSoftLimit) {
    myQueryLoader = queryLoader;
    myQuery = query;
    myOverrideSoftLimit = overrideSoftLimit;
  }

  @NotNull
  public List<BugInfoMinimal> loadBugs(@Nullable Progress progress) throws ConnectorException {
    try {
      if (progress != null) progress.setActivity("Downloading bug lists");
      setupQuery();
      return loadPaged(progress);
    } finally {
      if (progress != null) progress.setDone();
    }
  }

  private void setupQuery() {
    myQuery.setOrderBy(MODIFICATION_DATE, ID);
    if (myOverrideSoftLimit && !myQuery.isLimitSet()) myQuery.setLimit(0);
  }
  
  /** Observations on the bound on the number of cycles.
   * <ul>
   *   <li>If page sizes are constant, page offsets are constant too. The following discussion assumes constant page sizes (= constant hard limit.)</li>
   *   <li>There are two cases in the end of each cycle:
   *    <ol>
   *      <li>"Backward": stack was popped; on the next iteration we will reload a previously loaded page because this page is out of sequence.</li>
   *      <li>"Forward": next page was pushed onto the stack.</li>
   *    </ol>
   *   </li>
   *   <li>Maximum offset is bounded by the current number of bugs in the loaded query.</li>
   *   <li>Number of cycles is bounded by {@link #MAX_LOADS_PER_OFFSET}, because each cycle happens at some offset, which is bounded by the current number of bugs;
   *    since we have a finite set of offsets visited by backward and forward pushes, the number of visits is bounded.</li>
   *   <li>Now if page size changes once, and the set of possible offsets changes, we can apply the same argument to the new situation; the new bound will be the 
   *   number of cycles already made plus the new bound.</li>
   *   <li>So, we can cycle indefinitely only if bugs are added to the query each time we load it (indefinitely long.)</li>
   * </ul> 
   * */
  private List<BugInfoMinimal> loadPaged(Progress progress) throws ConnectorException {
    Deque<Page> pages = new ArrayDeque<Page>();
    pages.push(Page.initial());
    int n = 0;
    while (!pages.isEmpty()) {
      Page p = pages.peek();
      checkLoadCountByOffset(p);
      myQuery.setOffset(p.getOffset());
      List<BugInfoMinimal> bugs = myQueryLoader.load(myQuery.getURL(), null);
      if (p.setBugs(bugs)) {
        if (bugs.size() > 1) {
          pages.push(Page.nextPage(p));
        } else {
          break;
        };
      } else {
        pages.pop();
      }
      if (progress != null) n = increaseProgress(progress, n);
    }
    int size = 0;
    for (Page p : pages) size += p.getSize();
    return arrayList(selectMany(iterableOnce(pages.descendingIterator()), Page.GET_BUGS), size);
  }

  /** We don't know in advance how much pages we will load, so we need to pick a function <tt>f(n)</tt> with the following properties:
   * <ol>
   *   <li>for any n > 0 f(n) /in (0, 1).</li>
   *   <li>For the Progress not to become "done" too soon, max f(n) = 0.999.</li>
   *   <li><tt>f(1)</tt> is quite big so that if one page is loaded (quite common scenario) it will look good.</li>
   *   <li>It grows quite fast while n < 5.</li>
   *   <li>For larger n, it should grow slowly and steadily; with n around 50 it should get noticeable increases with each n.</li>
   * </ol> 
   * The solution is to choose a mixture of two functions:
   * <ol>
   *   <li>"Starter": <tt>1 - exp(-sqrt(n/2))</tt>;</li>
   *   <li>"Stayer": <tt>1 - exp(-ln(x/2 + 1))</tt>.</li>
   * </ol>
   * To mix, <tt>tanh</tt> is used, properly shifted and scaled: <tt>(tanh(n/3-2.7) + 1) / 2</tt></br>
   * To see in action, run QueryPagingTests#testRandom; it takes 140 pages to make the increase lower than 0.01%.
   * */
  private static int increaseProgress(Progress progress, int n) {
    n = n + 1;
    double x = n;
    double starter = 1 - exp(-sqrt(x/2));
    double stayer = 1 - exp(-log(x/2 + 1));
    double mixer = (tanh(x/3 - 2.7) + 1)/2;
    double f = starter*(1 - mixer) + stayer*mixer;
    double p = Math.max(0D, f - 0.001D);
    progress.setProgress(p);
    return n;
  }

  private static class Page {
    private final int myOffset;
    @Nullable(documentation = "if first page")
    private final BugInfoMinimal myHead;
    @Nullable(documentation = "until this page is loaded")
    private List<BugInfoMinimal> myTail;
    
    public static final Convertor<Page, List<BugInfoMinimal>> GET_BUGS = new Convertor<Page, List<BugInfoMinimal>>() {
      @Override
      public List<BugInfoMinimal> convert(Page value) {
        return value.myTail;
      }
    };
    
    private Page(int offset, BugInfoMinimal head) {
      myOffset = offset;
      myHead = head;
      myTail = null;
    }
    
    public static Page initial() {
      return new Page(0, null);
    }
    
    public int getOffset() {
      return myOffset;
    }
    
    /** @return false if not in sequence */
    public boolean setBugs(List<BugInfoMinimal> newBugs) {
      if (myHead == null) {
        myTail = newBugs;
        return true;
      } else if (myHead.equals(first(newBugs))) {
        myTail = newBugs.subList(1, newBugs.size());
        return true;
      } else return false;
    }
    
    public int getSize() {
      return myTail == null ? 0 : myTail.size();
    }

    /** @param page must have > 1 bugs */
    public static Page nextPage(Page page) {
      List<BugInfoMinimal> tail = page.myTail;
      int head = page.myHead == null ? 0 : 1;
      int tailWithoutLast = tail.size() - 1;
      return new Page(page.myOffset + head + tailWithoutLast, tail.get(tail.size() - 1));
    }

    @Override
    public String toString() {
      return (myHead != null ? "#" + myHead.getStringID() : "") + '@' + myOffset;
    }
  }

  private void checkLoadCountByOffset(Page page) throws ConnectorException {
    int current = page.getOffset();
    Integer nLoads = myLoadsByOffsets.get(current);
    if (nLoads == null) {
      // one load is always allowed
      nLoads = 1;
    } else {
      nLoads += 1;
      if (nLoads > MAX_LOADS_PER_OFFSET) {
        Log.error("QP too many loads (" + nLoads + ") for " + page + ", max " + MAX_LOADS_PER_OFFSET + ' ' + myQuery.getURL());
        throwTooManyLoads();
      }
    }
    myLoadsByOffsets.put(current, nLoads);
  }

  private static ConnectorException throwTooManyLoads() throws ConnectorException {
    throw new ConnectorException(
      "Cannot load query, please try again later",
      "Cannot retrieve search results from Bugzilla",
      "Possibly there are too many concurrent changes on the server. " +
        "Please try to load the query later. " +
        "Alternatively, you could ask your Bugzilla administrator to increase the max_search_results parameter."
    );    
  }
}
