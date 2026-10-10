package gov.usgs.volcanoes.swarm.file;

import gov.usgs.volcanoes.swarm.ChannelGroupInfo;
import gov.usgs.volcanoes.swarm.ChannelInfo;
import gov.usgs.volcanoes.swarm.ChannelUtil;
import gov.usgs.volcanoes.swarm.GroupsType;
import gov.usgs.volcanoes.swarm.SwarmConst;
import gov.usgs.volcanoes.swarm.data.SeismicDataSource;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataStructureFormat implements Cloneable, DataStructureConst, SwarmConst {
  /** The logger. */
  private static final Logger LOGGER = LoggerFactory.getLogger(DataStructureFormat.class);
  private static boolean verbose = false;

  /**
   * Append the configuration string.
   * 
   * @param sb the string builder.
   * @param dsf the data structure format.
   */
  public static void appendConfigString(StringBuilder sb, DataStructureFormat dsf) {
    if (dsf != null) {
      sb.append(dsf.getBaseDir());
      sb.append(DS_CONFIG_DELIM_CHAR);
      String s = dsf.getDataStructure();
      if (!SDS.equals(s)) {
        sb.append(s);
      }
      sb.append(DS_CONFIG_DELIM_CHAR);
      appendTime(sb, dsf.startDate);
      sb.append(DS_CONFIG_DELIM_CHAR);
      appendTime(sb, dsf.endDate);
    }
  }

  private static void appendTime(StringBuilder sb, Date date) {
    long time = date.getTime();
    if (time > 0L) {
      sb.append(Long.toString(time));
    }
  }

  private static int getMinSize(int... indices) {
    int size = 0;
    for (int index : indices) {
      size = Math.max(index + 1, size);
    }
    return size;
  }

  /**
   * Test program.
   * 
   * @param args the program arguments.
   */
  public static void main(String[] args) {
    int index = 0;
    // check for flags
    for (index = 0; index < args.length;) {
      if (args[index].equals("-verbose")) {
        verbose = true;
        index++;
      } else {
        break;
      }
    }
    if (args.length < index + 2) {
      System.out.printf("Usage: %s basedir data_structure [outputDir data_structure]\n",
          DataStructureFormat.class.getName());
      return;
    }
    try {
      Path baseDir = Paths.get(args[index++]);
      String dataStructure = parseDataStructureText(args[index++]);
      Path outputDir = null;
      String outputDataStructure = null;
      if (args.length >= index + 2) {
        outputDir = Paths.get(args[index++]);
        Files.createDirectories(outputDir);
        outputDataStructure = parseDataStructureText(args[index++]);
      }
      DataStructureFormat dsf = new DataStructureFormat(baseDir, dataStructure);
      if (outputDir == null) {
        int size;
        String format;
        final List<String> channels = new ArrayList<>();
        String error = dsf.findChannels(channels, null, null, null);
        if (!error.isEmpty()) {
          System.err.printf("findYears error: %s\n", error);
          return;
        }
        size = channels.size();
        format = "%0" + String.valueOf(size).length() + "d '%s'\n";
        for (index = 0; index < size; index++) {
          System.out.printf(format, index + 1, channels.get(index));
        }
      } else {
        dsf.copyFiles(new DataStructureFormat(outputDir, outputDataStructure));
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Parse the configuration string.
   * 
   * @param s the configuration string.
   * @return the data structure format.
   * @throws IllegalArgumentException if the configuration string is invalid.
   */
  public static DataStructureFormat parse(String s) throws IllegalArgumentException {
    if (s == null || s.isEmpty()) {
      throw new IllegalArgumentException("configuration string is invalid (" + s + ")");
    }
    final String sa[] = s.split(DS_CONFIG_DELIMITERS);
    final Path baseDir = Paths.get(sa[0]);
    final String dataStructure;
    if (sa.length > 1) {
      dataStructure = sa[1];
    } else {
      dataStructure = DEFAULT_DATA_STRUCTURE;
    }
    DataStructureFormat dsf = new DataStructureFormat(baseDir, dataStructure);
    if (sa.length > 2 && !sa[2].isEmpty()) {
      try {
        dsf.startDate.setTime(Long.parseLong(sa[2]));
      } catch (Exception ex) {
        throw new IllegalArgumentException("configuration string is invalid (" + s + ")");
      }
    }
    if (sa.length > 3 && !sa[3].isEmpty()) {
      try {
        dsf.endDate.setTime(Long.parseLong(sa[3]));
      } catch (Exception ex) {
        throw new IllegalArgumentException("configuration string is invalid (" + s + ")");
      }
    }
    return dsf;
  }

  /**
   * Parse the data structure text.
   * 
   * @param s the data structure text or null or empty string for the default.
   * @return the parsed data structure text.
   */
  public static String parseDataStructureText(String s) {
    if (s == null || s.isEmpty()) {
      s = DEFAULT_DATA_STRUCTURE;
    } else {
      switch (s.toUpperCase()) {
        case "SDS":
          s = SDS;
          break;
        case "BUD":
          s = BUD;
          break;
        case SDS_TEXT:
          s = SDS;
          break;
        case BUD_TEXT:
          s = BUD;
          break;
        default:
          int begIndex = s.indexOf('(');
          if (begIndex == -1) {
            begIndex = 0;
          } else {
            begIndex++; // next character
          }
          int endIndex = s.lastIndexOf(')');
          if (endIndex == -1) {
            endIndex = s.length();
          }
          s = s.substring(begIndex, endIndex);
          break;
      }
    }
    return s;
  }

  private static String[] split(String fileName, String delimiters) {
    return fileName.split(delimiters);
  }

  private final Calendar _calendar = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
  private final Date _calendarDate = new Date(_calendar.getTimeInMillis());
  private final Path baseDir;
  private final String dataStructure;
  /** The end date with a time of 0 for none. */
  public final Date endDate = new Date(0L);
  private final int FILENAME_CHANNEL_INDEX;
  private final int FILENAME_DAY_INDEX;
  private final int FILENAME_LOCATION_INDEX;
  private final int FILENAME_NETWORK_INDEX;
  private final int FILENAME_SA_MINSIZE;
  private final int FILENAME_STATION_INDEX;
  private final int FILENAME_YEAR_INDEX;
  private final DataStructureComponent[] fileNameComponents;
  private final DirectoryStream.Filter<Path> filter;
  private final int PATH_YEAR_INDEX;
  private final DataStructureComponent[] pathComponents;
  private final StringBuilder sb = new StringBuilder(142);
  /** The start date with a time of 0 for none. */
  public final Date startDate = new Date(0L);

  /**
   * Create the data structure format.
   * 
   * @param baseDir the base directory.
   * @param dataStructure the data structure format text.
   * @throws IllegalArgumentException if the base directory is not a directory or the data structure
   *         format text is invalid.
   */
  public DataStructureFormat(final Path baseDir, final String dataStructure)
      throws IllegalArgumentException {
    if (baseDir == null || !Files.isDirectory(baseDir)) {
      throw new IllegalArgumentException("basedir is not a directory (" + baseDir + ")");
    }
    if (dataStructure == null || dataStructure.isEmpty()) {
      throw new IllegalArgumentException("Empty data structure");
    }
    if (baseDir.toString().indexOf(DS_CONFIG_DELIM_CHAR) != -1) {
      throw new IllegalArgumentException(
          "basedir cannot contain `" + DS_CONFIG_DELIM_CHAR + "' (" + baseDir + ")");
    }

    int index;
    this.baseDir = baseDir;
    this.dataStructure = dataStructure;
    String sa[];
    sa = split(dataStructure, PATH_DELIMITERS);

    int pathYearIndex = -1;
    // determine the path items
    pathComponents = new DataStructureComponent[sa.length - 1];
    for (index = 0; index < pathComponents.length; index++) {
      pathComponents[index] = DataStructureComponent.parse(sa[index]);
      if (pathComponents[index] == DataStructureComponent.YEAR) {
        pathYearIndex = index;
      }
    }
    PATH_YEAR_INDEX = pathYearIndex;

    int fStationIndex = -1;
    int fChannelIndex = -1;
    int fNetworkIndex = -1;
    int fLocationIndex = -1;
    int fYearIndex = -1;
    int fDayIndex = -1;
    // last entry is the file name
    String fileName = sa[sa.length - 1];
    sa = splitFileName(fileName);
    fileNameComponents = new DataStructureComponent[sa.length];
    for (index = 0; index < fileNameComponents.length; index++) {
      fileNameComponents[index] = DataStructureComponent.parse(sa[index]);
      switch (fileNameComponents[index]) {
        case STA:
          fStationIndex = index;
          break;
        case CHAN:
          fChannelIndex = index;
          break;
        case NET:
          fNetworkIndex = index;
          break;
        case LOC:
          fLocationIndex = index;
          break;
        case YEAR:
          fYearIndex = index;
          break;
        case DAY:
          fDayIndex = index;
          break;
        default:
          break;
      }
    }
    // ensure required DataStructureComponents are present
    if (fStationIndex == -1) {
      throw new IllegalArgumentException(
          "Could not determine station index (" + dataStructure + ")");
    }
    if (fChannelIndex == -1) {
      throw new IllegalArgumentException(
          "Could not determine channel index (" + dataStructure + ")");
    }
    if (fStationIndex == -1) {
      throw new IllegalArgumentException(
          "Could not determine network index (" + dataStructure + ")");
    }
    if (fLocationIndex == -1) {
      throw new IllegalArgumentException(
          "Could not determine location index (" + dataStructure + ")");
    }
    if (fYearIndex == -1) {
      throw new IllegalArgumentException("Could not determine year index (" + dataStructure + ")");
    }
    if (fDayIndex == -1) {
      throw new IllegalArgumentException("Could not determine day index (" + dataStructure + ")");
    }

    FILENAME_NETWORK_INDEX = fNetworkIndex;
    FILENAME_STATION_INDEX = fStationIndex;
    FILENAME_LOCATION_INDEX = fLocationIndex;
    FILENAME_CHANNEL_INDEX = fChannelIndex;
    FILENAME_YEAR_INDEX = fYearIndex;
    FILENAME_DAY_INDEX = fDayIndex;
    FILENAME_SA_MINSIZE = getMinSize(FILENAME_STATION_INDEX, FILENAME_CHANNEL_INDEX,
        FILENAME_NETWORK_INDEX, FILENAME_LOCATION_INDEX, FILENAME_YEAR_INDEX, FILENAME_DAY_INDEX);

    filter = new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path p) throws IOException {
        try {
          if (Files.isHidden(p)) {
            return false;
          }
          if (!Files.isDirectory(p)) {
            if (Files.notExists(p)) {
              LOGGER.debug("path does not exist: {}", p);
              return false;
            }
            if (Files.exists(p)) {
              return true;
            }
            LOGGER.debug("could not determine if the path exists: {}", p);
            return false;
          }
        } catch (Exception ex) {
          return false;
        }
        return true;
      }
    };
  }

  private void append(StringBuilder sb, ChannelInfo channelInfo, Calendar calendar, char sepChar,
      DataStructureComponent dsc) {
    int value;
    switch (dsc) {
      case CHAN:
        sb.append(sepChar);
        sb.append(channelInfo.getChannel());
        break;
      case CHAN_TYPE:
        sb.append(sepChar);
        sb.append(channelInfo.getChannel());
        sb.append(FILE_NAME_DELIMTER);
        sb.append(TYPE_TEXT_DATA);
        break;
      case DAY:
        sb.append(sepChar);
        value = calendar.get(Calendar.DAY_OF_YEAR);
        if (value < 100) {
          sb.append('0');
        }
        if (value < 10) {
          sb.append('0');
        }
        sb.append(value);
        break;
      case LOC:
        sb.append(sepChar);
        sb.append(channelInfo.getLocation());
        break;
      case NET:
        sb.append(sepChar);
        sb.append(channelInfo.getNetwork());
        break;
      case STA:
        sb.append(sepChar);
        sb.append(channelInfo.getStation());
        break;
      case TYPE:
        sb.append(sepChar);
        sb.append(TYPE_TEXT_DATA);
        break;
      case YEAR:
        sb.append(sepChar);
        value = calendar.get(Calendar.YEAR);
        sb.append(value);
        break;
    }
  }

  /**
   * Append the configuration string.
   * 
   * @param sb the string builder.
   */
  public void appendConfigString(StringBuilder sb) {
    appendConfigString(sb, this);
  }

  @Override
  public DataStructureFormat clone() {
    DataStructureFormat dsf = new DataStructureFormat(baseDir, dataStructure);
    dsf.startDate.setTime(startDate.getTime());
    dsf.endDate.setTime(endDate.getTime());
    return dsf;
  }

  private void copyFiles(DataStructureFormat odsf) {
    copyFilesNow(odsf, baseDir, getFilter());
  }

  private void copyFilesNow(DataStructureFormat odsf, Path dir,
      DirectoryStream.Filter<Path> filter) {
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, filter)) {
      for (Path p : ds) {
        String channel = getChannelForPath(p, null, null);
        if (channel == null) {
          continue;
        }
        // if directory
        if (channel.isEmpty()) {
          copyFilesNow(odsf, p, filter);
        } else {
          String[] sa = splitFileName(p);
          long time = parseDate(p, sa, null, null);
          if (time > 0L) {
            Path t = Paths.get(odsf.getFileName(new ChannelInfo(channel), time));
            if (verbose) {
              System.out.printf("%s %s\n", p, t);
            }
            Path parent = t.getParent();
            if (parent != null) {
              Files.createDirectories(parent);
            }
            Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
          } else if (time == -1L) {
            LOGGER.warn("copyFiles date is invalid {}", p);
          }
        }
      }
    } catch (IOException ex) {
      LOGGER.warn("copyFiles error", ex);
    }
  }

  /**
   * Find the channels.
   * 
   * @param channels the channel list or null if none.
   * @param source the source.
   * @param minDate the start date or null if none.
   * @param maxDate the start date or null if none.
   * @return an empty string if success, an error message otherwise.
   */
  public String findChannels(List<String> channels, SeismicDataSource source, Date minDate,
      Date maxDate) {
    String error = findChannelsNow(channels, source, baseDir, getFilter(), minDate, maxDate);
    if (channels != null && !channels.isEmpty()) {
      Collections.sort(channels);
    }
    return error;
  }

  /**
   * Find the channels.
   * 
   * @param channels the channel list or null if none.
   * @param source the source.
   * @param dir the directory.
   * @param filter the filter.
   * @param minDate the start date or null if none.
   * @param maxDate the start date or null if none.
   * @return an empty string if success, an error message otherwise.
   */
  private String findChannelsNow(List<String> channels, SeismicDataSource source, Path dir,
      DirectoryStream.Filter<Path> filter, Date minDate, Date maxDate) {
    String error = EMPTY_STRING;
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, filter)) {
      for (Path p : ds) {
        String channel = getChannelForPath(p, minDate, maxDate);
        if (channel == null) {
          continue;
        }
        // if directory
        if (channel.isEmpty()) {
          findChannelsNow(channels, source, p, filter, minDate, maxDate);
        } else if (channels != null && !channels.contains(channel)) {
          if (source == null) {
            channels.add(channel);
          } else {
            ChannelUtil.addChannel(channels,
                new ChannelGroupInfo(channel, GroupsType.NETWORK_AND_SITE), source);
          }
        }
      }
    } catch (IOException ex) {
      error = ex.toString();
    }
    return error;
  }

  /**
   * Get the base directory.
   * 
   * @return the base directory.
   */
  public Path getBaseDir() {
    return baseDir;
  }

  /**
   * Get the channel for the specified path.
   * 
   * @param p the path.
   * @param minDate the start date or null if none.
   * @param maxDate the start date or null if none.
   * @return null if the path does not exist or should be ignored, an empty string if the path is a
   *         directory, otherwise the channel.
   */
  private String getChannelForPath(Path p, Date minDate, Date maxDate) {
    if (Files.isDirectory(p)) {
      // if year is in the path
      if (PATH_YEAR_INDEX != -1) {
        p = baseDir.relativize(p);
        // if year directory
        if (p.getNameCount() == PATH_YEAR_INDEX + 1) {
          int year = LocalFileUtil.getYear(p.getName(PATH_YEAR_INDEX).toString(), true);
          if (year < 0) {
            LOGGER.warn("invalid year in path: {}", p);
            return null;
          }
          if (startDate.getTime() != 0L && year < getYear(startDate)) {
            return null;
          }
          if (endDate.getTime() != 0L && year > getYear(endDate)) {
            return null;
          }
        }
      }
      return EMPTY_STRING;
    }
    String[] sa = splitFileName(p);
    return parseChannel(p, sa, minDate, maxDate);
  }

  /**
   * Get the data structure format text.
   * 
   * @return the data structure format text.
   */
  public String getDataStructure() {
    return dataStructure;
  }

  /**
   * Get the file name.
   * 
   * @param channelInfo the channel information.
   * @param millis the time in UTC milliseconds from the epoch.
   * @return the file name.
   */
  public String getFileName(ChannelInfo channelInfo, long millis) {
    String fileName;
    char sepChar = PATH_DELIMETER;
    synchronized (sb) {
      sb.setLength(0);
      sb.append(baseDir);
      synchronized (_calendar) {
        _calendar.setTimeInMillis(millis);
        for (DataStructureComponent dsc : pathComponents) {
          append(sb, channelInfo, _calendar, sepChar, dsc);
        }
      }
      for (DataStructureComponent dsc : fileNameComponents) {
        append(sb, channelInfo, _calendar, sepChar, dsc);
        sepChar = FILE_NAME_DELIMTER;
      }
      fileName = sb.toString();
      sb.setLength(0);
    }
    return fileName;
  }

  /**
   * Get the file name.
   * 
   * @param station the station.
   * @param millis the time in UTC milliseconds from the epoch.
   * @return the file name.
   */
  public String getFileName(String station, long millis) {
    return getFileName(new ChannelInfo(station), millis);
  }

  private DirectoryStream.Filter<Path> getFilter() {
    return filter;
  }

  private int getYear(Date date) {
    synchronized (_calendar) {
      _calendar.setTime(date);
      return _calendar.get(Calendar.YEAR);
    }
  }

  /**
   * Parse the file name to get the channel.
   * 
   * @param p the path.
   * @param sa the split file names.
   * @param minDate the start date or null if none.
   * @param maxDate the start date or null if none.
   * @return the channel or null if the channel should be ignored or could not be determined.
   * @see #splitFileName(String)
   */
  private String parseChannel(Path p, String[] sa, Date minDate, Date maxDate) {
    String channel = null;
    try {
      long time = parseDate(p, sa, minDate, maxDate);
      if (time > 0L) {
        if (startDate.getTime() != 0L) {
          if (time < startDate.getTime()) {
            return null;
          }
        }
        if (endDate.getTime() != 0L) {
          if (time > endDate.getTime()) {
            return null;
          }
        }
        channel = ChannelUtil.getFormattedScnl(sa[FILENAME_STATION_INDEX],
            sa[FILENAME_CHANNEL_INDEX], sa[FILENAME_NETWORK_INDEX], sa[FILENAME_LOCATION_INDEX]);
        // validate path
        String s = getFileName(channel, time);
        if (!p.toString().equals(s)) {
          LOGGER.warn("parseChannel path is invalid {}, expected {}", p, s);
          channel = null;
        }
      } else if (time == -1L) {
        LOGGER.warn("parseChannel date is invalid {}", p);
      }
    } catch (Exception ex) {
    }
    return channel;
  }

  /**
   * Parse the file name to get the date.
   * 
   * @param p the path.
   * @param sa the split file names.
   * @param minDate the start date or null if none.
   * @param maxDate the start date or null if none.
   * @return the number of milliseconds since January 1, 1970, 00:00:00 UTC, -1 if invalid, or 0 if
   *         the date could not be determined or is not in range.
   */
  private long parseDate(Path p, String[] sa, Date minDate, Date maxDate) {
    try {
      if (sa.length >= FILENAME_SA_MINSIZE) {
        synchronized (_calendar) {
          _calendar.clear();
          _calendar.set(Calendar.YEAR, Integer.parseInt(sa[FILENAME_YEAR_INDEX]));
          _calendar.set(Calendar.DAY_OF_YEAR, Integer.parseInt(sa[FILENAME_DAY_INDEX]));
          long time = _calendar.getTimeInMillis();
          if (time > System.currentTimeMillis()) {
            return -1L;
          }
          if (minDate != null && (minDate.getTime() == 0 || time < minDate.getTime())) {
            minDate.setTime(time);
          }
          if (maxDate != null && time > maxDate.getTime()) {
            maxDate.setTime(time);
          }
          _calendarDate.setTime(time);
          return _calendarDate.getTime();
        }
      }
    } catch (Exception ex) {
    }
    return 0L;
  }

  private String[] splitFileName(Path p) {
    return splitFileName(p.getFileName().toString());
  }

  private String[] splitFileName(String fileName) {
    return split(fileName, FILE_NAME_DELIMITERS);
  }
}
