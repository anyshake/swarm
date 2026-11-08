package gov.usgs.volcanoes.swarm.data.seedlink;

import gov.usgs.volcanoes.swarm.AbstractChannelInfo;
import gov.usgs.volcanoes.swarm.ChannelUtil;
import gov.usgs.volcanoes.swarm.GroupsType;
import gov.usgs.volcanoes.swarm.data.SeismicDataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SeedLink channel information.
 * 
 * @author Kevin Frechette (ISTI)
 */
public class SeedLinkChannelInfo extends AbstractChannelInfo {
  /** Data type. */
  public static final String DATA_TYPE = "D";

  private final List<String> channels = new ArrayList<String>();

  /** Formatted SCNL strings already kept in {@link #channels}. */
  private final Set<String> knownChannels = new HashSet<String>();

  /** The data source. */
  private final SeedLinkSource dataSource;


  private double latitude = Double.NaN;
  private double longitude = Double.NaN;
  private double elevation = Double.NaN;

  private String location;
  private String network;
  private String channel;
  private String station;
  private String type;

  /**
   * Constructor.
   * 
   * @param dataSource data source
   * @param infoStr info string
   * @throws Exception when things go wrong
   */
  public SeedLinkChannelInfo(SeedLinkSource dataSource, String infoStr) throws Exception {
    this.dataSource = dataSource;
    readStreams(infoStr);
  }

  /**
   * Add a channel for each data stream in a SeedLink INFO STREAMS reply. A large server lists tens
   * of thousands of streams and only the station and stream tags matter, so this walks the reply
   * once instead of running the general SimpleXmlParser over it, and reads it the same way:
   * attribute values have their entities decoded and their line breaks and tabs made spaces;
   * comments, CDATA, declarations and anything after the root element are skipped; and a reply
   * whose root element never closes, or that has a broken tag or an unknown entity, is an error.
   *
   * @param s the reply
   * @throws Exception if the reply cannot be read
   */
  private void readStreams(String s) throws Exception {
    final int n = s.length();
    final StringBuilder value = new StringBuilder();
    int depth = 0;
    // The text before the first tag and after a declaration is skipped; the rest is checked.
    boolean checkText = false;
    for (int i = s.indexOf('<'); i >= 0 && i + 1 < n; i = s.indexOf('<', i + 1)) {
      final char first = s.charAt(i + 1);
      if (first == '/') {
        i = s.indexOf('>', i + 2);
        if (i < 0) {
          break;
        }
        if (--depth == 0) {
          return;
        }
        checkText = true;
      } else if (first == '?' || s.startsWith("!DOCTYPE", i + 1)) {
        i = s.indexOf('>', i + 2);
        if (i < 0) {
          break;
        }
        checkText = false;
      } else if (s.startsWith("!--", i + 1)) {
        i = s.indexOf("-->", i + 3);
        if (i < 0) {
          break;
        }
        i += 2;
        checkText = true;
      } else if (s.startsWith("![CDATA[", i + 1)) {
        i = s.indexOf("]]>", i + 9);
        if (i < 0) {
          break;
        }
        i += 2;
        checkText = true;
      } else {
        // The name always takes the first character, then runs to white space, '>' or '/'. A line
        // feed right after a carriage return does not count, as everywhere in SimpleXmlParser.
        int k = i + 2;
        if (first == '\r' && k < n && s.charAt(k) == '\n') {
          k++;
        }
        while (k < n && !isTagNameEnd(s.charAt(k))) {
          k++;
        }
        final boolean isStation = k - i - 1 == 7 && s.startsWith("station", i + 1);
        final boolean isStream = k - i - 1 == 6 && s.startsWith("stream", i + 1);
        String nameOrSeedname = null;
        String networkOrLocation = null;
        String streamType = null;
        boolean empty = false;
        while (true) {
          if (k >= n) {
            throw new Exception("missing end tag");
          }
          final char c = s.charAt(k);
          if (c == '>') {
            break;
          }
          if (c == '/') {
            if (k + 1 >= n) {
              throw new Exception("missing end tag");
            }
            if (s.charAt(k + 1) != '>') {
              throw new Exception("Expected > for tag");
            }
            k++;
            empty = true;
            break;
          }
          if (isSpace(c)) {
            k++;
            continue;
          }
          // Like a tag name, an attribute name always takes its first character.
          final int keyFrom = k++;
          while (k < n && s.charAt(k) != '=' && !isSpace(s.charAt(k))) {
            k++;
          }
          final int keyLength = k - keyFrom;
          while (k < n && isSpace(s.charAt(k))) {
            k++;
          }
          if (k < n && s.charAt(k) != '=') {
            throw new Exception("Error in attribute processing.");
          }
          k++;
          while (k < n && isSpace(s.charAt(k))) {
            k++;
          }
          if (k >= n) {
            throw new Exception("missing end tag");
          }
          final char quote = s.charAt(k);
          if (quote != '"' && quote != '\'') {
            throw new Exception("Error in attribute processing");
          }
          int slot = 0;
          if (isStation) {
            slot = isKey(s, keyFrom, keyLength, "name") ? 1
                : isKey(s, keyFrom, keyLength, "network") ? 2 : 0;
          } else if (isStream) {
            slot = isKey(s, keyFrom, keyLength, "seedname") ? 1
                : isKey(s, keyFrom, keyLength, "location") ? 2
                    : isKey(s, keyFrom, keyLength, "type") ? 3 : 0;
          }
          value.setLength(0);
          k = readValue(s, k + 1, quote, slot == 0 ? null : value) + 1;
          if (slot == 1) {
            nameOrSeedname = value.toString();
          } else if (slot == 2) {
            networkOrLocation = value.toString();
          } else if (slot == 3) {
            streamType = value.toString();
          }
        }
        i = k;
        if (isStation) {
          clearStation();
          station = nameOrSeedname;
          network = networkOrLocation;
        } else if (isStream) {
          clearChannel();
          channel = nameOrSeedname;
          location = networkOrLocation;
          type = streamType;
          if (station != null && network != null && channel != null && location != null
              && DATA_TYPE.equals(type)) {
            ChannelUtil.addChannel(channels, knownChannels, this, getSource());
          }
        }
        if (!empty) {
          depth++;
        } else if (depth == 0) {
          return;
        }
        checkText = true;
      }
      if (checkText) {
        // The text up to the next tag: only an unknown entity in it matters.
        for (int k = i + 1; k < n && s.charAt(k) != '<'; k++) {
          if (s.charAt(k) == '&') {
            final int semi = s.indexOf(';', k + 1);
            if (semi < 0) {
              throw new Exception("missing end tag");
            }
            entity(s.substring(k + 1, semi));
            k = semi;
          }
        }
      }
    }
    throw new Exception("missing end tag");
  }

