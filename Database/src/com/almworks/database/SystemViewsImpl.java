package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.ArtifactType;
import com.almworks.database.bitmap.BitmapRootArtifactView;
import com.almworks.util.commons.Lazy;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SystemViewsImpl implements SystemViews, RootViewProvider {
  private final Basis myBasis;
  private final FilterManager myFilterManager;
  //private final ArtifactView myRootView;
  private final Map<RevisionAccess, AbstractArtifactView> myRoots = Collections15.hashMap();

  private final Lazy<ArtifactView> myUserView = new Lazy<ArtifactView>() {
    public ArtifactView instantiate() {
      return getRootView().filter(myFilterManager.not(myFilterManager.or(
        myFilterManager.attributeSet(myBasis.ATTRIBUTE.deleted, true),
        myFilterManager.attributeSet(myBasis.ATTRIBUTE.isSystemObject, true))));
    }
  };


  private static final boolean USE_BITMAP_ROOT = true;

  public SystemViewsImpl(Basis basis, FilterManager filterManager) {
    myBasis = basis;
    myFilterManager = filterManager;
    getRootView(RevisionAccess.ACCESS_DEFAULT);
  }

  public final synchronized AbstractArtifactView getRootView(RevisionAccess strategy) {
    AbstractArtifactView view = myRoots.get(strategy);
    if (view == null) {
      view =
        USE_BITMAP_ROOT ?
        (AbstractArtifactView) new BitmapRootArtifactView(myBasis, this, strategy) :
        new ScanningRootArtifactView(myBasis, this, strategy);
      myRoots.put(strategy, view);
    }
    return view;
  }

  public ArtifactView getRootView() {
    return getRootView(RevisionAccess.ACCESS_DEFAULT);
  }

  public ArtifactView getUserView() {
    return myUserView.get();
  }

  public ArtifactView getTypedView(TypedKey<ArtifactType> type) {
//    Threads.assertLongOperationsAllowed();
    return getTypedView(myBasis.getSystemObject(type));
  }

  public ArtifactView getTypedView(ArtifactPointer type) {
    return getRootView().filter(myFilterManager.attributeEquals(myBasis.ATTRIBUTE.type, type, true));
  }
}
