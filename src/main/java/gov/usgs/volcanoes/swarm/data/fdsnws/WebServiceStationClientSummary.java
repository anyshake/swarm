package gov.usgs.volcanoes.swarm.data.fdsnws;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.util.Date;
import java.util.List;

import gov.usgs.volcanoes.swarm.StationInfo;
import gov.usgs.volcanoes.swarm.SwarmUtil;
import gov.usgs.volcanoes.swarm.TimeConstants;

public class WebServiceStationClientSummary extends AbstractWebServiceStationClient implements TimeConstants {
  private static final int NUM_COLUMNS = 7;

  private static String getArg(String[] args, int index) {
    return args.length > index ? args[index] : null;
  }

  /**
   * Main method for testing.
   * 
   * @param args arguments <HTML><BODY>
   *             <P>
   *             <BR>
   *             Example:<br>
   *             <A HREF=
   *             "http://jamaseis.iris.edu:8800/fdsnws/dataselect/1/summary">http://jamaseis.iris.edu:8800/fdsnws/dataselect/1/summary</a>
   *             </BODY></HTML>
   */
  public static void main(String[] args) {
    try {
      if (args.length == 0) {
        System.out.printf("Usage: %s summary URL\n", WebServiceStationClientSummary.class.getName());
        return;
      }
      int index = 0;
      String baseUrlText = args[index++];
      String net = getArg(args, index++);
      String sta = getArg(args, index++);
      String loc = getArg(args, index++);
      String chan = getArg(args, index++);
      final WebServiceStationClientSummary wsc = new WebServiceStationClientSummary(baseUrlText, net, sta, loc, chan,
          null, null);
      wsc.fetch();
      System.err.println(wsc.getNetworkList());
      System.err.println(wsc.getStationList());
      System.err.println(wsc.getChannelList());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private boolean loaded = false;

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
  public WebServiceStationClientSummary(String baseUrlText, String net, String sta, String loc, String chan,
      Date startDate, Date endDate) {
    super(baseUrlText, WebServiceUtils.getRegex(net), WebServiceUtils.getRegex(sta), WebServiceUtils.getRegex(loc),
        WebServiceUtils.getRegex(chan), startDate, endDate);
  }

  private void addChannel(StationInfo stationInfo, String channel, String location) {
    processChannel(createChannelInfo(stationInfo, channel, location));
  }

  private String addNetwork(String network) {
    final List<String> networkList = getNetworkList();
    for (String net : networkList) {
      if (network.equals(net)) {
        return net;
      }
    }
    processNetwork(network);
    return network;
  }

  private StationInfo addStation(String station, String network) {
    for (StationInfo si : getStationList()) {
      if (si.getStation().equals(station) && si.getNetwork().equals(network)) {
        return si;
      }
    }
    StationInfo stationInfo = createStationInfo(station, network);
    getStationList().add(stationInfo);
    processStation(stationInfo);
    return stationInfo;
  }

  @Override
  public String fetch() {
    if (loaded) {
      return null;
    }
    return super.fetch();
  }

  @Override
  protected void fetch(URLConnection conn) {
    loaded = true;
    final long startTime = getStartTime();
    try {
      String line;
      String[] columns;
      String network;
      String station;
      String location;
      String channel;
      Date latest;
      StationInfo stationInfo;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
        while ((line = reader.readLine()) != null) {
          // skip comment line
          if (line.startsWith("#")) {
            continue;
          }
          columns = line.split("\\s+", NUM_COLUMNS);
          if (columns.length >= NUM_COLUMNS) {
            if (startTime != 0L) {
              latest = SwarmUtil.parseDate(columns[5]);
              if (latest == null) {
                appendInvalidLine(line, "invalid Latest");
                continue;
              }
              if (latest.getTime() < startTime) {
                continue;
              }
            }
            network = columns[0];
            station = columns[1];
            location = columns[2];
            channel = columns[3];
            if (!matches(network, getNetwork())) {
              continue;
            }
            if (!matches(station, getStation())) {
              continue;
            }
            if (!matches(location, getLocation())) {
              continue;
            }
            if (!matches(channel, getChannel())) {
              continue;
            }
            network = addNetwork(network);
            stationInfo = addStation(station, network);
            addChannel(stationInfo, channel, location);
          } else {
            appendInvalidLine(line);
          }
        }
      }
    } catch (Exception ex) {
      appendError(ex);
    }
  }

  private boolean matches(String s, String regex) {
    return s != null && (regex == null || s.matches(regex));
  }
}
