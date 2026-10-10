package gov.usgs.volcanoes.swarm.data.fdsnws;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.sc.seis.seisFile.mseed.DataRecord;
import gov.usgs.volcanoes.core.data.Wave;
import gov.usgs.volcanoes.swarm.ChannelInfo;
import gov.usgs.volcanoes.swarm.data.SeismicDataSource;

public class WebServicesClient extends AbstractDataRecordClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebServicesClient.class);

  /**
   * Get the default web services data select URL text.
   * 
   * @return the default web services data select URL text.
   */
  public static String getDefaultWsDataSelectUrl() {
    return DataSelectReader.DEFAULT_WS_URL;
  }

  /**
   * Get the default web services station URL text.
   * 
   * @return the default web services station URL text.
   */
  public static String getDefaultWsStationUrl() {
    return AbstractWebServiceStationClient.DEFAULT_WS_URL;
  }

  private String lastStation = "";
  private int numStations = 0;
  private final String progressId = "channels";
  /** The station client. */
  private final WebServiceStationClient stationClient;
  private int stationCount = 0;

  /** The web services data select URL text. */
  private final String wsDataSelectUrl;

  /**
   * Creates the web services client.
   * 
   * @param net  the network filter or empty if none.
   * @param sta  the station filter or empty if none.
   * @param loc  the location filter or empty if none.
   * @param chan the channel filter or empty if none.
   */
  public WebServicesClient(final SeismicDataSource source, String net, String sta, String loc, String chan) {
    this(source, net, sta, loc, chan, getDefaultWsDataSelectUrl(), getDefaultWsStationUrl());
  }

  /**
   * Creates the web services client.
   * 
   * @param net             the network filter or empty if none.
   * @param sta             the station filter or empty if none.
   * @param loc             the location filter or empty if none.
   * @param chan            the channel filter or empty if none.
   * @param wsDataSelectUrl the web services data select URL text.
   * @param wsStationUrl    the web services station URL text.
   * @see #getDefaultWsDataSelectUrl()
   * @see #getDefaultWsStationUrl()
   */
  public WebServicesClient(final SeismicDataSource source, String net, String sta, String loc, String chan,
      String wsDataSelectUrl, String wsStationUrl) {
    super(source);
    stationClient = WebServiceStationClientFactory.createClient(wsStationUrl, net, sta, loc, chan);
    final ChannelProcessor channelProcessor = new ChannelProcessor() {
      @Override
      public void processChannel(ChannelInfo ch) {
        WebServiceUtils.addChannel(stationClient.getChannelList(), ch, source);
        if (lastStation.compareTo(ch.getStation()) != 0) {
          lastStation = ch.getStation();
          if (numStations > 0) {
            source.fireChannelsProgress(progressId, (double) stationCount / (double) numStations);
          }
          stationCount++;
        }
      }
    };
    stationClient.addChannelProcessor(channelProcessor);
    this.wsDataSelectUrl = wsDataSelectUrl;
  }

  /**
   * Get the channel information.
   * 
   * @return the list of channel information.
   */
  public List<String> getChannels() {
    final List<String> channelList = stationClient.getChannelList();
    if (channelList.isEmpty()) {
      String error = null;
      long start = System.currentTimeMillis();
      if (stationClient.isAllNetworks()) {
        stationClient.setLevel(OutputLevel.STATION);
        error = stationClient.fetch();
        if (error == null) {
          getSource().fireChannelsProgress(progressId, 0.);
          numStations = stationClient.getStationList().size();
          stationClient.setLevel(OutputLevel.CHANNEL);
          error = stationClient.fetch();
        }

      } else {
        getSource().fireChannelsProgress(progressId, 0.);
        stationClient.setLevel(OutputLevel.STATION);
        error = stationClient.fetch();
        numStations = stationClient.getStationList().size();
        if (error == null) {
          stationClient.setLevel(OutputLevel.CHANNEL);
          error = stationClient.fetch();
        }
        getSource().fireChannelsProgress(progressId, 1.);
      }
      long end = System.currentTimeMillis();
      if (WebServiceUtils.isDebug()) {
        LOGGER.debug("getChannels(): {} seconds", ((end - start) / 1000.));
      }
      if (error != null) {
        LOGGER.warn("could not get channels: {}", error);
      }
      assignChannels(channelList);
    }
    return channelList;
  }

  /**
   * Get the raw data.
   * 
   * @param channelInfo the channel information.
   * @param t1          the start time.
   * @param t2          the end time.
   * @return the raw data.
   */
  public Wave getRawData(final ChannelInfo channelInfo, final double t1, final double t2) {
    final Date begin = getDate(t1);
    final Date end = getDate(t2);
    final List<Wave> waves = createWaves();
    final DataSelectReader reader = new DataSelectReader(wsDataSelectUrl, 10000) {
      /**
       * Process a data record.
       * 
       * @param dr the data record.
       * @return true if data record should be added to the list, false otherwise.
       */
      public boolean processRecord(DataRecord dr) {
        try {
          addWaves(waves, dr);
        } catch (Exception ex) {
          LOGGER.warn("error adding web service raw data ({}): {}", channelInfo, ex.getMessage());
        }
        return true;
      }
    };
    try {
      final String query = reader.createQuery(channelInfo.getNetwork(), channelInfo.getStation(),
          channelInfo.getLocation(), channelInfo.getChannel(), begin, end);
      reader.read(query, (List<DataRecord>) null);
      LOGGER.trace("read web services raw data: {}?{}", wsDataSelectUrl, query);
    } catch (Exception ex) {
      LOGGER.warn("could not get web service raw data: {}", ex.getMessage());
    }
    Wave wave = join(waves);
    if (wave != null && WebServiceUtils.isDebug()) {
      LOGGER.debug("web service raw data ({}, {})", getDateText(wave.getStartTime()),
          getDateText(wave.getEndTime()) + ")");
    }
    return wave;
  }
}
