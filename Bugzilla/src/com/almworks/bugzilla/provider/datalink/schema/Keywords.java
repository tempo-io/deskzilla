package com.almworks.bugzilla.provider.datalink.schema;

import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.KeywordLink;
import com.almworks.integers.LongArray;
import com.almworks.integers.LongListIterator;
import com.almworks.items.sync.DBDrain;
import com.almworks.items.sync.ItemVersionCreator;

import java.util.Map;

public class Keywords {

  public static void updateAll(DBDrain drain, PrivateMetadata pm, Map<String, String> keywords) {
    if (keywords == null) return;
    LongArray items = CommonMetadata.keywordLink.getReferentsView(pm).query(drain.getReader()).copyItemsSorted();
    for (Map.Entry<String, String> entry : keywords.entrySet()) {
      String keyword = entry.getKey();
      String description = entry.getValue();
      long item = CommonMetadata.keywordLink.createProxy(pm, keyword).findOrCreate(drain);
      items.remove(item);
      ItemVersionCreator creator = drain.changeItem(item);
      creator.setAlive();
      creator.setValue(KeywordLink.attrDisplay, keyword);
      creator.setValue(KeywordLink.attrDescription, description);
      creator.setValue(KeywordLink.attrSynced, Boolean.TRUE);
    }
    for (LongListIterator i = items.iterator(); i.hasNext();) {
      drain.changeItem(i.next()).setValue(KeywordLink.attrSynced, Boolean.FALSE);
    }
  }
}
