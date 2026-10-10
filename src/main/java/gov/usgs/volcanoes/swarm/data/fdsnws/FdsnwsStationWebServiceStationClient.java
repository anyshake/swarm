package gov.usgs.volcanoes.swarm.data.fdsnws;

import java.util.Date;

import gov.usgs.volcanoes.swarm.SwarmUtil;

public abstract class FdsnwsStationWebServiceStationClient extends AbstractWebServiceStationClient {
  private static final String endTimeText = "endtime";
  private static final String equalsText = "=";
  private static final String queryEnd = "?";
  private static final String separatorText = "&";
  private static final String startTimeText = "starttime";

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
  public FdsnwsStationWebServiceStationClient(String baseUrlText, String net, String sta, String loc, String chan,
      Date startDate, Date endDate) {
    super(baseUrlText, net, sta, loc, chan, startDate, endDate);
  }

  /**
   * Append the name and value to the text.
   * 
   * @param sb    the text.
   * @param name  the name.
   * @param value the value or null if none.
   */
  private void append(StringBuilder sb, String name, Object value) {
    if (value instanceof Date) {
      value = SwarmUtil.getDateText((Date) value);
    }
    if (value == null || value.toString().isEmpty()) {
      return;
    }
    sb.append(separatorText);
    sb.append(name);
    sb.append(equalsText);
    sb.append(value);
  }

  /**
   * Get the base URL text.
   * 
   * @return the base URL text.
   */
  protected String getBaseUrlText() {
    return super.getBaseUrlText() + queryEnd;
  }

  @Override
  protected String getUrlText() {
    final StringBuilder sb = new StringBuilder(getBaseUrlText());
    append(sb, "level", getLevel());
    append(sb, "network", getNetwork());
    append(sb, "station", getStation());
    append(sb, "location", getLocation());
    append(sb, "channel", getChannel());
    append(sb, startTimeText, getStartDate());
    append(sb, endTimeText, getEndDate());
    String urlText = sb.toString();
    return urlText;
  }
}
