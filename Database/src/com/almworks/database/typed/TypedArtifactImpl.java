package com.almworks.database.typed;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.ArtifactType;
import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.database.Basis;
import com.almworks.util.cache2.LongKeyed;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;
import util.concurrent.SynchronizedBoolean;

public class TypedArtifactImpl extends LongKeyed implements TypedArtifact {
  public static final long TYPED_ARTIFACT_FAKE_KEY = Long.MAX_VALUE - 2;

  private final ArtifactPointer myDelegate;
  protected final Basis myBasis;
  private final SynchronizedBoolean myToStringLock = new SynchronizedBoolean(false);

  public TypedArtifactImpl(Basis basis, ArtifactPointer delegate) {
    super(TYPED_ARTIFACT_FAKE_KEY);
    if (basis == null)
      throw new NullPointerException("basis");
    if (delegate == null)
      throw new NullPointerException("delegate");
    myBasis = basis;
    myDelegate = delegate;
  }

  public boolean isValid(Revision revision) {
    Value type = revision.getValues().get(myBasis.ATTRIBUTE.type);
    return type != null;
  }

  public ArtifactType getType() {
    Artifact type = getUntypedType();
    if (type == null) {
      return null;
    }
    return type.getTyped(ArtifactType.class);
  }

  protected Artifact getUntypedType() {
    return getMandatoryValue(getArtifact().getLastRevision(), myBasis.ATTRIBUTE.type, Artifact.class);
  }

  public Artifact getArtifact() {
    return myDelegate.getArtifact();
  }

  /*public int hashCode() {
    return WorkspaceUtils.hashCode(this);
  }

  public boolean equals(Object obj) {
    return WorkspaceUtils.equals(this, obj);
  }
*/
  public long key() {
    return myDelegate.getArtifact().getKey();
  }

  public final String toString() {
    boolean success = myToStringLock.commit(false, true);
    if (!success)
      return "[R]" + toStringDefault();
    try {
      return toStringSafe();
    } catch (Exception e) {
      return "[E]" + toStringDefault();
    } catch (AssertionError e) {
      return "[ERR]" + toStringDefault();
    } finally {
      myToStringLock.commit(true, false);
    }
  }

  protected String toStringSafe() {
    return toStringDefault();
  }

  protected final String toStringDefault() {
    return "=" + myDelegate;
  }

  public long getPointerKey() {
    return myDelegate.getPointerKey();
  }

  @NotNull
  public <T> T getMandatoryValue(Revision revision, ArtifactPointer attribute, Class<T> expectedType) {
    assert revision != null : this;
    assert attribute != null : this;
    Value value = revision.getValue(attribute);
    if (value == null) {
      whine("is not set", attribute);
      //noinspection ConstantConditions
      return null;
    }
    T result = value.getValue(expectedType);
    if (result == null) {
      whine("has invalid value (asked " + expectedType + " from " + value.getType() + ")", attribute);
      //noinspection ConstantConditions
      return null;
    }
    return result;
  }

  private void whine(String message, ArtifactPointer attribute) {
    StringBuffer buffer = new StringBuffer();
    buffer.append(this.toString());
    buffer.append(": mandatory attribute ");
    try {
      buffer.append(attribute.getArtifact().getLastRevision().getValue(myBasis.ATTRIBUTE.name, String.class));
    } catch (Exception e) {
      buffer.append("[E][").append(attribute).append("]");
    } catch (AssertionError e) {
      buffer.append("[ERR][").append(attribute).append("]");
    }
    buffer.append(" ").append(message);
    message = buffer.toString();

    assert false : message;
    Log.warn(message, new Throwable("trace"));
  }

  protected boolean checkType(ArtifactPointer requiredType) {
    Revision revision = getArtifact().getLastRevision();
    Artifact t = getMandatoryValue(revision, myBasis.ATTRIBUTE.type, Artifact.class);
    do {
      if (WorkspaceUtils.equals(t, requiredType)) {
        return true;
      }
      t = t.getLastRevision().getValue(myBasis.ATTRIBUTE.superType, Artifact.class);
    } while (t != null && !WorkspaceUtils.equals(t, myBasis.TYPE.generic));
    return false;
  }
}
