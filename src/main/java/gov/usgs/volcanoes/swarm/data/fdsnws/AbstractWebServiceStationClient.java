package gov.usgs.volcanoes.swarm.data.fdsnws;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.usgs.volcanoes.swarm.ChannelGroupInfo;
import gov.usgs.volcanoes.swarm.ChannelInfo;
import gov.usgs.volcanoes.swarm.GroupsType;
import gov.usgs.volcanoes.swarm.StationInfo;

public abstract class AbstractWebServiceStationClient implements ChannelProcessor, WebServiceStationClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractWebServiceStationClient.class);

  /** The base URL text. */
  private final String baseUrlText;
  /** The channel name. */
  private final String chan;
  /** The channel list. */
  private final List<String> channelList = WebServiceStationClient.createList();
  /** Channel processor list or empty list if none, not null */
  private List<ChannelProcessor> channelProcessorList = Collections.emptyList();
  /** The end date. */
  private final Date endDate;
  /** The error message. */
  private final StringBuilder error = new StringBuilder();
  /** The output level. */
  private OutputLevel level = OutputLevel.CHANNEL;
  /** The location name. */
  private final String loc;
  /** The network name. */
  private final String net;
  /** The network list. */
  private final List<String> networkList = WebServiceStationClient.createList();
  /** The station name. */
  private final String sta;
  /** The start date. */
  private final Date startDate;
  /** The station list. */
  private final List<StationInfo> stationList = WebServiceStationClient.createList();

  /**
   * Create the web service station client.
   * 
   * @param baseUrlText the base URL text.
   * @param net         the network or null if none.
   * @param sta         the station or null if none.
   * @param loc         the location or null if none.
   * @param chan        the channel or null if none.
   * @param startDate   the start date or null if none.
   * @param endDate     the end date or null if none.
   */
  public AbstractWebServiceStationClient(String baseUrlText, String net, String sta, String loc, String chan,
      Date startDate, Date endDate) {
    this.baseUrlText = baseUrlText;
    this.net = net;
    this.sta = sta;
    this.loc = loc;
    this.chan = chan;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  @Override
  public void addChannelProcessor(ChannelProcessor cp) {
    switch (channelProcessorList.size()) {
    case 0:
      channelProcessorList = Collections.singletonList(cp);
      break;
    case 1:
      ChannelProcessor old = channelProcessorList.get(0);
      channelProcessorList = new ArrayList<>();
      channelProcessorList.add(old);
      channelProcessorList.add(cp);
      break;
    default:
      channelProcessorList.add(cp);
      break;
    }
  }

  /**
   * Append an error.
   * 
   * @param o the object to append.
   */
  protected void appendError(Object o) {
    error.append(o);
  }

  /**
   * Append invalid line error with the default text.
   * 
   * @param line the line.
   * @see #appendInvalidLine(String, String)
   */
  protected void appendInvalidLine(String line) {
    appendInvalidLine(line, (String) null);
  }

  /**
   * Append invalid line error.
   * 
   * @param line the line.
   * @param s    the text to append before the line, null for the default (invalid
   *             number of columns) or empty if none.
   */
  protected void appendInvalidLine(String line, String s) {
    if (s == null) {
      error.append("invalid number of columns ");
    } else if (!s.isEmpty()) {
      error.append(s);
      // if does not end with space
      if (!Character.isWhitespace(s.charAt(s.length() - 1))) {
        error.append(' ');
      }
    }
    error.append("(");
    error.append(line);
    error.append(")\n");
  }

  /**
   * Clear the error message.
   */
  public void clearError() {
    if (error.length() != 0) {
      error.setLength(0);
    }
  }

  /**
   * Determine if latitude and longitude should be cleared.
   * 
   * @return true if latitude and longitude should be cleared, false otherwise.
   */
  protected boolean clearLatLon() {
    // clear if all networks
    return isAllNetworks();
  }

  /**
   * Close the connection.
   */
  protected void closeConnection(URLConnection conn) {
    if (conn instanceof HttpURLConnection) {
      ((HttpURLConnection) conn).disconnect();
    }
  }

  /**
   * Create the channel information.
   * 
   * @param stationInfo station information.
   * @param channel     channel name
   * @param location    location
   * @return the channel information.
   */
  protected ChannelInfo createChannelInfo(StationInfo stationInfo, String channel, String location) {
    return new ChannelGroupInfo(stationInfo, channel, location, getGroupsType());
  }

  /**
   * Create the channel information.
   * 
   * @param station   the station.
   * @param channel   the channel.
   * @param network   the network.
   * @param location  the location.
   * @param latitude  the latitude.
   * @param longitude the longitude.
   * @param elevation the elevation
   * @param siteName  the site name.
   * @return the channel information.
   */
  protected ChannelInfo createChannelInfo(String station, String channel, String network, String location,
      double latitude, double longitude, double elevation, String siteName) {
    if (clearLatLon()) {
      latitude = Double.NaN;
      longitude = Double.NaN;
      elevation = Double.NaN;
    }
    return new ChannelGroupInfo(station, channel, network, location, latitude, longitude, elevation, siteName,
        getGroupsType());
  }

  /**
   * Create the station information.
   * 
   * @param station the station.
   * @param network the network.
   * @return the station information.
   */
  protected StationInfo createStationInfo(String station, String network) {
    return new StationInfo(station, network, Double.NaN, Double.NaN, Double.NaN, null);
  }

  /**
   * Create the station information.
   * 
   * @param station   the station.
   * @param network   the network.
   * @param latitude  the latitude.
   * @param longitude the longitude.
   * @param elevation the elevation
   * @param siteName  the site name.
   * @return the station information.
   */
  protected StationInfo createStationInfo(String station, String network, double latitude, double longitude,
      double elevation, String siteName) {
    if (clearLatLon()) {
      latitude = Double.NaN;
      longitude = Double.NaN;
      elevation = Double.NaN;
    }
    return new StationInfo(station, network, latitude, longitude, elevation, siteName);
  }

  @Override
  public String fetch() {
    clearError();
    final URLConnection conn = openConnection();
    if (conn != null) {
      LOGGER.debug("web service {} {}", getLevel(), conn.getURL());
      try {
        fetch(conn);
      } catch (Exception ex) {
        error.append(ex);
      }
      closeConnection(conn);
    }
    String s = getError();
    clearError();
    if (s.isEmpty()) {
      s = null;
      // if fetching networks and network list is empty
      if (getLevel() == OutputLevel.NETWORK && getNetworkList().isEmpty()) {
        s = "no networks";
      }
    }
    return s;
  }

  /**
   * Fetch the stations.
   * 
   * @param conn the connection.
   * 
   * @throws Exception if an error occurs.
   */
  protected abstract void fetch(URLConnection conn) throws Exception;

  /**
   * Get the base URL text.
   * 
   * @return the base URL text.
   */
  protected String getBaseUrlText() {
    return baseUrlText;
  }

  @Override
  public String getChannel() {
    return chan;
  }

  @Override
  public List<String> getChannelList() {
    return channelList;
  }

  @Override
  public Date getEndDate() {
    return endDate;
  }

  /**
   * Get the error message.
   * 
   * @return the error message.
   */
  public String getError() {
    return error.toString();
  }

  /**
   * Get the groups type.
   * 
   * @return the groups type.
   */
  protected GroupsType getGroupsType() {
    return GroupsType.NETWORK;
  }

  @Override
  public OutputLevel getLevel() {
    return level;
  }

  @Override
  public String getLocation() {
    return loc;
  }

  @Override
  public String getNetwork() {
    return net;
  }

  @Override
  public List<String> getNetworkList() {
    return networkList;
  }

  @Override
  public Date getStartDate() {
    return startDate;
  }

  @Override
  public String getStation() {
    return sta;
  }

  @Override
  public List<StationInfo> getStationList() {
    return stationList;
  }

  /**
   * Get the URL.
   * 
   * @return the URL.
   * @throws MalformedURLException if the URL text is invalid.
   */
  protected URL getUrl() throws MalformedURLException {
    String s = getUrlText();
    LOGGER.trace(s);
    return new URL(s);
  }

  /**
   * Get the URL text.
   * 
   * @return the URL text.
   */
  protected String getUrlText() {
    return getBaseUrlText();
  }

  /**
   * Open a connection.
   * 
   * @return the connection or null if error.
   */
  protected URLConnection openConnection() {
    try {
      final URL url = getUrl();
      final URLConnection conn = url.openConnection();
      if (conn instanceof HttpURLConnection) {
        HttpURLConnection httpconn = (HttpURLConnection) conn;
        final int code = httpconn.getResponseCode();
        if (code != 200) { // if response not OK
          error.append("Error in connection with url: ");
          error.append(url);
          InputStream in = httpconn.getErrorStream();
          if (in != null) {
            try (final BufferedReader errorReader = new BufferedReader(new InputStreamReader(in))) {
              for (String line; (line = readLine(errorReader)) != null;) {
                error.append("\n" + line);
              }
            }
          } else {
            error.append(" (");
            error.append(code);
            error.append(")");
          }
          httpconn.disconnect();
          return null;
        }
      }
      return conn;
    } catch (Exception ex) {
      error.append(ex);
    }
    return null;
  }

  /**
   * Process the channel.
   * 
   * @param ch the channel information.
   */
  public void processChannel(ChannelInfo ch) {
    if (channelProcessorList.isEmpty()) {
      channelList.add(ch.toString());
    } else {
      for (ChannelProcessor channelProcessor : channelProcessorList) {
        channelProcessor.processChannel(ch);
      }
    }
  }

  /**
   * Process the network.
   * 
   * @param network the network.
   */
  public void processNetwork(String network) {
    networkList.add(network);
  }

  /**
   * Process the station.
   * 
   * @param si the station information.
   */
  public void processStation(StationInfo si) {
    stationList.add(si);
  }

  /**
   * Read a line from the reader.
   * 
   * @param reader the reader.
   * @return the line or null if none or error.
   */
  protected String readLine(BufferedReader reader) {
    try {
      return reader.readLine();
    } catch (Exception ex) {
      //
    }
    return null;
  }

  /**
   * Set the output level.
   * 
   * @param level the output level.
   */
  public void setLevel(OutputLevel level) {
    if (level != null) {
      this.level = level;
    }
  }
}
