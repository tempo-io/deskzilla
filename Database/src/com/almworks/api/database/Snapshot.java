package com.almworks.api.database;

import com.almworks.api.database.typed.ArtifactType;
import com.almworks.util.model.CollectionModel;
import com.almworks.util.model.SquareCollectionModel;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface Snapshot {
  WCN getSnapshotWCN();

  CollectionModel<Revision> getArtifacts();

  CollectionModel<ArtifactType> getArtifactTypes();

  SquareCollectionModel<Revision, ArtifactPointer, Value> getArtifactValues(
    CollectionModel<ArtifactPointer> attributes);

  SquareCollectionModel<Revision, ArtifactPointer, Value> getArtifactValues();
}
