package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.ArtifactType;
import com.almworks.util.Pair;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.MapIterator;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.*;

import java.util.Collection;

class SnapshotImpl implements Snapshot {
  private final WCN myWcn;
  private final CollectionModel<Revision> myResult;
  private ArrayListCollectionModel<ArtifactType> myArtifactTypes = null;

  private static final Convertor<Pair<Revision, ArtifactPointer>, Value> VALUE_EXTRACTOR =
    new Convertor<Pair<Revision, ArtifactPointer>, Value>() {
      public Value convert(Pair<Revision, ArtifactPointer> pair) {
        return pair.getFirst().getValue(pair.getSecond());
      }
    };

  public SnapshotImpl(WCN wcn, CollectionModel<Revision> result) {
    myWcn = wcn;
    myResult = result;
  }

  public WCN getSnapshotWCN() {
    return myWcn;
  }

  public CollectionModel<Revision> getArtifacts() {
    return myResult;
  }

  public CollectionModel<ArtifactType> getArtifactTypes() {
    synchronized (this) {
      if (myArtifactTypes == null) {
        myArtifactTypes = ArrayListCollectionModel.create(false, false);
        myResult.getEventSource().addListener(ThreadGate.LONG(this), new CollectionModel.Adapter<Revision>() {
          public void onScalarsAdded(CollectionModelEvent<Revision> event) {
            Collection<ArtifactType> collection = myArtifactTypes.getWritableCollection();
            for (int i = 0; i < event.size(); i++) {
              Revision revision = event.get(i);
              ArtifactType value = revision.getValue(SystemObjects.ATTRIBUTE.TYPE);
              if (value != null && !collection.contains(value))
                collection.add(value); // todo synchronization
            }
          }

          public void onContentKnown(CollectionModelEvent<Revision> event) {
            myArtifactTypes.setContentKnown();
          }
        });
      }
    }
    return myArtifactTypes;
  }

  public SquareCollectionModel<Revision, ArtifactPointer, Value> getArtifactValues(
    CollectionModel<ArtifactPointer> attributes) {

    return ConvertingSquareCollectionModel.createLong(getArtifacts(), attributes, VALUE_EXTRACTOR, this);
  }

  public SquareCollectionModel<Revision, ArtifactPointer, Value> getArtifactValues() {
    final ArrayListCollectionModel<ArtifactPointer> attributesModel = ArrayListCollectionModel.create(true, false);
    final Collection<ArtifactPointer> attributes = attributesModel.getWritableCollection();
    getArtifacts().getEventSource().addListener(ThreadGate.LONG(this),
      new CollectionModel.Adapter<Revision>() {
        public void onScalarsAdded(CollectionModelEvent<Revision> event) {
          for (int i = 0; i < event.getScalars().length; i++) {
            Revision revision = (Revision) event.getScalars()[i];
            MapIterator<ArtifactPointer, Value> iterator = revision.getValues().iterator();
            while (iterator.next()) {
              attributes.add(iterator.lastKey());
            }
          }
        }

        public void onContentKnown(CollectionModelEvent<Revision> event) {
          attributesModel.setContentKnown();
        }
      }); // here - add thread gate
    return getArtifactValues(attributesModel);
  }
}
