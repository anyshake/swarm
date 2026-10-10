package gov.usgs.volcanoes.swarm.file;

import gov.usgs.volcanoes.swarm.ChannelUtil;
import gov.usgs.volcanoes.swarm.SwarmConst;
import gov.usgs.volcanoes.swarm.TimeConstants;
import gov.usgs.volcanoes.swarm.data.DataSourceType;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The local file source.
 */
public class LocalFileSource extends ActiveDataSource
    implements DataStructureConst, SwarmConst, TimeConstants {
  /** The configuration starting text */
  public static final String CONFIG_STARTING_TEXT;
  /** The logger. */
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileSource.class);
  /** The short name */
  public static final String SHORT_NAME;
  static {
    SHORT_NAME = DataSourceType.getShortName(LocalFileSource.class);
    CONFIG_STARTING_TEXT = ";" + SHORT_NAME + CONFIG_DELIM_CHAR;
  }
  private DataStructureFormat _dsf;

  /**
   * Create the local file source.
   */
  public LocalFileSource() {}

  @Override
  protected LocalFileSource clone() {
    LocalFileSource copy = new LocalFileSource();
    DataStructureFormat dsf = _dsf;
    if (dsf != null) {
      dsf = dsf.clone();
    }
    copy._dsf = dsf;
    return copy;
  }

  @Override
  public void close() {}

  @Override
  public List<String> getChannels() {
    if (SwingUtilities.isEventDispatchThread()) {
      LOGGER.error("getChannels called on EDT");
      return Collections.emptyList();
    }
    try {
      final DataStructureFormat dsf = getDataStructureFormat();
      if (dsf != null) {
        final List<String> channels = new ArrayList<>();
        String error = dsf.findChannels(channels, this, null, null);
        if (!error.isEmpty()) {
          LOGGER.warn("getChannels error: {}", error);
          return Collections.emptyList();
        }
        if (!channels.isEmpty()) {
          ChannelUtil.assignChannels(channels, this);
          return Collections.unmodifiableList(channels);
        }
      }
    } catch (Exception ex) {
      LOGGER.warn("getChannels error: {}", ex.getMessage());
    }
    return Collections.emptyList();
  }

  @Override
  protected void getData(String station, long t1, long t2) {
    station = LocalFileUtil.getStation(station);
    try {
      final DataStructureFormat dsf = getDataStructureFormat();
      if (dsf != null) {
        File file = null;
        boolean exists = false;
        StringBuilder error = new StringBuilder();
        String fileName, lastFileName = null;
        for (long millis = t1;;) {
          fileName = dsf.getFileName(station, millis);
          if (!fileName.equals(lastFileName)) {
            lastFileName = fileName;
            file = new File(fileName);
            exists = file.exists();
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace("getData({}): {} exists={}", station, fileName, exists);
            }
            if (exists) {
              int count = LocalFileReader.getInstance().cacheWaves(station, file, error);
              if (error.length() != 0) {
                LOGGER.warn("getData {}  {}", station, error);
              } else {
                if (count == 0) {
                  LOGGER.warn("getData {} no waves found", station);
                }
              }
              if (LOGGER.isTraceEnabled() && count != 0) {
                LOGGER.trace("getData {} cached {} samples", station, count);
              }
            }
          }
          if (millis >= t2) {
            break;
          }
          millis += MS_PER_DAY;
          if (millis > t2) {
            millis = t2;
          }
        }
      } else {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("getData: no data structure format {}", station);
        }
      }
    } catch (Exception ex) {
      LOGGER.warn("getData {} error: {}", station, ex.getMessage());
    }
  }

  private DataStructureFormat getDataStructureFormat() {
    return _dsf;
  }

  @Override
  public void parse(String params) {
    try {
      _dsf = DataStructureFormat.parse(params);
    } catch (Exception ex) {
      _dsf = null;
      LOGGER.warn("parse error: {}", ex.getMessage());
    }
  }

  @Override
  public String toConfigString() {
    final StringBuilder sb = new StringBuilder();
    final DataStructureFormat dsf = getDataStructureFormat();
    if (dsf != null) {
      sb.append(name);
      sb.append(LocalFileSource.CONFIG_STARTING_TEXT);
      dsf.appendConfigString(sb);
    }
    String config = sb.toString();
    return config;
  }
}
