package com.almworks.api.database;

import com.almworks.util.collections.Containers;
import com.almworks.util.collections.Convertor;
import org.jetbrains.annotations.*;

import java.util.Comparator;

public interface ArtifactPointer {
  ArtifactPointer[] EMPTY_ARRAY = new ArtifactPointer[0];
  
  Comparator<ArtifactPointer> KEY_COMPARATOR = new Comparator<ArtifactPointer>() {
    public int compare(ArtifactPointer o1, ArtifactPointer o2) {
      long k1 = o1.getPointerKey();
      long k2 = o2.getPointerKey();
      return Containers.compareLongs(k1, k2);
    }
  };

  Convertor<ArtifactPointer, Artifact> GET_ARTIFACT = new Convertor<ArtifactPointer, Artifact>() {
    @Override
    public Artifact convert(ArtifactPointer value) {
      return value != null ? value.getArtifact() : null;
    }
  };

  @NotNull
  Artifact getArtifact();

  long getPointerKey();
}
