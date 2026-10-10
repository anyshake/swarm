package gov.usgs.volcanoes.swarm.data.seedlink;

public interface DoneListener {
  /**
   * No action done listener.
   */
  static DoneListener NO_DONE_LISTENER = new DoneListener() {
    @Override
    public void done(String station) {}
  };

  /**
   * Called when done.
   * 
   * @param station the station or an empty string if none, not null.
   */
  void done(String station);
}
