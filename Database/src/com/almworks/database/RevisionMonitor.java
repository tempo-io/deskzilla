package com.almworks.database;

import com.almworks.api.database.Revision;
import com.almworks.api.database.RevisionChain;
import com.almworks.api.universe.Atom;
import com.almworks.database.objects.DBUtil;
import com.almworks.database.objects.PhysicalRevisionIterator;
import com.almworks.util.commons.Condition;
import com.almworks.util.events.EventSource;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import gnu.trove.TLongLongHashMap;
import gnu.trove.TLongObjectHashMap;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;

public class RevisionMonitor {
  private final Basis myBasis;

  private final TLongLongHashMap myLastRevisionAtomMap = new TLongLongHashMap(1000, 0.75F);
  private final TLongObjectHashMap myChainSubscribersMap = new TLongObjectHashMap();

  private final Object myLock = new Object();

  public RevisionMonitor(Basis basis) {
    assert basis != null;
    myBasis = basis;
    myBasis.ourTransactionControl.addCommittedAtomsHook(new TransactionControlExt.CommittedAtomsHook() {
      public void onCommittedAtoms(TransactionExt transaction, Atom[] atoms) {
        assert atoms != null;
        if (atoms == null)
          return;
        for (Atom atom : atoms) {
          assert atom != null : atoms;
          if (atom == null)
            return;
          if (myBasis.isRevisionAtom(atom)) {
            RevisionChain physicalChain = DBUtil.getPhysicalChain(myBasis, atom);
            updateLastRevisionModel(physicalChain.getKey());
          }
        }
      }
    });
  }

  public Revision tryGetLastRevision(long chainId) {
    long head;
    synchronized (myLock) {
      head = myLastRevisionAtomMap.get(chainId);
    }
    if (head > 0) {
      return myBasis.getRevision(head, PhysicalRevisionIterator.INSTANCE);
    } else {
      return null;
    }
  }

  public Revision getOrCalculateLastRevision(long chainID) {
    long atomId = getOrCalculateLastRevisionAtomId(chainID);
    if (atomId > 0)
      return myBasis.getRevision(atomId, PhysicalRevisionIterator.INSTANCE);
    else
      return null;
  }

  public long getOrCalculateLastRevisionAtomId(long chainId) {
    while (true) {
      try {
        long head;
        synchronized (myLock) {
          head = myLastRevisionAtomMap.get(chainId);
        }
        if (head == 0) {
          Atom atom = DBUtil.getLastAtomInPhysicalChain(myBasis, chainId);
          head = atom != null ? atom.getAtomID() : -1;
          synchronized (myLock) {
            myLastRevisionAtomMap.put(chainId, head);
          }
        }
        return head;
      } catch (DatabaseInconsistentException e) {
        myBasis.ourConsistencyWrapper.handle(e, -1);
      }
    }
  }


  public ScalarModel<Revision> getLastRevisionModel(final long chainKey) {
    return new ModelFacade(chainKey);
  }

  void updateLastRevisionModel(final long chainID) {
    Subscriber subscribersHead = null;
    while (true) {
      try {
        long oldAtomId;
        synchronized (myLock) {
          oldAtomId = myLastRevisionAtomMap.get(chainID);
        }
        if (oldAtomId == 0)
          return; // nobody cares
        final int ATTEMPTS = 10;
        for (int i = 0; i < ATTEMPTS; i++) {
          Atom newAtom = DBUtil.getLastAtomInPhysicalChain(myBasis, chainID);
          if (newAtom == null)
            return;
          long newUCN = newAtom.getUCN();
          long oldUCN;
          if (oldAtomId > 0) {
            Atom oldAtom = myBasis.getAtom(oldAtomId);
            oldUCN = oldAtom.getUCN();
          } else {
            // no atom yet
            oldUCN = 0;
          }
          if (newUCN < oldUCN)
            throw new DatabaseInconsistentException(
              "last rev UCN " + oldUCN + " lowered to " + newUCN + " (" + chainID + ")");
          if (newUCN == oldUCN) {
            // same
            break;
          }
          if (newUCN > oldUCN) {
            boolean success;
            synchronized (myLock) {
              long expunged = myLastRevisionAtomMap.put(chainID, newAtom.getAtomID());
              success = expunged == oldAtomId;
              if (success) {
                subscribersHead = (Subscriber) myChainSubscribersMap.get(chainID);
              }
            }
            if (success) {
              break;
            }
          }
        }
        break;
      } catch (DatabaseInconsistentException e) {
        myBasis.ourConsistencyWrapper.handle(e, -1);
      }
    }
    if (subscribersHead != null) {
      subscribersHead.fire(chainID);
    }
  }


