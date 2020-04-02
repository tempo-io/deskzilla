package com.almworks.items.sync.impl;

import com.almworks.items.api.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.util.*;
import com.almworks.util.collections.ByteArray;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.List;

public class SyncSchema {
  public static final DBNamespace NS = DBNamespace.moduleNs("com.almworks.itemSync");
  /**
   * Base shadow. The server side values which are the past of last user changes<br>
   * Exist iff the item is locally modified (locally created new items are locally modified).
   */
  public static final DBAttribute<AttributeMap> BASE = SyncAttributes.BASE_SHADOW;
  /**
   * Conflict shadow. The server side values which are conflicts with last local changes. {@link #BASE} is past of this shadow.<br>
   * Exists iff the item is locally modified and concurrent edit on server conflicts with local changes. The item is in
   * conflict state.
   */
  public static final DBAttribute<AttributeMap> CONFLICT = SyncAttributes.CONFLICT_SHADOW;
  /**
   * Download shadow. The last known server side values which aren't processed yet. This shadow exists until automerge copy it
   * to some other ({@link #BASE} or {@link #CONFLICT} shadow or to TRUNK.
   */
  public static final DBAttribute<AttributeMap> DOWNLOAD = SyncAttributes.Shadow(NS.attr("download"), "Download Shadow");
  /**
   * Upload task shadow. The local values which are requested for upload.<br>
   * This shadow exists since upload is started and until it is completely done (including automerge). This shadow is past of TRUNK
   */
  public static final DBAttribute<AttributeMap> UPLOAD_TASK = SyncAttributes.Shadow(NS.attr("uploadTask"), "Upload-Task Shadow");
  /**
   * Actual successful upload. The local values which are actually uploaded to server.<br>
   * The {@link #BASE} is past of this shadow and {@link #UPLOAD_TASK} is the future. <br>
   * If this shadow is equal to BASE this means that upload completely failed (nothing is uploaded)<br>
   * If this shadow is equal to UPLOAD_TASK this means that all requested changes are successfully uploaded 
   */
  public static final DBAttribute<AttributeMap> DONE_UPLOAD = SyncAttributes.Shadow(NS.attr("doneUpload"), "Done-Upload Shadow");
  private static final DBAttribute<byte[]> HISTORY = SyncAttributes.CHANGE_HISTORY;
  public static final DBAttribute<Boolean> IS_SHADOWABLE = SyncAttributes.SHADOWABLE;
  public static final DBAttribute<Boolean> INVISIBLE = SyncAttributes.INVISIBLE;
  public static final DBAttribute<Boolean> UPLOAD_FAILED = NS.bool("uploadFailed", "Last Upload Failed?", false);

  public static final DBAttribute<Integer> DECIMAL_SCALE = NS.integer("decimalScale", "Decimal Scale", false);

  /**
   * @return true if an item can has shadowable value for the attribute. false means that the attribute is not shadowable or
   * has not ever been used, so no item may has a value for it
   */
  public static boolean hasShadowableValue(DBReader reader, DBAttribute<?> attribute) {
    return AttributeInfo.instance(reader).isShadowable(attribute);
  }

  public static void writeHistory(DBWriter writer, long item, List<byte[]> newHistory) {
    if (newHistory == null || newHistory.isEmpty()) writer.setValue(item, HISTORY, null);
    else {
      ByteArray bytes = new ByteArray();
      for (byte[] section : newHistory) {
        if (section == null) bytes.addInt(-1);
        else {
          bytes.addInt(section.length);
          bytes.add(section);
        }
      }
      writer.setValue(item, HISTORY, bytes.toNativeArray());
    }
  }

  @NotNull
  private static byte[][] decodeHistory(byte[] bytes) throws HistoryException {
    if (bytes == null || bytes.length == 0) return Const.EMPTY_BYTES2D;
    List<byte[]> result = Collections15.arrayList();
    int offset = 0;
    while (offset < bytes.length - 4) {
      int count = ByteArray.getInt(bytes, offset);
      offset += 4;
      if (count < -1 || offset + count > bytes.length)
        throw new HistoryException("Wrong section length " + count + " at " + (offset - 4));
      if (count == -1) result.add(null);
      else {
        result.add(ArrayUtil.arrayCopy(bytes, offset, count));
        offset += count;
      }
    }
    if (offset != bytes.length) throw new HistoryException("Tail not decoded " + offset + " length " + bytes.length);
    return result.toArray(new byte[result.size()][]);
  }

  @NotNull
  public static byte[][] getHistory(ItemVersion version) {
    byte[] bytes = version.getValue(HISTORY);
    try {
      return decodeHistory(bytes);
    } catch (HistoryException e) {
      Log.error("Error decoding history for " + version.getItem() + " " + ArrayUtil.toString(bytes), e);
      return Const.EMPTY_BYTES2D;
    }
  }

  @NotNull
  public static AttributeMap filterShadowable(DBReader reader, AttributeMap map) {
    AttributeMap result = new AttributeMap();
    for (DBAttribute attribute : map.keySet()) {
      if (hasShadowableValue(reader, attribute)) result.put(attribute, map.get(attribute));
    }
    return result;
  }

  public static void markShadowable(DBAttribute<?> attribute) {
    attribute.initialize(IS_SHADOWABLE, true);
  }

  public static AttributeMap getInvisible() {
    AttributeMap map = new AttributeMap();
    map.put(INVISIBLE, true);
    return map;
  }

  public static boolean isInvisible(AttributeMap map) {
    return map != null && Boolean.TRUE.equals(map.get(SyncSchema.INVISIBLE));
  }

  public static void discardSingle(DBWriter writer, long item) {
    HolderCache holders = HolderCache.instance(writer);
    VersionHolder serverHolder = holders.getServerHolder(item);
    if (serverHolder instanceof VersionHolder.WriteTrunk) {
      VersionHolder.WriteTrunk trunk = (VersionHolder.WriteTrunk) serverHolder;
      boolean aNew = trunk.isNew();
      AttributeMap download = writer.getValue(item, SyncSchema.DOWNLOAD);
      AttributeMap conflict = writer.getValue(item, SyncSchema.CONFLICT);
      AttributeMap base = writer.getValue(item, SyncSchema.BASE);
      if (aNew && download == null && conflict == null && base == null) serverHolder = null;
      else Log.error("not empty shadows " + aNew + " " + download + " " + conflict + " " + base);
    }
    AttributeMap server = serverHolder != null ? serverHolder.getAllShadowableMap() : null;
    if (server != null) {
      AttributeMap map = writer.getAttributeMap(item);
      for (DBAttribute<?> attribute : filterShadowable(writer, map).keySet()) {
        if (!server.containsKey(attribute))
          writer.setValue(item, attribute, null);
      }
      for (DBAttribute<?> a : server.keySet()) {
        DBAttribute<Object> attribute = (DBAttribute<Object>) a;
        writer.setValue(item, attribute, server.get(attribute));
      }
    }
    holders.setBase(item, null);
    holders.setConflict(item, null);
    holders.setDownload(item, null);
    holders.setUploadTask(item, null);
    holders.setDoneUpload(item, null);
  }

  public static void setDecimalScale(int scale, DBAttribute<BigDecimal> ... attributes) {
    for (DBAttribute<BigDecimal> attribute : attributes) attribute.initialize(DECIMAL_SCALE, scale);
  }

  private static class HistoryException extends Exception {
    public HistoryException() {
    }

    public HistoryException(Throwable cause) {
      super(cause);
    }

    public HistoryException(String message) {
      super(message);
    }

    public HistoryException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
