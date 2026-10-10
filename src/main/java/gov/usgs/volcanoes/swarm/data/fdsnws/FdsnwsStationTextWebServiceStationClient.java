package gov.usgs.volcanoes.swarm.data.fdsnws;

import java.io.BufferedReader;
import java.net.URLConnection;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.usgs.volcanoes.swarm.StationInfo;

public class FdsnwsStationTextWebServiceStationClient extends FdsnwsStationWebServiceStationClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(FdsnwsStationTextWebServiceStationClient.class);

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
  public FdsnwsStationTextWebServiceStationClient(String baseUrlText, String net, String sta, String loc, String chan,
      Date startDate, Date endDate) {
    super(baseUrlText, net, sta, loc, chan, startDate, endDate);
  }

  @Override
  protected void fetch(URLConnection conn) throws Exception {
    try (BufferedReader reader = WebServiceUtils.createBufferedReader(conn)) {
      for (String line; (line = reader.readLine()) != null;) {
        processLine(line);
      }
    }
  }

  /**
   * Get the base URL text.
   * 
   * @return the base URL text.
   */
  protected String getBaseUrlText() {
    return super.getBaseUrlText() + "format=text";
  }

  /**
   * Get the column text.
   * 
   * @param columns the columns.
   * @param index   the column index or -1 if none.
   * @return the column text or null if none.
   */
  protected String getColumnText(String[] columns, int index) {
    String s = null;
    if (index >= 0 && index < columns.length) {
      s = columns[index];
    }
    return s;
  }

  /**
   * Get the line split text.
   * 
   * @return the line split text.
   */
  protected String getLineSplitText() {
    return "\\s*\\|\\s*";
  }

  /**
   * Process the line.
   * 
   * @param line the line of text containing the channel information.
   */
  protected void processLine(final String line) {
    // skip comment line
    if (line.startsWith("#")) {
      return;
    }

    // skip line if it starts with the separator
    if (line.matches("^" + getLineSplitText() + ".*")) {
      LOGGER.info("skipping line ({})", line);
      return;
    }

    final String[] columns = split(line);
    int minNumColumns;
    switch (getLevel()) {
    case NETWORK:
      minNumColumns = 1;
      if (columns.length < minNumColumns) {
        appendInvalidLine(line);
      } else {
        String network = getColumnText(columns, 0);
        if (network != null && !network.isEmpty()) {
          if (columns.length >= 2) {
            String description = getColumnText(columns, 1);
            if (description != null && !description.isEmpty()) {
              network += "," + description;
            }
          }
          processNetwork(network);
        }
      }
      break;
    case STATION:
      minNumColumns = 6;
      if (columns.length < minNumColumns) {
        appendInvalidLine(line);
      } else {
        String network = getColumnText(columns, 0);
        String station = getColumnText(columns, 1);
        double latitude = StationInfo.parseDouble(getColumnText(columns, 2));
        double longitude = StationInfo.parseDouble(getColumnText(columns, 3));
        double elevation = StationInfo.parseDouble(getColumnText(columns, 4));
        String siteName = getColumnText(columns, 5);
        processStation(createStationInfo(station, network, latitude, longitude, elevation, siteName));
      }
      break;
    case CHANNEL:
      minNumColumns = 6;
      if (columns.length < minNumColumns) {
        appendInvalidLine(line);
      } else {
        String network = getColumnText(columns, 0);
        String station = getColumnText(columns, 1);
        String location = getColumnText(columns, 2);
        String channel = getColumnText(columns, 3);
        String siteName = null; // site name is not available
        double latitude = StationInfo.parseDouble(getColumnText(columns, 4));
        double longitude = StationInfo.parseDouble(getColumnText(columns, 5));
        double elevation = Double.NaN;
        if (columns.length > 6) {
          elevation = StationInfo.parseDouble(getColumnText(columns, 6));
        }
        processChannel(
            createChannelInfo(station, channel, network, location, latitude, longitude, elevation, siteName));
      }
      break;
    default:
      break;
    }
  }

  /**
   * Split the line.
   * 
   * @param line the line of text containing the channel information.
   * @return the channel information.
   */
  protected String[] split(String line) {
    return line.split(getLineSplitText());
  }
}
