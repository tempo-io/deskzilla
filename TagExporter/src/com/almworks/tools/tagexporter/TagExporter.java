package com.almworks.tools.tagexporter;

import com.almworks.util.DecentFormatter;
import com.almworks.util.NoObfuscation;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Functional;
import com.almworks.util.commons.Procedure2;
import com.almworks.util.components.plaf.LAFUtil;
import com.almworks.util.exec.LongEventQueue;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.io.IOUtils;
import com.almworks.util.progress.Progress;
import com.almworks.util.tags.TagFileStorage;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.logging.Level;

public class TagExporter implements NoObfuscation {
  public static final String TOOL_NAME = "Deskzilla and JIRA Client 1.x\u20132.x Tag Exporter";

  // error keys
  static final String WORKSPACE_KEY = "workspace";
  static final String OUT_FILE_KEY = "outFile";
  static final String GLOBAL_KEY = "global";

  public static void main(String[] args) {
    setupLogging();
    if (args.length == 1 && Collections15.arrayList("?", "/?", "-h", "-help", "help").contains(args[0])) {
      System.err.println(TOOL_NAME);
      System.err.println("Usage: tagexporter [-v] <workspace folder path>");
      System.err.println("-v: verbose mode");
      System.exit(1);
    }
    if (args.length == 0) runGui();
    else {
      boolean verbose = "-v".equals(args[0]);
      if (verbose && args.length == 1) {
        System.err.println("Please specify workspace folder");
        System.exit(1);
      }
      String workspace = verbose ? args[1] : args[0];
      if (args.length > (verbose ? 2 : 1)) {
        // count the rest of the arguments as a workspace path with spaces
        workspace = StringUtil.implode(Arrays.asList(verbose ? Arrays.copyOfRange(args, 1, args.length) : args), " ");
      }
      runCli(workspace, verbose);
    }
  }

  private static void setupLogging() {
    DecentFormatter.install("");
  }

  private static void runGui() {
    Log.getApplicationLogger().setLevel(Level.ALL);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        LongEventQueue.installToContext();
        LAFUtil.initializeLookAndFeel();

        TagExporterForm.showWindow();
      }
    });
  }

  private static void runCli(String workspace, boolean verbose) {
    String error = ArtifactDb.verifyWorkspace(new File(workspace));
    if (error != null) {
      System.err.println(error);
      System.exit(2);
    }
    Log.getApplicationLogger().setLevel(verbose ? Level.FINEST : Level.SEVERE);
    final Progress p = new Progress();
    if (verbose) {
      p.getModifiable().addChangeListener(Lifespan.FOREVER, ThreadGate.STRAIGHT, new ChangeListener() {
        @Override
        public void onChange() {
          String activity = String.valueOf(Util.NN(p.getActivity(), ""));
          if (!activity.isEmpty())
            System.err.println(activity);
        }
      });
    }
    exportTags(new File(workspace), null, p, new Procedure2<String, String>() {
      @Override
      public void invoke(String key, String error) {
        String preamble = key == WORKSPACE_KEY ? "Workspace problem: " : "Error: ";
        System.err.println(preamble + error);
        System.exit(3);
      }
    });
  }

  public static TagExporter exportTags(File workArea, File outFile, Progress progress, Procedure2<String, String> reportError) {
    return exportTags(workArea, outFile, progress, reportError, ThreadGate.STRAIGHT);
  }

  public static TagExporter exportTags(File workArea, File outFile, Progress progress, Procedure2<String, String> reportError, ThreadGate gate) {
    TagExporter me = new TagExporter(new WorkspaceStructure(workArea), outFile, progress, reportError, gate);
    me.start();
    return me;
  }

  public static boolean is1xWorkspace(File workspace) {
    return ArtifactDb.isWorkspace(new WorkspaceStructure(workspace));
  }

  private final WorkspaceStructure myWorkArea;
  @Nullable
  private final File myOutFile;
  private final Progress myProgress;
  private final Procedure2<String, String> myReportError;

  private final Object myLock = new Object();
  private boolean myCancelled = false;
  private List<ArtifactDb.TagInfo> myTags;
  private TagInfoConvertor myTagInfoConvertor;

  public TagExporter(WorkspaceStructure workArea, File outFile, Progress progress, final Procedure2<String, String> reportError, final ThreadGate gate) {
    myWorkArea = workArea;
    myOutFile = outFile;
    myProgress = progress;
    myReportError = new Procedure2<String, String>() {
      @Override
      public void invoke(final String key, final String message) {
        gate.execute(new Runnable() {
          @Override
          public void run() {
            reportError.invoke(key, message);
          }
        });
        cancel();
      }
    };
  }

  public void start() {
    ThreadGate.LONG.execute(new Runnable() { public void run() {
      try {
        List<ArtifactDb.TagInfo> tags = ArtifactDb.readTags(myWorkArea, myProgress.createDelegate(0.6));
        synchronized (myLock) {
          myTags = tags;
          myLock.notifyAll();
        }
      } catch (IOException ex) {
        myReportError.invoke(WORKSPACE_KEY, ex.getMessage() + " \u2014 is the application running?");
      } catch (Exception ex) {
        myReportError.invoke(GLOBAL_KEY, ex.getMessage());
      }
    }});
    ThreadGate.LONG.execute(new Runnable() { public void run() {
      try {
        Progress configReadProgress = myProgress.createDelegate(0.3, "config.xml", "Reading workspace configuration file");
        Map<String, String> connectionIdUrl = ConnectionUrlFetcher.fetchConnectionUrls(myWorkArea);
        configReadProgress.setDone();
        synchronized (myLock) {
          myTagInfoConvertor = new TagInfoConvertor(connectionIdUrl);
          myLock.notifyAll();
        }
      } catch (Exception ex) {
        myReportError.invoke(WORKSPACE_KEY, ex.getMessage());
      }
    }});
    ThreadGate.LONG.execute(new Runnable() { public void run() {
      myProgress.setActivity(myOutFile != null ? "Writing to file" : null);
      List<ArtifactDb.TagInfo> tags;
      TagInfoConvertor tagInfoConvertor;
      PrintStream ps = null;
      try {
        synchronized (myLock) {
          while (!myCancelled && (myTags == null || myTagInfoConvertor == null)) {
            myLock.wait();
          }
          if (myCancelled) return;
          tags = myTags;
          tagInfoConvertor = myTagInfoConvertor;
        }

        ps = myOutFile == null ? System.out : new PrintStream(myOutFile, "Unicode");
        TagFileStorage.write(Functional.convert(tags, tagInfoConvertor), ps, myWorkArea.getRootDir().getPath());
        myProgress.setDone();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      } catch (Exception ex) {
        myReportError.invoke(OUT_FILE_KEY, ex.getMessage());
      } finally {
        IOUtils.closeStreamIgnoreExceptions(ps);
        myProgress.setDone();
      }
    }});
  }

  public void cancel() {
    synchronized (myLock) {
      myCancelled = true;
    }
    myProgress.setDone();
  }

  public Progress getProgress() {
    return myProgress;
  }

  @Nullable
  public File getOutputFile() {
    return myOutFile;
  }

  public boolean isCancelled() {
    synchronized (myLock) {
      return myCancelled;
    }
  }
}
