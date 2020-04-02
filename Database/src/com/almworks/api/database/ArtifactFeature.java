package com.almworks.api.database;

/**
 * :todoc:
 *
 * @author sereda
 */
public final class ArtifactFeature {
  public static final ArtifactFeature MULTI_CHAIN = new ArtifactFeature("supportsLocalBranches");
  public static final ArtifactFeature[] LOCAL_ARTIFACT = {};
  public static final ArtifactFeature[] REMOTE_ARTIFACT = {MULTI_CHAIN};
  public static final ArtifactFeature[] DEFAULT_ARTIFACT = LOCAL_ARTIFACT;

  private String myName;

  private ArtifactFeature(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }
}
