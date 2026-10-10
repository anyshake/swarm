package gov.usgs.volcanoes.swarm.file;

import gov.usgs.volcanoes.swarm.SwarmConst;
import gov.usgs.volcanoes.swarm.SwarmUtil;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.UIManager;
import edu.iris.dmc.seedcodec.Codec;
import edu.iris.dmc.seedcodec.CodecException;
import edu.iris.dmc.seedcodec.DecompressedData;
import edu.iris.dmc.seedcodec.UnsupportedCompressionType;
import edu.sc.seis.seisFile.mseed.Blockette1000;
import edu.sc.seis.seisFile.mseed.Btime;
import edu.sc.seis.seisFile.mseed.DataHeader;
import edu.sc.seis.seisFile.mseed.DataRecord;

public class LocalFileUtil implements DataStructureConst, SwarmConst {
  private static final String[] DIR_CHOOSER_HIDE_LABEL_TEXTS;
  /** YYYYYMMDD parser */
  private static final DateTimeFormatter YYYYYMMDD_PARSER;

  static {
    String s = System.getProperty("DIR_CHOOSER_HIDE_LABEL_TEXTS");
    if (s == null) {
      DIR_CHOOSER_HIDE_LABEL_TEXTS = new String[] { "Files of Type", "File Format" };
    } else if ((s = s.trim()).isEmpty()) {
      DIR_CHOOSER_HIDE_LABEL_TEXTS = new String[0];
    } else {
      DIR_CHOOSER_HIDE_LABEL_TEXTS = s.split(",");
    }
    YYYYYMMDD_PARSER = createFormatter("yyyyMMdd", "yyyy-MM-dd");
  }

  /**
   * Append the formatted SCNL.
   *
   * @param station the station name.
   * @param channel the channel name.
   * @param network the network name.
   * @param location the location name.
   * @param sepChar the separator character.
   * @param sb the string builder or null to create a new one.
   * @return the string builder.
   */
  public static StringBuilder appendFormattedScnl(String station, String channel, String network,
      String location, char sepChar, StringBuilder sb) {
    station = station.trim();
    channel = channel.trim();
    network = network.trim();
    if (location == null) {
      location = EMPTY_STRING;
    } else {
      location = location.trim();
    }
    if (sb == null) {
      sb = new StringBuilder(
          station.length() + channel.length() + network.length() + location.length() + 3);
    }
    sb.append(station);
    sb.append(sepChar);
    sb.append(channel);
    sb.append(sepChar);
    sb.append(network);
    if (!location.isEmpty()) {
      sb.append(sepChar);
      sb.append(location);
    }
    return sb;
  }

  /**
   * Create a browse button.
   *
   * @return a browse button.
   */
  public static JButton createBrowseButton() {
    final JButton browseButton;
    final Icon icon = UIManager.getIcon("FileView.directoryIcon");
    if (icon == null) {
      String s = UIManager.getString("FormView.browseFileButtonText");
      if (s == null) {
        s = "Browse...";
      }
      browseButton = new JButton(s);
    } else {
      browseButton = new JButton(icon);
    }
    return browseButton;
  }

  /**
   * Create the directory chooser.
   *
   * @return the directory chooser.
   */
  public static JFileChooser createDirectoryChooser() {
    JFileChooser dirChooser = new JFileChooser();
    dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    // disable the "All files" filter
    dirChooser.setAcceptAllFileFilterUsed(false);
    if (DIR_CHOOSER_HIDE_LABEL_TEXTS.length != 0) {
      hideComponent(dirChooser, DIR_CHOOSER_HIDE_LABEL_TEXTS);
    }
    return dirChooser;
  }

  /**
   * Create the formatter for formatting or parsing.
   *
   * @param patterns the patterns.
   * @return the formatter.
   */
  private static DateTimeFormatter createFormatter(String... patterns) {
    DateTimeFormatterBuilder dtfb = new DateTimeFormatterBuilder();
    if (patterns.length == 1) { // if only one format
      dtfb.appendPattern(patterns[0]);
    } else {
      for (int i = 0; i < patterns.length; i++) {
        dtfb.optionalStart().appendOptional(DateTimeFormatter.ofPattern(patterns[i])).optionalEnd();
      }
    }
    dtfb.parseDefaulting(ChronoField.NANO_OF_DAY, 0);
    return dtfb.toFormatter().withZone(ZoneOffset.UTC);
  }

  /**
   * Get the data.
   *
   * @param dr the data record.
   * @param b1000 the blockette 1000.
   * @return the data.
   * @throws UnsupportedCompressionType if the compression type is not supported.
   * @throws CodecException if there is an error while decompressing the data.
   */
  public static int[] getData(DataRecord dr, Blockette1000 b1000)
      throws UnsupportedCompressionType, CodecException {
    final int type = b1000.getEncodingFormat();
    final byte[] data = dr.getData();
    final boolean swapNeeded = b1000.getWordOrder() == 0;
    final Codec codec = new Codec();
    final DecompressedData decomp =
        codec.decompress(type, data, dr.getHeader().getNumSamples(), swapNeeded);
    return decomp.getAsInt();
  }

  /**
   * Get the station key for the specified data.
   *
   * @param dh the data header.
   * @return the station key.
   */
  public static String getStation(DataHeader dh) {
    return getStation(dh.getStationIdentifier(), dh.getChannelIdentifier(), dh.getNetworkCode(),
        dh.getLocationIdentifier());
  }

