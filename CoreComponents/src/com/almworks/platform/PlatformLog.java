package com.almworks.platform;

import com.almworks.api.install.TrackerProperties;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.platform.ProductInformation;
import com.almworks.util.GlobalProperties;
import com.almworks.util.files.FileUtil;

import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

class PlatformLog {
  static void logVersionAndEnvironment(Logger logger, ProductInformation pi, WorkArea wa) {
    logger.info("Starting " + pi.getName());
    logger.info("Workspace: " + wa.getRootDir());
    logger.info("Home: " + wa.getHomeDir());
    logger.info(pi.getVersion() + " (" + pi.getVersionType() + "), build " + pi.getBuildNumber());

    String[] logProps = {
      "java.version", "java.vm.version", "java.vm.vendor", "java.vm.name", "java.vm.info", "java.specification.version", "os.name",
      "os.version", "os.arch", "sun.os.patch.level", "sun.arch.data.model", "sun.cpu.endian", "user.country",
      "user.timezone", "file.encoding", "user.language",};
    for(String prop : logProps) {
      logger.info(prop + " = " + System.getProperty(prop));
    }

    Runtime runtime = Runtime.getRuntime();
    logger.info("cpus = " + runtime.availableProcessors());
    logger.info("mx = " + FileUtil.getMemoryMegs(runtime.maxMemory()) + "m");
    logger.info("mem = " + FileUtil.getMemoryMegs(runtime.totalMemory()) + "m");

    boolean assertions = false;
    assert assertions = true;
    logger.info("assertions = " + (assertions ? "on" : "off"));

    Properties properties = System.getProperties();
    for(Map.Entry<Object, Object> entry : properties.entrySet()) {
      String key = String.valueOf(entry.getKey());
      if(TrackerProperties.hasProperty(key) || GlobalProperties.hasProperty(key)) {
        logger.info(entry.getKey() + " = " + entry.getValue());
      }
    }
  }
}
