package com.almworks.tools.tagexporter;

import com.almworks.api.database.*;
import com.almworks.database.*;
import com.almworks.universe.FileUniverse;
import com.almworks.util.collections.Functional;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.progress.Progress;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.io.File;
import java.util.List;

/** Represents local database employed in Deskzilla 1.x--2.x and JIRA Client 1.x--2.x. */
public class ArtifactDb {
  /** @return null if workspace directory contains database file */
  @Nullable
  public static String verifyWorkspace(File workspace) {
    if (workspace == null) {
      return "Please specify workspace folder";
    } else if (!workspace.isDirectory()) {
      return "Specified workspace is not a folder";
    } else {
      WorkspaceStructure workArea = new WorkspaceStructure(workspace);
      if (!isWorkspace(workArea)) {
        return "Cannot find database file in the workspace folder";
      }
    }
    return null;
  }

  /**
   * @return true if the specified work area contains database of this kind.
   * */
  public static boolean isWorkspace(@NotNull WorkspaceStructure workArea) {
    File dbDir = workArea.getDatabaseDir();
    if (!dbDir.isDirectory()) return false;
    try {
      File universeFile = new File(dbDir, FileUniverse.SINGLE_DATAFILE_NAME).getCanonicalFile();
      return universeFile.isFile();
    } catch (Exception ex) {
      return false;
    }
  }

  @NotNull
  public static List<TagInfo> readTags(@NotNull final WorkspaceStructure workArea, final Progress progress) throws Exception {
    progress.setProgress(0.05);
    progress.setActivity("Opening database file");
    FileUniverse universe = new FileUniverse(workArea.getDatabaseDir());
    try {
      universe.start();
    } catch (Failure f) {
      // This ugly code is a result of FileUniverse.start not throwing the exception resulted from opening database file
      Throwable ex = f.getCause();
      throw ex != null && (ex instanceof Exception) ? (Exception) ex : f;
    }

    Workspace workspace = null;
    try {
      universe.setReadOnly(true);

      Basis basis = new Basis(universe, ConsistencyWrapper.FAKE);
      basis.start();

      workspace = new WorkspaceImpl(basis);
      workspace.repair();
      workspace.open();

      progress.setProgress(0.2);
      progress.setActivity("Reading tags");
      return new ArtifactDbTagReader(workspace, progress.createDelegate(0.8)).readTags();
    } finally {
      try { universe.stop(); } catch (Exception e) { Log.warn(e); }
      try { if (workspace != null) workspace.close(); } catch (Exception e) { Log.warn(e); }
      try { WorkspaceStatic.cleanup(); } catch (Exception e) { Log.warn(e); }
    }
  }

  private static class ArtifactDbTagReader {
    private final ArtifactPointer attrTagType;
    private final ArtifactPointer attrArtifactTags;
    private final ArtifactPointer attrTagIconPath;

    // A connection artifact must have only one of these
    @Nullable
    private final ArtifactPointer attrBugzillaConnectionId;
    @Nullable
    private final ArtifactPointer attrJiraClientConnectionId;

    // An artifact may have only one of these (unsubmitted locally created artifacts don't)
    @Nullable
    private final ArtifactPointer attrBugzillaBugId;
    @Nullable
    private final ArtifactPointer attrJiraClientIssueKey;

    private final Workspace myWorkspace;
    private final Progress myProgress;

    public ArtifactDbTagReader(Workspace workspace, Progress progress) {
      String tagsPrefix = "system:desc:tags:";
      attrTagType = getById(workspace, tagsPrefix + "type");
      attrArtifactTags = getById(workspace, tagsPrefix + "attribute");
      attrTagIconPath = getById(workspace, tagsPrefix + "iconPath");

      attrBugzillaConnectionId = getById(workspace, "tracker:bz:type:provider:attribute:providerID", false);
      attrJiraClientConnectionId = getById(workspace, "tracker:jira:attribute:providerID", false);

      attrBugzillaBugId = getById(workspace, "tracker:bz:attribute:id", false);
      attrJiraClientIssueKey = getById(workspace, "tracker:jira:type:Issue:attribute:KEY", false);

      if (attrBugzillaConnectionId == null && attrJiraClientConnectionId == null
        || attrBugzillaBugId == null && attrJiraClientIssueKey == null)
      {
        Log.error("Attributes not found! " + attrBugzillaConnectionId + " " + attrJiraClientConnectionId + " " + attrBugzillaBugId + " " + attrJiraClientIssueKey);
        throw new Failure("Database is broken");
      }

      myWorkspace = workspace;
      myProgress = progress;
    }