  /**
   * Get the station key for the station SCNL.
   *
   * @return the station key.
   */
  public static String getStation(String station) {
    return station.replace(CHANNEL_SEP_CHAR, STATION_SEP_CHAR);
  }

  /**
   * Get the station key.
   *
   * @param station the station name.
   * @param channel the channel name.
   * @param network the network name.
   * @param location the location name.
   * @return the station key.
   */
  public static final String getStation(String station, String channel, String network,
      String location) {
    return appendFormattedScnl(station, channel, network, location, STATION_SEP_CHAR, null)
        .toString();
  }

  /**
   * Get the string for the specified object.
   *
   * @param obj the object or null if none.
   * @param defaultValue the default value if the object is null.
   * @return the string.
   */
  public static String getString(Object obj, String defaultValue) {
    String s = defaultValue;
    if (obj != null) {
      s = obj.toString();
    }
    return s;
  }

  /**
   * Get the current year.
   * 
   * @return the current year.
   */
  public static int getYear() {
    return Year.now(ZoneOffset.UTC).getValue();
  }

  /**
   * Get the year for the text.
   * 
   * @param s the text.
   * @param validate true to validate the year.
   * @return the year or negative number if invalid.
   */
  public static int getYear(final String s, boolean validate) {
    int year = -1;
    try {
      year = Integer.parseInt(s);
      if (validate && (year < 1970 || year > getYear())) {
        return -2;
      }
    } catch (Exception ex) {
    }
    return year;
  }

  /**
   * Hide the components with the specified label text.
   * 
   * @param parent the parent container.
   * @param label the label or null if none.
   * @param comboBox the combo box or null if none.
   * @param labelTexts the label text values.
   */
  private static void hideComponent(Container parent, JLabel label, JComboBox<?> comboBox,
      String... labelTexts) {
    if (labelTexts != null && labelTexts.length != 0) {
      for (Component comp : parent.getComponents()) {
        if (comp instanceof JLabel) {
          // if label has one the specified text values
          final JLabel lcomp = (JLabel) comp;
          for (String labelText : labelTexts) {
            if (lcomp.getText().startsWith(labelText)) {
              label = lcomp;
              label.setVisible(false); // hide the label
            }
            if (label != null && comboBox != null) {
              break;
            }
          }
        } else if (comp instanceof javax.swing.JComboBox) {
          // if combo box has no selection
          final JComboBox<?> cbcomp = (JComboBox<?>) comp;
          if (cbcomp.getSelectedIndex() == -1) {
            comboBox = cbcomp;
            comboBox.setVisible(false); // hide the combo box
            if (label != null && comboBox != null) {
              break;
            }
          }
        } else if (comp instanceof Container) {
          hideComponent((Container) comp, label, comboBox, labelTexts);
        }
        if (label != null && comboBox != null) {
          break;
        }
      }
      // hide parent if it has no visible components
      if (label != null && comboBox != null) {
        for (Component comp : parent.getComponents()) {
          if (comp.isVisible()) {
            return;
          }
        }
        parent.setVisible(false); // hide empty container
      }
    }
  }

  /**
   * Hide the components with the specified label text.
   *
   * @param parent the parent container.
   * @param labelTexts the label text values.
   */
  public static void hideComponent(Container parent, String... labelTexts) {
    hideComponent(parent, (JLabel) null, (JComboBox<?>) null, labelTexts);
  }

  /**
   * Get the instant for the text.
   *
   * @param s the text.
   * @return the instant.
   * @throws DateTimeParseException if unable to parse the requested result
   */
  public static Instant parseInstant(final String s) throws DateTimeParseException {
    return Instant.from(SwarmUtil.parseDateText(s));
  }

  /**
   * Get the instant for the text.
   *
   * @param s the text.
   * @param end true if the end date.
   * @return the instant.
   * @throws DateTimeParseException if unable to parse the requested result
   */
  public static Instant parseInstant(final String s, boolean end) throws DateTimeParseException {
    Instant instant;
    int index = s.indexOf('T');
    if (index != -1) {
      instant = parseInstant(s);
    } else if ((index = s.indexOf(' ')) != -1 && index == s.lastIndexOf(' ')) {
      instant = parseInstant(s.replace(' ', 'T'));
    } else {
      instant = Instant.from(YYYYYMMDD_PARSER.parse(s));
      if (end) {
        instant = instant.plus(1L, ChronoUnit.DAYS).minusMillis(1L);
      }
    }
    return instant;
  }

  /**
   * Set the time in the calendar.
   *
   * @param cal the calendar.
   * @param dr the data record.
   */
  public static void setTime(Calendar cal, DataRecord dr) {
    final DataHeader dh = dr.getHeader();
    Btime btime = dh.getStartBtime();
    cal.clear();
    cal.set(Calendar.YEAR, btime.getYear());
    cal.set(Calendar.DAY_OF_YEAR, btime.getDayOfYear());
    cal.set(Calendar.HOUR_OF_DAY, btime.getHour());
    cal.set(Calendar.MINUTE, btime.getMin());
    cal.set(Calendar.SECOND, btime.getSec());
    cal.set(Calendar.MILLISECOND, btime.getTenthMilli() / 10);
  }
}
