package gov.usgs.volcanoes.swarm.data.fdsnws;

import gov.usgs.volcanoes.swarm.ChannelInfo;

public interface ChannelProcessor {
  /**
   * Process the channel.
   * 
   * @param ch the channel information.
   */
  void processChannel(ChannelInfo ch);
}