    public List<TagInfo> readTags() {
      FilterManager fm = myWorkspace.getFilterManager();
      ArtifactView userView = myWorkspace.getViews().getUserView();

      List<Revision> tags = userView.filter(fm.attributeEquals(SystemObjects.ATTRIBUTE.TYPE, attrTagType, true)).getAllArtifacts();
      int nTags = tags.size();
      List<TagInfo> tagInfos = Collections15.arrayList(nTags);
      double fTags = nTags;
      for (int i = 0; i < nTags; ++i) {
        Revision tag = tags.get(i);

        String iconPath = Util.NN(tag.getValue(attrTagIconPath, String.class));
        String name = Util.NN(tag.getValue(SystemObjects.ATTRIBUTE.DISPLAYABLE_NAME));

        myProgress.setProgress(i / fTags, name);

        List<Revision> taggedArtifacts = userView.filter(fm.containsReference(attrArtifactTags, tag, true)).getAllArtifacts();
        MultiMap<String, String> connectionId_artifactId = MultiMap.create(taggedArtifacts.size());
        for (Revision taggedArtifact : taggedArtifacts) {
          String connectionId = getConnectionId(taggedArtifact);
          String artifactId = getArtifactId(taggedArtifact);
          if (connectionId != null && artifactId != null) {
            connectionId_artifactId.add(connectionId, artifactId);
          }
        }

        tagInfos.add(new TagInfo(name, iconPath, connectionId_artifactId, attrJiraClientConnectionId != null));
      }
      return tagInfos;
    }

    private String getConnectionId(Revision rev) {
      Artifact connection = rev.getValue(SystemObjects.ATTRIBUTE.CONNECTION);
      if (connection != null) {
        Revision connectionRev = connection.getLastRevision();
        String connectionId = null;
        if (attrBugzillaConnectionId != null) connectionId = connectionRev.getValue(attrBugzillaConnectionId, String.class);
        if (connectionId != null) return connectionId;
        return attrJiraClientConnectionId == null ? null : connectionRev.getValue(attrJiraClientConnectionId, String.class);
      }
      return null;
    }

    private String getArtifactId(Revision rev) {
      if (attrBugzillaBugId != null) {
        Integer bugId = rev.getValue(attrBugzillaBugId, Integer.class);
        if (bugId != null) return String.valueOf(bugId);
      }
      return attrJiraClientIssueKey == null ? null : rev.getValue(attrJiraClientIssueKey, String.class);
    }
  }

  private static ArtifactPointer getById(Workspace workspace, String id) {
    return getById(workspace, id, true);
  }

  private static ArtifactPointer getById(Workspace workspace, String id, boolean require) {
    FilterManager fm = workspace.getFilterManager();
    ArtifactView view = workspace.getViews().getRootView().filter(fm.attributeEquals(SystemObjects.ATTRIBUTE.ID, id, true));
    List<Revision> all = view.getAllArtifacts();
    if (all.size() > 1) {
      String msg = "multiple revisions with id '" + id + "': " + all;
      Log.error(msg);
      throw new Failure(msg);
    } else if (all.isEmpty() && require) {
      String msg = "no revisions found with id '" + id + '\'';
      Log.error(msg);
      throw new Failure(msg);
    } else {
      return Functional.first(all);
    }
  }

  public static class TagInfo {
    public final String name;
    public final String iconPath;
    /** Connection ID => [item key] */
    public final MultiMap<String, String> items;
    public final boolean isJira;

    public TagInfo(String name, String iconPath, MultiMap<String, String> items, boolean isJira) {
      this.name = name;
      this.iconPath = iconPath;
      this.items = items;
      this.isJira = isJira;
    }
  }
}
