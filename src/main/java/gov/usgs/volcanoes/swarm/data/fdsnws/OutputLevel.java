package gov.usgs.volcanoes.swarm.data.fdsnws;

/** The station service output level. */
public enum OutputLevel {
  CHANNEL("channel"), NETWORK("network"), STATION("station");

  private final String level;

  OutputLevel(String s) {
    this.level = s;
  }

  public String toString() {
    return level;
  }
}
