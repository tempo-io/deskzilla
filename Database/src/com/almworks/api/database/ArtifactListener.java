package com.almworks.api.database;

public interface ArtifactListener {
  boolean onListeningAcknowledged(WCN.Range past, WCN.Range future);

  boolean onArtifactExists(Artifact artifact, Revision lastRevision);

  boolean onPastPassed(WCN.Range pastRange);

  boolean onArtifactAppears(Artifact artifact, Revision lastRevision);

  boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision);

  boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision);

  boolean onWCNPassed(WCN wcn);

  public static abstract class Adapter implements ArtifactListener {
    private final boolean myDefaultProceed;

    protected Adapter(boolean defaultProceed) {
      myDefaultProceed = defaultProceed;
    }

    protected Adapter() {
      this(true);
    }

    public boolean onListeningAcknowledged(WCN.Range past, WCN.Range future) {
      return true;
    }

    public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
      return myDefaultProceed;
    }

    public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
      onViewChanged();
      return myDefaultProceed;
    }

    public boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision) {
      onViewChanged();
      return myDefaultProceed;
    }

    public boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
      onViewChanged();
      return myDefaultProceed;
    }

    public boolean onPastPassed(WCN.Range pastRange) {
      return true;
    }

    protected void onViewChanged() {
    }

    public boolean onWCNPassed(WCN wcn) {
      return true;
    }
  }

  public static abstract class Decorator implements ArtifactListener {
    protected final ArtifactListener myDecorated;

    protected Decorator(ArtifactListener decorated) {
      assert decorated != null;
      myDecorated = decorated;
    }

    public boolean onListeningAcknowledged(WCN.Range past, WCN.Range future) {
      return myDecorated.onListeningAcknowledged(past, future);
    }

    public boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
      return myDecorated.onArtifactExists(artifact, lastRevision);
    }

    public boolean onPastPassed(WCN.Range pastRange) {
      return myDecorated.onPastPassed(pastRange);
    }

    public boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
      return myDecorated.onArtifactAppears(artifact, lastRevision);
    }

    public boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision, Revision unseenRevision) {
      return myDecorated.onArtifactDisappears(artifact, lastSeenRevision, unseenRevision);
    }

    public boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
      return myDecorated.onArtifactChanges(artifact, prevRevision, newRevision);
    }

    public boolean onWCNPassed(WCN wcn) {
      return myDecorated.onWCNPassed(wcn);
    }
  }
}
