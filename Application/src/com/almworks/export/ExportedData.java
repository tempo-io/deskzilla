package com.almworks.export;

import com.almworks.api.application.*;
import com.almworks.api.application.tree.GenericNode;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.TableController;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.CantPerformException;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.*;

public class ExportedData {
  @NotNull private final List<ArtifactRecord> myRecords;
  @NotNull private final LinkedHashSet<ModelKey<?>> myKeys;
  @NotNull private final List<String> mySelectedColumnsNames;
  @Nullable private final String myCollectionName;
  @Nullable private final GenericNode myNode;
  @NotNull private final Date myDateCollected = new Date();

  public ExportedData(List<ArtifactRecord> records, LinkedHashSet<ModelKey<?>> keys, List<String> selectedColumnsNames,
    String collectionName, GenericNode node)
  {
    myRecords = records;
    myKeys = keys;
    mySelectedColumnsNames = selectedColumnsNames;
    myCollectionName = collectionName;
    myNode = node;
  }

  public static ExportedData create(TableController controller) throws CantPerformException {
    AListModel<? extends LoadedItem> model = controller.getCollectionModel();
    int size = model.getSize();
    if (size == 0)
      throw new CantPerformException();

    LinkedHashSet<ModelKey<?>> allKeys = Collections15.linkedHashSet();
    List<ArtifactRecord> allRecords = Collections15.arrayList();

    List<LoadedItem> items = Collections15.arrayList(Collections15.linkedHashSet(model.toList()));
    for (LoadedItem item : items) {
      Connection connection = item.getConnection();
      PropertyMap map = new PropertyMap(item.getValues());
      MetaInfo metaInfo = item.getMetaInfo();
      Collection<ModelKey<?>> keys = metaInfo.getKeys();
      allKeys.addAll(keys);

      // kludge: specially for custom fields key
      for (ModelKey<?> key : keys) {
        Object value = item.getModelKeyValue(key);
        if (value instanceof Collection) {
          for (Object element : ((Collection) value)) {
            if (element instanceof ModelKey) {
              allKeys.add((ModelKey<?>) element);
            } else {
              break;
            }
          }
        }
      }

      ArtifactRecord record = new ArtifactRecord(map, connection, metaInfo.getDisplayableType());
      allRecords.add(record);
    }

    @Nullable String collectionName = controller.getCollectionShortName();
    @Nullable GenericNode node = controller.getCollectionNode();

    List<String> selectedColumnsNames = Collections15.arrayList();
    List<TableColumnAccessor<LoadedItem, ?>> columns = controller.getSelectedColumns();
    for (TableColumnAccessor<LoadedItem, ?> column : columns) {
      selectedColumnsNames.add(column.getName());
    }

    return new ExportedData(allRecords, allKeys, selectedColumnsNames, collectionName, node);
  }

  public List<ArtifactRecord> getRecords() {
    return myRecords;
  }

  public LinkedHashSet<ModelKey<?>> getKeys() {
    return myKeys;
  }

  public List<String> getSelectedColumnsNames() {
    return mySelectedColumnsNames;
  }

  public String getCollectionName() {
    return myCollectionName;
  }

  public GenericNode getNode() {
    return myNode;
  }

  public Date getDateCollected() {
    return myDateCollected;
  }

  public Collection<Connection> getConnections() {
    final Collection<Connection> conns = Collections15.hashSet();
    for(final ArtifactRecord rec : myRecords) {
      conns.add(rec.getConnection());
    }
    return conns;
  }

  public static class ArtifactRecord {
    @NotNull private final PropertyMap myValues;
    @NotNull private final Connection myConnection;
    @NotNull private final String myDisplayableType;

    public ArtifactRecord(PropertyMap values, Connection connection, String displayableType) {
      myValues = values;
      myConnection = connection;
      myDisplayableType = displayableType;
    }

    public PropertyMap getValues() {
      return myValues;
    }

    public Connection getConnection() {
      return myConnection;
    }

    public String getDisplayableType() {
      return myDisplayableType;
    }
  }
}
