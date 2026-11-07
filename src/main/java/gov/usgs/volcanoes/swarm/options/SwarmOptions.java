package gov.usgs.volcanoes.swarm.options;

import javax.swing.event.EventListenerList;

/**
 * A singleton ArrayList of internal frames which keeps a list of listeners.
 * 
 * @author Tom Parker
 * 
 */
public final class SwarmOptions {

  private static final EventListenerList optionsListeners = new EventListenerList();

  private SwarmOptions() {}

  /**
   * Options changed.
   */
  public static void optionsChanged() {
    for (SwarmOptionsListener listener : optionsListeners
        .getListeners(SwarmOptionsListener.class)) {
      listener.optionsChanged();
    }
  }

  public static void addOptionsListener(SwarmOptionsListener tl) {
    optionsListeners.add(SwarmOptionsListener.class, tl);
  }
}
