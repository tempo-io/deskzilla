package com.almworks.database.objects.remote;

import com.almworks.api.database.Revision;

interface RCBHelperInterface {
  void rescan();

  boolean isReincarnating(Revision revision);

  boolean isReincarnating();

  Object getArtifactLock();
}