  private static boolean isTagNameEnd(char c) {
    return c == '>' || c == '/' || isSpace(c);
  }

  /** Same as {@link Character#isWhitespace(char)}, without the table lookup for letters. */
  private static boolean isSpace(char c) {
    return c <= ' ' ? Character.isWhitespace(c) : c >= 0x80 && Character.isWhitespace(c);
  }

  private static boolean isKey(String s, int from, int length, String key) {
    return length == key.length() && s.startsWith(key, from);
  }

  /**
   * Read a quoted attribute value: entities are decoded, and each space, tab, line feed and
   * carriage return becomes one space, except that a line feed right after a carriage return is
   * dropped. As in SimpleXmlParser, a character right after such a carriage return is taken as it
   * is, so a second carriage return there does not drop the line feed after it.
   *
   * @param s the reply
   * @param from index just past the opening quote
   * @param quote the quote character
   * @param out where to put the value, or null to only check it
   * @return index of the closing quote
   * @throws Exception if the value has an unknown entity or no closing quote
   */
  private static int readValue(String s, int from, char quote, StringBuilder out)
      throws Exception {
    final int n = s.length();
    boolean afterCr = false;
    for (int k = from; k < n; k++) {
      final char c = s.charAt(k);
      if (afterCr) {
        afterCr = false;
        if (c == '\n') {
          continue;
        }
      } else if (c == '\r') {
        afterCr = true;
      }
      if (c == quote) {
        return k;
      }
      if (c == '&') {
        final int semi = s.indexOf(';', k + 1);
        if (semi < 0) {
          break;
        }
        final char e = entity(s.substring(k + 1, semi));
        if (out != null) {
          out.append(e);
        }
        k = semi;
      } else if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        if (out != null) {
          out.append(' ');
        }
      } else if (out != null) {
        out.append(c);
      }
    }
    throw new Exception("missing end tag");
  }

  /**
   * The character an XML entity stands for.
   *
   * @param name the entity name, between '&amp;' and ';'
   * @return the character
   * @throws Exception if the entity is unknown
   */
  private static char entity(String name) throws Exception {
    if ("lt".equals(name)) {
      return '<';
    } else if ("gt".equals(name)) {
      return '>';
    } else if ("amp".equals(name)) {
      return '&';
    } else if ("quot".equals(name)) {
      return '"';
    } else if ("apos".equals(name)) {
      return '\'';
    } else if (name.startsWith("#x")) {
      return (char) Integer.parseInt(name.substring(2), 16);
    } else if (name.startsWith("#")) {
      return (char) Integer.parseInt(name.substring(1));
    }
    throw new Exception("Unknown entity: &" + name + ";");
  }

  private void clearChannel() {
    channel = null;
    location = null;
    type = null;
  }

  private void clearStation() {
    station = null;
    network = null;
    clearChannel();
  }

  /**
   * Get the channel name.
   * 
   * @return the channel name.
   */
  public String getChannel() {
    return channel;
  }

  /**
   * Get the channels.
   * 
   * @return the list of channels.
   */
  public List<String> getChannels() {
    return channels;
  }

  /**
   * Get the groups.
   * 
   * @return the list of groups.
   */
  public List<String> getGroups() {
    return ChannelUtil.getGroups(this, GroupsType.NETWORK_AND_SITE);
  }

  /**
   * Get the latitude.
   * 
   * @return the latitude.
   */
  public double getLatitude() {
    return latitude;
  }

  /**
   * Get the location.
   * 
   * @return the location.
   */
  public String getLocation() {
    return location;
  }

  /**
   * Get the longitude.
   * 
   * @return the longitude.
   */
  public double getLongitude() {
    return longitude;
  }

  /**
   * Get the network name.
   * 
   * @return the network name.
   */
  public String getNetwork() {
    return network;
  }

  /**
   * Get the site name. A noop since siteName is not set.
   * 
   * @return the site name.
   */
  public String getSiteName() {
    return null;
  }

  /**
   * Get the seismic data source.
   * 
   * @return the seismic data source.
   */
  public SeismicDataSource getSource() {
    return dataSource;
  }

  /**
   * Get the station name.
   * 
   * @return the station name.
   */
  public String getStation() {
    return station;
  }

  @Override
  public double getHeight() {
    return elevation;
  }
}