  private class ModelFacade extends EventSource<ScalarModel.Consumer<Revision>> implements ScalarModel<Revision> {
    private final long myChainKey;

    public ModelFacade(long chainKey) {
      myChainKey = chainKey;
    }

    public Revision getValue() {
      long head = getOrCalculateLastRevisionAtomId(myChainKey);
      if (head > 0) {
        return myBasis.getRevision(head, PhysicalRevisionIterator.INSTANCE);
      } else {
        return null;
      }
    }

    public Revision getValueBlocking() throws InterruptedException {
      throw new UnsupportedOperationException();
    }

    public Revision waitValue(Condition<Revision> condition) throws InterruptedException {
      throw new UnsupportedOperationException();
    }

    public boolean isContentKnown() {
      return true;
    }

    public void requestContent() {
    }

    public boolean isContentChangeable() {
      return true;
    }

    public EventSource<Consumer<Revision>> getEventSource() {
      return this;
    }

    public boolean addListener(Lifespan life, ThreadGate callbackGate, final Consumer<Revision> consumer) {
      if (life.isEnded())
        return false;
      getOrCalculateLastRevisionAtomId(myChainKey);
      Subscriber subscriber = new Subscriber(myChainKey, consumer, callbackGate);
      subscriber.fire(myChainKey);
      synchronized (myLock) {
        Subscriber head = (Subscriber) myChainSubscribersMap.get(myChainKey);
        if (head != null) {
          subscriber.myNext = head.myNext;
          head.myNext = subscriber;
        } else {
          myChainSubscribersMap.put(myChainKey, subscriber);
        }
      }
      life.add(new UnsubscribeDetach(myChainKey, consumer, myLock, myChainSubscribersMap));
      return true;
    }

    public void removeListener(Consumer<Revision> consumer) {
      UnsubscribeDetach.unsubscribe(myLock, myChainSubscribersMap, myChainKey, consumer);
    }

    public void addChainedSource(EventSource<Consumer<Revision>> chained) {
      throw new UnsupportedOperationException();
    }

    public void removeChainedSource(EventSource<Consumer<Revision>> chained) {
      throw new UnsupportedOperationException();
    }
  }


  private class Subscriber extends ScalarModelEvent<Revision> implements Runnable {
    private final long myChainKey;
    private final ScalarModel.Consumer<Revision> myConsumer;
    private final ThreadGate myGate;
    private Subscriber myNext;

    public Subscriber(long chainKey, ScalarModel.Consumer<Revision> consumer, ThreadGate gate) {
      super(null, null, null);
      myChainKey = chainKey;
      myConsumer = consumer;
      myGate = gate;
    }

    public void fire(long chainID) {
      if (chainID != myChainKey) {
        assert false : this;
        return;
      }
      RevisionMonitor.Subscriber next = myNext;
      long last;
      synchronized (myLock) {
        last = myLastRevisionAtomMap.get(myChainKey);
      }
      if (last == 0) {
        // should be a real value or -1
        assert false : this;
      } else if (last > 0) {
        myGate.execute(this);
        if (next != null)
          next.fire(chainID);
      }
    }

    public void run() {
      myConsumer.onScalarChanged(this);
    }

    public Revision getOldValue() {
      throw new UnsupportedOperationException();
    }

    public Revision getNewValue() {
      Revision revision = tryGetLastRevision(myChainKey);
      if (revision == null) {
        assert false : this;
      }
      return revision;
    }
  }


  private static class UnsubscribeDetach extends Detach {
    private final long myKey;
    private final ScalarModel.Consumer<Revision> myConsumer;
    private final Object myLock;
    private final TLongObjectHashMap myMap;

    public UnsubscribeDetach(long key, ScalarModel.Consumer<Revision> consumer, Object lock, TLongObjectHashMap map) {
      myKey = key;
      myConsumer = consumer;
      myLock = lock;
      myMap = map;
    }

    protected void doDetach() throws Exception {
      unsubscribe(myLock, myMap, myKey, myConsumer);
    }

    public static void unsubscribe(Object lock, TLongObjectHashMap map, long key,
      ScalarModel.Consumer<Revision> consumer)
    {
      synchronized (lock) {
        Subscriber head = (Subscriber) map.get(key);
        if (head != null) {
          if (head.myConsumer == consumer) {
            Subscriber newHead = head.myNext;
            head.myNext = null;
            if (newHead == null) {
              map.remove(key);
            } else {
              map.put(key, newHead);
            }
          } else {
            Subscriber prev = head;
            Subscriber next = head.myNext;
            while (next != null) {
              if (next.myConsumer == consumer) {
                prev.myNext = next.myNext;
                next.myNext = null;
                break;
              } else {
                prev = next;
                next = next.myNext;
              }
            }
          }
        }
      }
    }
  }
}
