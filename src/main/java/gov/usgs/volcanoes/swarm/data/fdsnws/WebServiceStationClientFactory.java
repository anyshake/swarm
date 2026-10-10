package gov.usgs.volcanoes.swarm.data.fdsnws;

import java.util.Date;
import java.util.List;

public class WebServiceStationClientFactory implements WebServiceConst {
  /**
   * Create the web service station client.
   * 
   * @param baseUrlText the base URL text.
   */
  public static WebServiceStationClient createClient(String baseUrlText) {
    return createClient(baseUrlText, null, null, null, null, null, null);
  }

  /**
   * Create the web service station client.
   * 
   * @param baseUrlText the base URL text.
   * @param net         the network or null if none.
   * @param sta         the station or null if none.
   * @param loc         the location or null if none.
   * @param chan        the channel or null if none.
   */
  public static WebServiceStationClient createClient(String baseUrlText, String net, String sta, String loc,
      String chan) {
    return createClient(baseUrlText, net, sta, loc, chan, null, null);
  }

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
  public static WebServiceStationClient createClient(String baseUrlText, String net, String sta, String loc,
      String chan, Date startDate, Date endDate) {
    if (baseUrlText.endsWith(DATASELECT_SUMMARY_SUFFIX)) {
      return new WebServiceStationClientSummary(baseUrlText, net, sta, loc, chan, startDate, endDate);
    } else {
      return new FdsnwsStationTextWebServiceStationClient(baseUrlText, net, sta, loc, chan, startDate, endDate);
    }
  }

  /**
   * Main method for testing.
   * 
   * @param args arguments
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      printUsage();
      return;
    }
    List<String> networkList;
    WebServiceStationClient wsc;
    for (String arg : args) {
      wsc = createClient(arg);
      networkList = wsc.getNetworkList();
      wsc.setLevel(OutputLevel.NETWORK);
      wsc.fetch();
      int i = networkList.size();
      System.out.printf("%s %d\n", arg, i);
      i = 0;
      for (String network : networkList) {
        System.out.printf("%d %s\n", i++, network);
      }
    }
  }

  /**
   * Print the usage.
   */
  public static void printUsage() {
    System.out.printf("Usage: %s baseURL [...]", WebServiceStationClient.class.getName());
  }
}
