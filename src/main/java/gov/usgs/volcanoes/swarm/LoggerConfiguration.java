package gov.usgs.volcanoes.swarm;

import java.io.File;
import org.slf4j.LoggerFactory;

/**
 * The logger configuration.
 * 
 * <p>
 * If the logback configuration file location is specified with the
 * <code>logback.configurationFile</code> then that file will be used for the logger configuration.
 * For example:
 * <p>
 * <code>-Dlogback.configurationFile=/path/to/logback.xml</code>
 * <p>
 * For setting the logging level to debug, set the logback configuration file to
 * <code>logbackDebug.xml</code> which is included in the swarm.jar. For example:
 * <p>
 * <p>
 * <b>NOTE</b>: The <code>logback.configurationFile</code> value should be set to an absolute path
 * if not using a file included in the swarm.jar file.
 * <code>-Dlogback.configurationFile=logbackDebug.xml</code>
 * <p>
 * Otherwise if the <code>logback.xml</code> file exists in the current directory then it will be
 * used for the logger configuration.
 * <p>
 * 
 * @see #init()
 */
public class LoggerConfiguration {
  /** The default logback configuration file */
  private static final File DEFAULT_CONFIGURATION_FILE;
  /** The default logback configuration filename */
  private static final String DEFAULT_CONFIGURATION_FILENAME = "logback.xml";
  /** The logback configuration file key */
  private static final String KEY = "logback.configurationFile";
  static {
    DEFAULT_CONFIGURATION_FILE =
        new File(new File("").getAbsoluteFile(), DEFAULT_CONFIGURATION_FILENAME);
  }

  private static boolean configurationFilePropertyIsSet() {
    final boolean setFlag;
    String value = System.getProperty(KEY);
    if (value == null) {
      setFlag = false;
    } else {
      setFlag = true;
      LoggerFactory.getLogger(LoggerConfiguration.class)
          .info("LogbackConfiguration: configurationFilePropertyIsSet");
    }
    return setFlag;
  }

  /**
   * Initialize the login configuration.
   * 
   * @return true if using the login configuration in the current directory.
   */
  public static boolean init() {
    return init(DEFAULT_CONFIGURATION_FILE);
  }

  private static boolean init(File configurationFile) {
    // if user specified the configuration file location
    if (configurationFilePropertyIsSet()) {
      return false; // use the default configuration
    }
    if (configurationFile == null || configurationFile.isDirectory()) {
      configurationFile = new File(configurationFile, DEFAULT_CONFIGURATION_FILENAME);
    }
    if (!configurationFile.canRead()) { // if we cannot read the configuration file
      return false; // use the default configuration
    }
    String value = configurationFile.toString();
    System.setProperty(KEY, value);
    LoggerFactory.getLogger(LoggerConfiguration.class).info("LogbackConfiguration: set {}={}", KEY,
        value);
    return true;
  }
}
