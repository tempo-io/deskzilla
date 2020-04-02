package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.util.Pair;
import org.almworks.util.Collections15;

import java.lang.ref.WeakReference;
import java.util.Map;

public class ViewCachePolicy implements ViewFilteringPolicy {
  private final ArtifactView myOwner;
  private final ViewFilteringPolicy myDelegate;
  private Map<Pair<Filter, WCN.Range>, WeakReference<ArtifactView>> myChildrenCache;

  public ViewCachePolicy(ArtifactView owner, ViewFilteringPolicy delegate) {
    myOwner = owner;
    myDelegate = delegate;
  }

  public ArtifactView filter(AbstractArtifactView parent, Filter filter, WCN.Range range) {
    assert parent == myOwner;
    if (filter == Filter.ALL && range.equals(parent.getRange()))
      return parent;
    synchronized (this) {
      if (myChildrenCache == null) {
        myChildrenCache = Collections15.hashMap();
      }
      Pair<Filter, WCN.Range> pair = Pair.create(filter, range);
      WeakReference<ArtifactView> cached = myChildrenCache.get(pair);
      if (cached != null) {
        ArtifactView view = cached.get();
        if (view != null) {
          return view;
        }
      }
      ArtifactView view = myDelegate.filter(parent, filter, range);
      myChildrenCache.put(pair, new WeakReference<ArtifactView>(view));
      return view;
    }
  }
}
