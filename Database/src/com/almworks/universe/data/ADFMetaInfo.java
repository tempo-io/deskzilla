package com.almworks.universe.data;

import util.external.UID;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
interface ADFMetaInfo {
  UID getUID();

  Map<String, String> getCustomProperties();
}
