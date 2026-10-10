package gov.usgs.volcanoes.swarm.data.fdsnws;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.util.List;
import java.util.logging.Level;

import gov.usgs.volcanoes.swarm.ChannelInfo;
import gov.usgs.volcanoes.swarm.ChannelUtil;
import gov.usgs.volcanoes.swarm.data.SeismicDataSource;

/**
 * Web service utility methods.
 *
 * @author Kevin Frechette (ISTI)
 */
public class WebServiceUtils implements WebServiceConst {
  /** Debug level. */
  private static Level debugLevel;

  /** Default debug level. */
  private static final Level defaultDebugLevel = Level.FINEST;

  /** Swarm web services property key prefix. */
  public static final String SWARM_WS_PROP_KEY_PREFIX = "SWARM_WS_";

  static {
    try {
      final String s = getProperty(SWARM_WS_PROP_KEY_PREFIX + "DEBUG");
      if (s != null) {
        if (Boolean.valueOf(s)) {
          debugLevel = defaultDebugLevel;
        } else {
          debugLevel = Level.parse(s);
        }
      }
    } catch (final Exception ex) {
      //
    }
  }

  /**
   * Add the channel.
   * 
   * @param channels the list of channels.
   * @param ch       the channel information.
   * @param source   the seismic data source.
   * @return the channel information text.
   */
  public static String addChannel(final List<String> channels, final ChannelInfo ch, final SeismicDataSource source) {
    return ChannelUtil.addChannel(channels, ch, source);
  }

  /**
   * Assign the channels.
   * 
   * @param channels the list of channels.
   * @param source   the seismic data source.
   */
  public static void assignChannels(final List<String> channels, final SeismicDataSource source) {
    ChannelUtil.assignChannels(channels, source);
  }

  /**
   * Create a buffered reader.
   * 
   * @param conn the connection.
   * @return the buffered reader.
   * @throws IOException if an I/O error occurs.
   */
  public static BufferedReader createBufferedReader(URLConnection conn) throws IOException {
    return new BufferedReader(new InputStreamReader(conn.getInputStream()));
  }

  /**
   * Get the property for the specified key.
   * 
   * @param key the name of the system property.
   * @return the property or null if none.
   */
  public static String getProperty(final String key) {
    return getProperty(key, (String) null);
  }

  /**
   * Get the property for the specified key.
   * 
   * @param key the name of the system property.
   * @param def a default value.
   * @return the property or the default value if none.
   */
  public static String getProperty(final String key, final String def) {
    String s = null;
    try {
      s = System.getProperty(key);
      if (s == null) {
        s = System.getenv(key);
      }
    } catch (final Exception ex) {
      //
    }
    if (s == null) {
      s = def;
    }
    return s;
  }

  /**
   * Get the regular expression for the specified channel, location, network or
   * station value.
   * 
   * @param s the text or null for all.
   * @return the regular expression or null for all.
   */
  public static String getRegex(String s) {
    if (WebServiceStationClient.isAll(s)) {
      return null;
    }
    int length = s.length();
    boolean replace = false;
    for (int index = 0; index < s.length(); index++) {
      switch (s.charAt(index)) {
      case VALUE_DELIMITER:
        replace = true;
        break;
      case WILDCARD_SINGLE:
        replace = true;
        break;
      case WILDCARD_ZERO_TO_MANY:
        length++;
        replace = true;
        break;
      default:
        break;
      }
    }
    if (!replace) {
      return s;
    }
    char c;
    final StringBuilder sb = new StringBuilder(length);
    for (int index = 0; index < s.length(); index++) {
      switch (c = s.charAt(index)) {
      case VALUE_DELIMITER:
        c = REGEX_DELIMITER;
        break;
      case WILDCARD_SINGLE:
        c = REGEX_SINGLE;
        break;
      case WILDCARD_ZERO_TO_MANY:
        sb.append(REGEX_SINGLE);
        c = REGEX_ZERO_TO_MANY;
        break;
      default:
        break;
      }
      sb.append(c);
    }
    s = sb.toString();
    return s;
  }

  /**
   * Determines if debug logging is enabled.
   * 
   * @return true if debug logging is enabled, false otherwise.
   */
  public static boolean isDebug() {
    return isDebug(defaultDebugLevel);
  }

  /**
   * Determines if debug logging is enabled.
   * 
   * @param level the message level.
   * @return true if debug logging is enabled, false otherwise.
   */
  public static boolean isDebug(final Level level) {
    return debugLevel != null && level.intValue() >= debugLevel.intValue();
  }
}
