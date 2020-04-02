package com.almworks.platform;

import com.almworks.api.install.Setup;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.platform.ProductInformation;
import com.almworks.util.DecentFormatter;
import com.almworks.util.properties.Role;
import org.almworks.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiagnosticRecorder {
  public static final Role<DiagnosticRecorder> ROLE = Role.role(DiagnosticRecorder.class);
  private static final SimpleDateFormat DIAG_DIR = new SimpleDateFormat("yyyyMMdd-HHmm");

  private final ProductInformation myProductInfo;
  private final WorkArea myWorkArea;
  private final Logger myInternalLogger;

  private File mySessionDir;
  private FileHandler mySessionHandler;

  public DiagnosticRecorder(ProductInformation productInfo, WorkArea workArea) {
    myProductInfo = productInfo;
    myWorkArea = workArea;

    myInternalLogger = Logger.getLogger("com.almworks.diagnostics");
    myInternalLogger.setLevel(Level.ALL);
    myInternalLogger.setUseParentHandlers(false);
  }

  private static synchronized File getNextDiagnosticsDir() throws IOException {
    final String prefix = "diag-" + DIAG_DIR.format(new Date()) + "-";
    final Pattern pattern = Pattern.compile(prefix + "(\\d+)");

    final File logDir = Setup.getLogDir();

    int lastIndex = -1;
    for(final File f : logDir.listFiles()) {
      final Matcher m = pattern.matcher(f.getName());
      if(m.matches()) {
        lastIndex = Math.max(lastIndex, Integer.parseInt(m.group(1)));
      }
    }

    final File diagDir = new File(logDir, prefix + (lastIndex + 1));
    if(Setup.createDir(diagDir) == null) {
      throw new IOException("cannot create log directory " + diagDir);
    }

    return diagDir;
  }

  public synchronized void startSession() {
    if(mySessionDir != null) {
      return;
    }

    try {
      mySessionDir = getNextDiagnosticsDir();
      mySessionHandler = Setup.getDiagnosticsHandler(mySessionDir);
    } catch(IOException e) {
      Log.error(e);
      mySessionDir = null;
      mySessionHandler = null;
      return;
    }

    mySessionHandler.setLevel(Level.FINE);
    mySessionHandler.setFormatter(new DecentFormatter());
    Log.getRootLogger().addHandler(mySessionHandler);
    myInternalLogger.addHandler(mySessionHandler);

    myInternalLogger.info("Started diagnostic session " + mySessionDir.getName());
    PlatformLog.logVersionAndEnvironment(myInternalLogger, myProductInfo, myWorkArea);
  }

  public synchronized File stopSession() {
    if(mySessionDir == null) {
      return null;
    }

    final File sessionDir = mySessionDir;
    mySessionDir = null;

    myInternalLogger.info("Stopped diagnostic session " + sessionDir.getName());
    Log.getRootLogger().removeHandler(mySessionHandler);
    myInternalLogger.removeHandler(mySessionHandler);
    mySessionHandler.close();
    mySessionHandler = null;

    return sessionDir;
  }

  public boolean isRecording() {
    return mySessionDir != null;
  }

  public File getSessionDir() {
    return mySessionDir;
  }
}
