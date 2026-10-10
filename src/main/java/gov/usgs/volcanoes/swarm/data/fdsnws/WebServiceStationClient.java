package gov.usgs.volcanoes.swarm.data.fdsnws;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import gov.usgs.volcanoes.swarm.StationInfo;

public interface WebServiceStationClient extends WebServiceConst {
  /**
   * Create an empty list.
   * 
   * @return the list.
   */
  static <T> List<T> createList() {
    return new ArrayList<T>();
  }

  /**
   * Determines if the text is for all channel, location, network or station
   * values.
   * 
   * @param s the text or null for all.
   * @return true if for all
   */
  static boolean isAll(String s) {
    return s == null || s.isEmpty() || s.equals(WILDCARD_ALL);
  }

  /**
   * Add the channel processor.
   * 
   * @param cp the channel processor.
   */
  void addChannelProcessor(ChannelProcessor cp);

  /**
   * Fetch the current level and add them in the list.
   * 
   * @return an error message or null if none.
   * @see #getLevel()
   */
  String fetch();

  /**
   * Get the channel.
   * 
   * @return the channel or null if none.
   */
  String getChannel();

  /**
   * Get the channel list.
   * 
   * @return the channel list.
   * @see #fetchChannels()
   */
  List<String> getChannelList();

  /**
   * Get the end date.
   * 
   * @return the end date or null if none.
   */
  Date getEndDate();

  /**
   * Get the output level.
   * 
   * @return the output level.
   * @see #setLevel(OutputLevel)
   */
  OutputLevel getLevel();

  /**
   * Get the location.
   * 
   * @return the location or null if none.
   */
  String getLocation();

  /**
   * Get the network.
   * 
   * @return the network or null if none.
   */
  String getNetwork();

  /**
   * Get the network list.
   *
   * @return the network list.
   */
  List<String> getNetworkList();

  /**
   * Get the start date.
   * 
   * @return the start date or null if none.
   */
  Date getStartDate();

  /**
   * Get the start time.
   * 
   * @return the start time or 0 if none.
   */
  default long getStartTime() {
    Date date = getStartDate();
    if (date != null) {
      return date.getTime();
    } else {
      return 0L;
    }
  }

  /**
   * Get the station.
   * 
   * @return the station or null if none.
   */
  String getStation();

  /**
   * Get the station list.
   * 
   * @return the station list.
   * @see #fetchStations()
   */
  List<StationInfo> getStationList();

  /**
   * Determine if all networks.
   * 
   * @return true if all networks, false otherwise.
   */
  default boolean isAllNetworks() {
    return isAll(getNetwork());
  }

  /**
   * Set the output level.
   * 
   * @param level the output level.
   * @see #getLevel()
   */
  void setLevel(OutputLevel level);
}
