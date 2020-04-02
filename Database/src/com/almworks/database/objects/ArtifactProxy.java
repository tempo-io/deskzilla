package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.api.universe.Atom;
import com.almworks.database.Basis;
import com.almworks.util.cache2.LongKeyedProxy;
import com.almworks.util.cache2.NoCache;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.InstanceProvider;
import com.almworks.util.threads.Threads;
import org.almworks.util.Log;
import org.almworks.util.TypedKey;

import java.util.Map;

public class ArtifactProxy extends LongKeyedProxy<AbstractArtifactImpl> implements Artifact {
  private final Basis myBasis;

  public ArtifactProxy(long atomKey, Basis basis) {
    super(atomKey);
    myBasis = basis;
  }

  public NoCache<AbstractArtifactImpl> getCache() {
    return myBasis.getArtifactsCache();
  }

  public RevisionChain getChain(RevisionAccess strategy) {
    return delegate().getChain(strategy);
  }

  public Revision getFirstRevision() {
    return getChain(RevisionAccess.ACCESS_DEFAULT).getFirstRevision();
  }

  public long getKey() {
    return key();
  }

  public Revision getLastRevision(WCN wcn, RevisionAccess strategy) {
    Threads.assertLongOperationsAllowed();
    return getChain(strategy).getLastRevision(wcn);
  }

  public Revision getLastRevision(RevisionAccess strategy) {
    return getLastRevision(WCN.LATEST, strategy);
  }

  public Revision getLastRevision(WCN wcn) {
    return getLastRevision(wcn, RevisionAccess.ACCESS_DEFAULT);
  }

  public Revision getLastRevision() {
    return getLastRevision(WCN.LATEST, RevisionAccess.ACCESS_DEFAULT);
  }

  public <T extends TypedArtifact> T getTyped(Class<T> typedClass) {
    Threads.assertLongOperationsAllowed();
    TypedArtifact object = myBasis.getTypedObject(this);
    if (object == null)
      return null;
    if (typedClass.isAssignableFrom(object.getClass()))
      return (T) object;
    else
      return null;
  }

  public WCN getWCN() {
    return getFirstRevision().getWCN();
  }

  public boolean isAccessStrategySupported(RevisionAccess strategy) {
    // this would be a bottleneck
    //return delegate().isAccessStrategySupported(strategy);

    Boolean isRCB = myBasis.isRCBArtifactAtom(myKey);
    if (isRCB == null) {
      Log.warn(this + " is not artifact");
      return false;
    }

    if (isRCB.booleanValue()) {
      // supports all access methods
      return true;
    } else {
      // normal artifact supports only default strategy
      return strategy == RevisionAccess.ACCESS_DEFAULT;
    }
  }

  public boolean containsRevision(Revision revision) {
    AbstractArtifactImpl d = delegate();
    return d == null ? false : d.containsRevision(revision);
  }

  public boolean isValid() {
    InstanceProvider<Class<? extends RuntimeException>> exception =
      InstanceProvider.instance(ValidatingInconsistentAtomException.class, Basis.DATABASE_INCONSISTENCY_EXCEPTION);
    Context.add(exception, "VIAE");
    try {
      AbstractArtifactImpl impl = delegate();
      return impl != null && impl.isValid();
    } catch (ValidatingInconsistentAtomException e) {
      // see Basis - atom could not be loaded
      return false;
    } finally {
      Context.pop();
    }
  }

  public boolean isAccessibleIn(Transaction t) {
    Atom keyAtom = myBasis.ourUniverse.getAtom(getKey());
    if (keyAtom != null) return true;
    if (t != null) {
      return t.isChanging(this);
    }
    return false;
  }

  public Artifact getArtifact() {
    return this;
  }

  public long getPointerKey() {
    return getKey();
  }

  public <K> K getAspect(TypedKey<K> key) {
    return myBasis.ourAspectManager.getAspect(this, key);
  }

  public Map<TypedKey, ?> copyAspects() {
    return myBasis.ourAspectManager.copyAspects(this);
  }

  public RCBArtifact getRCBExtension(boolean assumeRcb) {
    // todo kludge - guarding against proxies created on "fake" caches, which may lead to duplicating RCBArtifact
    // was: Object artifact = delegate();
    Object artifact = myBasis.getArtifact(myKey).delegate();
    if (assumeRcb) {
      try {
        return (RCBArtifact) artifact;
      } catch (ClassCastException e) {
        return null;
      }
    } else {
      if (artifact instanceof RCBArtifact)
        return (RCBArtifact) artifact;
      else
        return null;
    }
  }

  public boolean hasRCBExtension() {
    Object artifact = myBasis.getArtifact(myKey).delegate();
    return artifact instanceof RCBArtifact;
  }
}
