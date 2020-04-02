package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.util.ArtifactPointerAspects;

public class SchemaAspects extends ArtifactPointerAspects implements SystemObjects {
  public SchemaAspects() {
    addAspect(new Aspect<Revision, HostedString>(ArtifactAspects.ASPECT_STRING_REPRESENTATION) {
      public HostedString getAspect(Revision revision) {
        String value = revision.getValue(ATTRIBUTE.DISPLAYABLE_NAME);
        if (value != null)
          return HostedString.string(value);
        value = revision.getValue(ATTRIBUTE.NAME);
        if (value != null)
          return HostedString.string(value);
        value = revision.getValue(ATTRIBUTE.ID);
        if (value != null)
          return HostedString.string(value);
        return HostedString.string("A:" + revision.getArtifact().getKey());
      }
    });
  }


  protected boolean isMyAspectedArtifact(Revision lastRevision) {
    return Boolean.TRUE.equals(lastRevision.getValue(ATTRIBUTE.IS_SYSTEM_OBJECT));
  }
}
