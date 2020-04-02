package com.almworks.api.database.util;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.*;
import com.almworks.api.exec.FatalError;
import com.almworks.api.install.Setup;
import com.almworks.database.objects.remote.IllegalBaseRevisionException;
import com.almworks.util.collections.MapAdapters;
import com.almworks.util.collections.MapIterator;
import com.almworks.util.exec.Context;
import com.almworks.util.model.CollectionModel;
import com.almworks.util.model.CollectionModelEvent;
import gnu.trove.*;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class WorkspaceUtils {
  /**
   * Gets a revision from view that satisfies filter, if no such revision exists,
   * then creates one and runs initializer to set it up. Concurrent calls with same parameters
   * should produce only one object.
   */
  public static Revision singularGet(Transaction transaction, ArtifactView view, FilterManager filterManager,
    ArtifactPointer attribute, Object value, Initializer initializer)
  {

    return singularGet(transaction, ArtifactFeature.DEFAULT_ARTIFACT, view, filterManager, attribute, value,
      initializer);
  }

  public static Revision singularGet(Transaction transaction, ArtifactView view, FilterManager filterManager,
    AttributeKey<?> attribute, Object value, Initializer initializer)
  {

    return singularGet(transaction, ArtifactFeature.DEFAULT_ARTIFACT, view, filterManager, attribute, value,
      initializer);
  }

  public static Revision singularGet(Transaction transaction, ArtifactFeature[] features, ArtifactView view,
    FilterManager filterManager, ArtifactPointer attribute, Object value, Initializer initializer)
  {

    // optimization!
    view = view.filter(filterManager.attributeSet(attribute, true));
    Filter.Equals filter = filterManager.attributeEquals(attribute, value, false);
    return singularGet(transaction, features, view, filter, initializer);
  }

  public static Revision singularGet(Transaction transaction, ArtifactFeature[] features, ArtifactView view,
    FilterManager filterManager, AttributeKey<?> attribute, Object value, Initializer initializer)
  {

    // optimization!
    view = view.filter(filterManager.attributeSet(attribute, true));
    Filter.Equals filter = filterManager.attributeEquals(attribute, value, false);
    return singularGet(transaction, features, view, filter, initializer);
  }

  private static Revision singularGet(Transaction transaction, ArtifactFeature[] features, ArtifactView view,
    Filter.Equals filter, Initializer initializer)
  {
    Revision revision = view.queryLatest(filter);
    transaction.verifyViewNotChanged(view);
    if (revision != null)
      return revision;
    synchronized (transaction) {
      RevisionCreator creator = transaction.getPendingCreator(transaction, filter);
      if (creator == null) {
        creator = transaction.createArtifact(features);
        if (initializer != null)
          initializer.initialize(creator);
      }
      return creator.asRevision();
    }
  }

  /**
   * Changes an object that satisfies view:filter condition. If object does not exist, creates it.
   * Concurrent calls with same parameters should produce at most one new object.
   */
  public static RevisionCreator singularChange(Transaction transaction, ArtifactView view, FilterManager filterManager,
    ArtifactPointer attribute, Object value, Initializer initializer)
  {

    // optimization!
    view = view.filter(filterManager.attributeSet(attribute, true));
    Filter.Equals filter = filterManager.attributeEquals(attribute, value, false);
    return singularChange(view, filter, transaction, initializer);
  }

  public static RevisionCreator singularChange(Transaction transaction, ArtifactView view, FilterManager filterManager,
    AttributeKey<?> attribute, Object value, Initializer initializer)
  {

    // optimization!
    view = view.filter(filterManager.attributeSet(attribute, true));
    Filter.Equals filter = filterManager.attributeEquals(attribute, value, false);
    return singularChange(view, filter, transaction, initializer);
  }

  private static RevisionCreator singularChange(ArtifactView view, Filter.Equals filter, Transaction transaction,
    Initializer initializer)
  {

    Revision revision = view.queryLatest(filter);
    transaction.verifyViewNotChanged(view);
    synchronized (transaction) {
      RevisionCreator creator = transaction.getPendingCreator(transaction, filter);
      if (creator == null) {
        creator = revision == null ? transaction.createArtifact() : transaction.changeArtifact(revision);
      }
      if (initializer != null)
        initializer.initialize(creator);
      return creator;
    }
  }

  public static boolean equals(ArtifactPointer p1, Object any) {
    if (p1 == any)
      return true;
    else if (any instanceof ArtifactPointer)
      return equals(p1, (ArtifactPointer) any);
    else
      return false;
  }

  public static boolean equals(ArtifactPointer p1, ArtifactPointer p2) {
    if (p1 == p2)
      return true;
    if (p1 == null || p2 == null)
      return false;
    long k1 = p1.getPointerKey();
    long k2 = p2.getPointerKey();
    return k1 == k2;
  }

  public static int hashCode(ArtifactPointer p) {
    return p == null ? 0 : (int) p.getPointerKey();
  }

  private static boolean checkEqualsClasses(Object k1, Object k2) {
    if (k1 == null || k2 == null)
      return true;
    if (k1.getClass().equals(k2.getClass()))
      return true;
    Log.warn("comparing two pointer keys with different types: " + k1.getClass() + " and " + k2.getClass());
    return false;
  }


  public static boolean contains(Collection<? extends ArtifactPointer> collection, ArtifactPointer pointer) {
    for (Iterator<? extends ArtifactPointer> iterator = collection.iterator(); iterator.hasNext();) {
      ArtifactPointer artifactPointer = iterator.next();
      if (equals(pointer, artifactPointer))
        return true;
    }
    return false;
  }

  /**
   * Remove artifactPointer from pointers' collection
   *
   * @return pointer, if removed
   */
  @Nullable
  public static ArtifactPointer remove(Collection<? extends ArtifactPointer> collection, ArtifactPointer pointer) {
    for (Iterator<? extends ArtifactPointer> iterator = collection.iterator(); iterator.hasNext();) {
      ArtifactPointer artifactPointer = iterator.next();
      if (equals(pointer, artifactPointer)) {
        iterator.remove();
        return artifactPointer;
      }
    }
    return null;
  }

  /**
   * Removes from collection all pointers contained in subset.<br>
   * Equal to collection.removeAll(subset);
   * @see java.util.Collection#removeAll(java.util.Collection)
   */
  public static boolean removeAll(Collection<? extends ArtifactPointer> collection, Collection<? extends ArtifactPointer> subset) {
    boolean changed = false;
    for (Iterator<? extends ArtifactPointer> iterator = collection.iterator(); iterator.hasNext();) {
      ArtifactPointer pointer = iterator.next();
      if (contains(subset, pointer)) {
        iterator.remove();
        changed = true;
      }
    }
    return changed;
  }

  public static void include(Collection<ArtifactPointer> collection, ArtifactPointer pointer) {
    for (Iterator<? extends ArtifactPointer> iterator = collection.iterator(); iterator.hasNext();) {
      ArtifactPointer artifactPointer = iterator.next();
      if (equals(pointer, artifactPointer)) {
        return;
      }
    }
    collection.add(pointer);
  }

  public static boolean containsAll(Collection<? extends ArtifactPointer> container,
    Collection<? extends ArtifactPointer> subCollection)
  {
    for (Iterator<? extends ArtifactPointer> iterator = subCollection.iterator(); iterator.hasNext();) {
      ArtifactPointer artifactPointer = iterator.next();
      if (!contains(container, artifactPointer))
        return false;
    }
    return true;
  }

  public static String outputValue(Value value) {
    Revision objectValue = value.getValue(Revision.class);
    if (objectValue != null)
      return outputRevision(objectValue);
    String stringValue = value.getValue(String.class);
    if (stringValue != null)
      return stringValue;
    return value.toString();
  }

  public static String outputRevision(Revision revision) {
    StringBuffer buffer = new StringBuffer();
    ArtifactType type = revision.getValue(SystemObjects.ATTRIBUTE.TYPE);
    if (type != null) {
      String typeName = type.getArtifact().getLastRevision().getValue(SystemObjects.ATTRIBUTE.NAME);
      if (typeName != null)
        buffer.append(typeName).append(' ');
    }
    String name = revision.getValue(SystemObjects.ATTRIBUTE.NAME);
    if (name == null)
      buffer.append("<unnamed>");
    else
      buffer.append('"').append(name).append('"');

    if (type == null && name == null)
      buffer.append("[").append(revision.toString()).append("]");

    buffer.append(" [WCN").append(revision.getWCN()).append("]");
    return buffer.toString();
  }

  public static void outputView(ArtifactView view, WCN earliestObjectWcn) {
    ArtifactView filteredView = view.filter(new RangeFilter(WCN.createRange(earliestObjectWcn, WCN.LATEST)));
    filteredView.takeSnapshot()
      .getArtifacts()
      .getEventSource()
      .addStraightListener(new CollectionModel.Adapter<Revision>() {
        public void onScalarsAdded(CollectionModelEvent<Revision> event) {
          for (int i = 0; i < event.getScalars().length; i++) {
            Revision revision = (Revision) event.getScalars()[i];
            System.out.println(outputRevision(revision));
            MapIterator<ArtifactPointer, Value> values = revision.getValues().iterator();
            while (values.next()) {
              System.out
                .println("    " + outputRevision(values.lastKey().getArtifact().getLastRevision()) + " => " +
                  outputValue(values.lastValue()));
            }
          }
        }
      });
  }

  public static void outputView(ArtifactView view) {
    outputView(view, WCN.EARLIEST);
  }

  public static Map<ArtifactPointer, Value> diff(Revision from, Revision to) {
    Map<ArtifactPointer, Value> result = Collections15.hashMap();
    Map<ArtifactPointer, Value> toMap = MapAdapters.getHashMap(to.getValues());
    MapIterator<ArtifactPointer, Value> ii = from.getValues().iterator();
    while (ii.next()) {
      ArtifactPointer attribute = ii.lastKey();
      Value fromValue = ii.lastValue();
      Value toValue = toMap.remove(attribute);
      if (!Util.equals(fromValue, toValue))
        result.put(attribute, toValue);
    }
    result.putAll(toMap);
    return result;
  }

  public static boolean repeatUntilNoCollisions(int maximumTries, DatabaseRunnable runnable) {
    return repeatUntilNoCollisions(maximumTries, runnable, null);
  }
  
  public static boolean repeatUntilNoCollisions(int maximumTries, DatabaseRunnable runnable, @Nullable Runnable cacheCleanupOnCollision) {
    int attempt = 0;
    while (true) {
      if (maximumTries > 0 && attempt++ >= maximumTries)
        return false;
      Exception collision = null;
      try {
        runnable.run();
        return true;
      } catch (CollisionException e) {
        collision = e;
      } catch (UnsafeCollisionException e) {
        collision = e;
      }
      Log.debug("DB collision, retrying", collision);
      if (cacheCleanupOnCollision != null) {
        try {
          cacheCleanupOnCollision.run();
        } catch (Exception e) {
          Log.error(e);
        } catch (Error e) {
          if (e instanceof ThreadDeath) throw e;
          Log.error(e);
        }
      }
    }
  }

  public static void deleteRCB(final Artifact artifact, final Workspace workspace) {
    assert artifact != null;
    assert workspace != null;
    if (artifact == null || workspace == null)
      return;
    RCBArtifact rcb = artifact.getRCBExtension(true);
    assert rcb != null : artifact;
    if (rcb == null)
      return;
    if (rcb.hasOpenLocalBranch()) {
      deleteArtifact(artifact, workspace, RevisionAccess.ACCESS_LOCAL);
      //rcb.closeLocalChain(workspace); - do not close because there will be a lot of deleted revision
    }
    deleteArtifact(artifact, workspace, RevisionAccess.ACCESS_MAINCHAIN);
  }

  public static void deleteArtifact(final Artifact artifact, final Workspace workspace, final RevisionAccess access) {
    repeatUntilNoCollisions(5, new DatabaseRunnable() {
      public void run() throws CollisionException {
        Transaction t = workspace.beginTransaction();
        RevisionCreator creator = t.changeArtifact(artifact, access);
        if (!creator.asRevision().isDeleted()) {
          creator.deleteObject();
          t.commit();
        } else {
          t.rollback();
        }
      }
    });
  }

  public static boolean checkNoDeleted(ArtifactPointer[] value) {
    if (value == null)
      return true;
    for (ArtifactPointer artifactPointer : value) {
      Artifact artifact = artifactPointer.getArtifact();
      if (!isValid(artifact))
        continue;
      Revision revision = artifact.getLastRevision();
      if (revision.isDeleted()) {
        assert false : revision.getImage().getData();
        return false;
      }
    }
    return true;
  }

  public static boolean isValid(@Nullable ArtifactPointer pointer) {
    if (pointer == null)
      return false;
    try {
      Context.require(Workspace.class).getArtifactByKey(pointer.getPointerKey());
    } catch (InvalidItemKeyException e) {
      return false;
    }
    return pointer.getArtifact().isValid();
  }

  public static void addManualChanges(Revision localRevision, Map<ArtifactPointer, Value> changes) {
    Artifact[] value = localRevision.getValue(SystemObjects.ATTRIBUTE.MANUALLY_CHANGED_ATTRIBUTES);
    if (value != null && value.length > 0)
      for (Artifact attribute : value)
        changes.put(attribute, localRevision.getValue(attribute));
  }

  public static Map<ArtifactPointer, Value> getManualChanges(Revision localRevision) {
    RCBArtifact rcb = localRevision.getArtifact().getRCBExtension(true);
    if (rcb == null)
      return Collections15.emptyMap();
    Map<ArtifactPointer, Value> changes = Collections15.hashMap(rcb.getLocalChanges(localRevision));
    addManualChanges(localRevision, changes);
    return changes;
  }

  public static Revision getRevision(ArtifactPointer pointer) {
    Revision r;
    if (pointer instanceof Revision)
      r = (Revision) pointer;
    else
      r = pointer.getArtifact().getLastRevision();
    return r;
  }

  public static Throwable onFatalDatabaseProblem(Throwable exception) {
    String error = "<html><body>" +
      "Application has encountered a fatal problem with the local database and must terminate.<br>" +
      "Please verify that workspace directory is available and writable, and that there's free<br>" +
      "space on your hard drive. If workspace is located on a network drive, there might be a <br>" +
      "problem with network.<br>" + "<br>" + "Workspace directory: " + Setup.getWorkspaceDir().getAbsolutePath() +
      "<br>";
    if (exception != null)
      error += "Problem: " + exception.getMessage();
    FatalError.terminate("Fatal Local Database Problem", error, exception);
    // should not proceed here
    return exception;
  }

  /**
   * @return -1 if not exists
   */
  public static int indexOf(ArtifactPointer what, Artifact[] list) {
    return indexOf(what, list, 0, list.length);
  }

  public static int indexOf(ArtifactPointer what, ArtifactPointer[] array, int offset, int length) {
    for (int i = offset; i < offset + length; i++) {
      ArtifactPointer pointer = array[i];
      if (equals(what, pointer)) return i;
    }
    return -1;
  }

  public static int compare(ArtifactPointer a1, ArtifactPointer a2) {
    if (a1 == null) return a2 == null ? 0 : -1;
    if (a2 == null) return 1;
    return Util.compareLongs(a1.getPointerKey(), a2.getPointerKey());
  }

  public static <T extends TypedArtifact> Collection<T> collectTyped(Collection<? extends ArtifactPointer> pointers, Class<? extends T> type) {
    if (pointers.isEmpty())
      return Collections15.emptyCollection();
    List<T> result = Collections15.arrayList();
    for (ArtifactPointer pointer : pointers) {
      result.add(pointer.getArtifact().getTyped(type));
    }
    return result;
  }

  public static long keyOrZero(ArtifactPointer pointer) {
    return pointer == null ? 0 : pointer.getPointerKey();
  }

  public static <A extends ArtifactPointer> void addAllNotContained(Collection<A> dest, Collection<? extends A> src) {
    for (A pointer : src) if (!contains(dest, pointer)) dest.add(pointer);
  }

  /**
   * Returns set theoretic difference (members in a and not in b).
   */
  public static <A extends ArtifactPointer> List<A> complement(Collection<? extends A> a, Collection<? extends ArtifactPointer> b) {
    List<A> result = Collections15.arrayList(a);
    if (b != null) removeAll(result, b);
    return result;
  }
  

  public static void retainAll(Collection<? extends ArtifactPointer> target, Collection<? extends ArtifactPointer> subset) {
    for (Iterator<? extends ArtifactPointer> iterator = target.iterator(); iterator.hasNext();) {
      ArtifactPointer pointer = iterator.next();
      if (!contains(subset, pointer)) iterator.remove();
    }
  }

  public static List<ArtifactPointer> intersection(Collection<? extends ArtifactPointer> set1, Collection<? extends ArtifactPointer> set2) {
    List<ArtifactPointer> result = null;
    for (ArtifactPointer pointer : set1) {
      if (!contains(set2, pointer)) continue;
      if (result == null) result = Collections15.arrayList(4);
      result.add(pointer);
    }
    return result;
  }
  

  public static RevisionCreator changeArtifactBasedOnRevisionWithFailover(Transaction t, Artifact a,RevisionAccess access, @Nullable Revision base) {
    if (base != null) {
      try {
        return t.changeArtifact(a, access, base);
      } catch (IllegalBaseRevisionException e) {
        Log.warn("failover to normal change", e);
      }
    }
    return t.changeArtifact(a, access);
  }

  @Nullable
  public static ArtifactPointer[] toReferenceArray(@Nullable Collection<? extends ArtifactPointer> collection) {
    if (collection == null || collection.isEmpty()) return null;
    ArtifactPointer[] array = new ArtifactPointer[collection.size()];
    int index = 0;
    for (ArtifactPointer pointer : collection) {
      if (pointer != null) {
        array[index] = pointer;
        index++;
      }
    }
    if (index != array.length) array = ArrayUtil.arrayCopy(array, 0, index);
    if (array != null)
      Arrays.sort(array, ArtifactPointer.KEY_COMPARATOR);
    return array;
  }

  public static ArtifactPointer[] toUniqueReferenceArray(@Nullable Collection<? extends ArtifactPointer> collection) {
    ArtifactPointer[] array = toReferenceArray(collection);
    if (array != null) {
      ArtifactPointer prev = null;
      List<ArtifactPointer> copy = null;
      for (int i = 0; i < array.length; i++) {
        ArtifactPointer pointer = array[i];
        if (equals(pointer, prev)) {
          if (copy == null) {
            copy = Collections15.arrayList();
            copy.addAll(Arrays.asList(array).subList(0, i));
          }
        } else if (copy != null) copy.add(pointer);
        prev = pointer;
      }
      if (copy != null) array = copy.toArray(new ArtifactPointer[copy.size()]);
    }
    return array;
  }

  public static Artifact[] toArtifactSet(ArtifactPointer[] value) {
    if (value == null || value.length == 0) return Artifact.EMPTY_ARRAY;
    Artifact[] artifacts = new Artifact[value.length];
    for (int i = 0; i < value.length; i++) {
      ArtifactPointer pointer = value[i];
      artifacts[i] = pointer.getArtifact();
    }
    Arrays.sort(artifacts, ArtifactPointer.KEY_COMPARATOR);
    int newLength = ArrayUtil.removeSubsequentDuplicates(artifacts, 0, artifacts.length);
    if (newLength != artifacts.length) {
      artifacts = ArrayUtil.arrayCopy(artifacts, 0, newLength);
    }
    return artifacts;
  }

  public static Artifact[] getArtifactSet(Revision revision, ArtifactPointer attribute) {
    return toArtifactSet(revision.getValue(attribute, ArtifactPointer[].class));
  }

  /**
   * @return a hash set that uses {@link #hashCode(ArtifactPointer)} and {@link #equals(ArtifactPointer, ArtifactPointer)}
   */
  public static Set<ArtifactPointer> artifactHashSet() {
    return new ArtifactHashSet();
  }

  public static Set<ArtifactPointer> artifactHashSet(ArtifactPointer... src) {
    if (src == null)
      return artifactHashSet();
    Set<ArtifactPointer> set = new ArtifactHashSet(src.length);
    for (ArtifactPointer t : src)
      set.add(t);
    return set;
  }

  public static Set<ArtifactPointer> artifactHashSet(@Nullable Collection<? extends ArtifactPointer> src) {
    Set<ArtifactPointer> set = artifactHashSet();
    if (src != null) set.addAll(src);
    return set;
  }

  private static class ArtifactHashSet implements Set<ArtifactPointer>  {
    private static final TObjectHashingStrategy HASHING_STRATEGY = new TObjectHashingStrategy() {
      @Override
      public int computeHashCode(Object o) {
        if (!(o instanceof ArtifactPointer)) return 0;
        return WorkspaceUtils.hashCode((ArtifactPointer)o);
      }

      @Override
      public boolean equals(Object o1, Object o2) {
        if (!(o1 instanceof ArtifactPointer) || !(o2 instanceof ArtifactPointer)) return false;
        return WorkspaceUtils.equals((ArtifactPointer)o1, (ArtifactPointer)o2);
      }
    };
    private static final Object VALUE = new Object();

    private final THashMap myMap;

    public ArtifactHashSet() {
      myMap = new THashMap(HASHING_STRATEGY);
    }

    public ArtifactHashSet(int initialCapacity) {
      myMap = new THashMap(initialCapacity, HASHING_STRATEGY);
    }

    @Override
    public int size() {
      return myMap.size();
    }

    @Override
    public boolean isEmpty() {
      return myMap.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
      if (!(o instanceof ArtifactPointer)) return false;
      return myMap.containsKey(o);
    }

    @Override
    public Iterator<ArtifactPointer> iterator() {
      return new Iterator<ArtifactPointer>() {
        Iterator it = myMap.keySet().iterator();
        @Override
        public boolean hasNext() {
          return it.hasNext();
        }

        @Override
        public ArtifactPointer next() {
          return (ArtifactPointer)it.next();
        }

        @Override
        public void remove() {
          it.remove();
        }
      };
    }

    @Override
    public Object[] toArray() {
      return myMap.keySet().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return (T[])myMap.keySet().toArray(a);
    }

    @Override
    public boolean add(ArtifactPointer pointer) {
      return myMap.put(pointer, VALUE) == null;
    }

    @Override
    public boolean remove(Object o) {
      return myMap.remove(o) == VALUE;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      return myMap.keySet().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends ArtifactPointer> c) {
      boolean changed = false;
      for (ArtifactPointer p : c) {
        changed |= add(p);
      }
      return changed;
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
      return myMap.retainEntries(new TObjectObjectProcedure() {
        @Override
        public boolean execute(Object key, Object value) {
          return c.contains(key);
        }
      });
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
      return myMap.retainEntries(new TObjectObjectProcedure() {
        @Override
        public boolean execute(Object key, Object value) {
          return !c.contains(key);
        }
      });
    }

    @Override
    public void clear() {
      myMap.clear();
    }
  }
}