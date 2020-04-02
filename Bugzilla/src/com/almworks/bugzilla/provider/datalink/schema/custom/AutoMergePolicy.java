package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.AutoMergeData;
import com.almworks.items.sync.util.AutoMergeLongSets;
import org.almworks.util.Util;

import java.util.Collection;

interface AutoMergePolicy<T> {
  AutoMergePolicy DO_NOTHING = new AutoMergePolicy<Object>() {
    @Override
    public void resolve(AutoMergeData data, DBAttribute<Object> attribute) {}
  };

  AutoMergePolicy TEXT = new AutoMergePolicy<String>() {
    @Override
    public void resolve(AutoMergeData data, DBAttribute<String> attribute) {
      String originalLocal = data.getLocal().getNewerValue(attribute);
      String newLocal = normalize(originalLocal);
      String newServer = normalize(data.getServer().getNewerValue(attribute));
      if (newLocal.equals(newServer)) data.discardEdit(attribute);
      else {
        String localBase = normalize(data.getLocal().getElderValue(attribute));
        if (newLocal.equals(localBase)) data.discardEdit(attribute);
        else {
          String serverBase = normalize(data.getServer().getElderValue(attribute));
          if (newServer.equals(serverBase)) data.setResolution(attribute, originalLocal);
        }
      }
    }

    private String normalize(String text) {
      return Util.NN(text);
    }
  };
  
  AutoMergePolicy<? extends Collection<? extends Long>> MERGE_ITEM_SET = new AutoMergePolicy<Collection<Long>>() {
    @Override
    public void resolve(AutoMergeData data, DBAttribute<Collection<Long>> attribute) {
      AutoMergeLongSets.mergeSets(data, attribute);
    }
  };

  void resolve(AutoMergeData data, DBAttribute<T> attribute);
}
