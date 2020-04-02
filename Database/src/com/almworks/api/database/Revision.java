package com.almworks.api.database;

import com.almworks.api.database.typed.Attribute;
import com.almworks.api.database.typed.AttributeKey;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.MapSource;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.Map;

public interface Revision extends Identifiable, Aspected, ArtifactPointer {
  Revision[] EMPTY_REVISION_ARRAY = {};

  Convertor<Revision, Artifact> GET_ARTIFACT = new Convertor<Revision, Artifact>() {
    public Artifact convert(Revision revision) {
      return revision.getArtifact();
    }
  };

  Artifact getArtifact();

  RevisionChain getChain();

  Map<ArtifactPointer, Value> getChanges();

  RevisionImage getImage();

  long getKey();

  Revision getPrevRevision();

  @Nullable
  <T> T getValue(ArtifactPointer attribute, Class<T> valueClass);

  <T> T getValue(TypedKey<Attribute> systemAttribute, Class<T> valueClass);

  Value getValue(ArtifactPointer attribute);

  Value getValue(TypedKey<Attribute> systemAttribute);

  <T> T getValue(AttributeKey<T> systemAttribute);

  MapSource<ArtifactPointer, Value> getValues();

  WCN getWCN();

  /**
   * Returns order of revision to compare with other revisions of this artifact.
   * Revision with greater order is guaranteed to be created later than revisions with less orders.
   *
   * NB: It is safe to compare order of only revisions that are received for the same revision chain!
   *
   * @return comparable long representing revision order in the artifact (chain), or -1 if order is
   * unavailable or revision does not belong to an artifact
   */
  long getOrder();

  boolean isDeleted();

  /**
   * Checks if the revision can be used.
   * <p>
   * Returns true if the revision has been committed to the database. If transaction argument is specified,
   * also returns true if the revision has not yet been committed, but is changed in the transaction t (so you
   * can use it within the same transaction).
   */
  boolean isAccessibleIn(@Nullable Transaction t);
}

