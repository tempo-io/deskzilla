package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.api.application.UiItem;

import java.util.Comparator;

public interface Flag extends UiItem {
  Comparator<Flag> ORDER = new Comparator<Flag>() {
    @Override
    public int compare(Flag o1, Flag o2) {
      if (o1 == o2) return 0;
      if (o1 == null || o2 == null) return o1 == null ? -1 : 1;
      int byFlag = o1.getName().compareTo(o2.getName());
      if (byFlag != 0) return byFlag;
      FlagStatus s1 = o1.getStatus();
      FlagStatus s2 = o2.getStatus();
      return s1.compareTo(s2);
    }
  };

  String getSetterName();

  String getRequesteeName();

  String getName();

  FlagStatus getStatus();
}
