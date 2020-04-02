package com.almworks.database.objects;

import com.almworks.api.database.*;

interface RevisionCreationContext {
  Artifact getArtifact();

  RevisionChain getRevisionChain();

  Revision getNewRevision();

  RevisionIterator getRevisionIterator();
}
