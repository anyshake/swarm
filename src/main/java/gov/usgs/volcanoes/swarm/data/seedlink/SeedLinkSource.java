package gov.usgs.volcanoes.swarm.data.seedlink;

import gov.usgs.volcanoes.core.data.HelicorderData;
import gov.usgs.volcanoes.core.data.Wave;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.swarm.ChannelUtil;
import gov.usgs.volcanoes.swarm.data.CachedDataSource;
import gov.usgs.volcanoes.swarm.data.DataSourceType;
import gov.usgs.volcanoes.swarm.data.GulperListener;
import gov.usgs.volcanoes.swarm.data.SeismicDataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of <code>SeismicDataSource</code> that connects to an SeedLink Server.
 * 
 * @author Kevin Frechette (ISTI)
 * @author Tom Parker
 */
public class SeedLinkSource extends SeismicDataSource {
  /** The logger. */
  private static final Logger LOGGER = LoggerFactory.getLogger(SeedLinkSource.class);
  /**
   * Close when data not needed flag.
   * 
   * @see #notifyDataNotNeeded(String, double, double, GulperListener)
   */
  private static final boolean CLOSE_WHEN_DATA_NOT_NEEDED;
  /** Info string prefix text or null if none. */
  private static final String INFO_FILE_TEXT;
  static {
    CLOSE_WHEN_DATA_NOT_NEEDED =
        Boolean.parseBoolean(System.getProperty("SEED_LINK_CLOSE_WHEN_DATA_NOT_NEEDED"));
    INFO_FILE_TEXT =
        System.getProperty(DataSourceType.getShortName(SeedLinkSource.class) + "infofile");
  }

  /** The server host. */
  private String host;

  /** The information string File or null if none. */
  private File infoStringFile;

  /** The server port. */
  private int port;

  /** SeedLink clients for real time updates. */
  private final ConcurrentHashMap<String, SeedLinkClient> rtClients = new ConcurrentHashMap<>();

  /** SeedLink clients for past data. */
  private final ConcurrentHashMap<String, SeedLinkClient> clients = new ConcurrentHashMap<>();

  /**
   * How long a past-data request stands before the same range may be asked for again. Matches the
   * idle time after which a client closes itself.
   */
  private static final long BACKFILL_RETRY_MS = 5 * 60 * 1000L;

  /** The earliest start asked of the server for each channel, and when, as {start, millis}. */
  private final ConcurrentHashMap<String, double[]> backfills = new ConcurrentHashMap<>();

  /**
   * Parse config string.
   * 
   * @see gov.usgs.volcanoes.swarm.data.SeismicDataSource#parse(java.lang.String)
   */
  public void parse(String params) {
    String[] ss = params.split(":");
    host = ss[0];
    port = Integer.parseInt(ss[1]);
    if (INFO_FILE_TEXT != null) {
      infoStringFile = new File(INFO_FILE_TEXT + host + port + ".xml");
    }
  }

  /**
   * Close the data source.
   * 
   * @see gov.usgs.volcanoes.swarm.data.SeismicDataSource#close()
   */
  public void close() {
    // Don't close. SeedLink data source and client are shared by all viewers.
    // Closing one viewer triggers close on data source, but should not do anything
    // in case other viewers are using it.
  }

  /**
   * Get the channels.
   * 
   * @return the list of channels.
   */
  public List<String> getChannels() {
    String infoString = readChannelCache();

    if (infoString == null) {
      infoString = new SeedLinkClient(host, port).getInfoString("STREAMS");
      writeChannelCache(infoString);
    }

    List<String> channels = Collections.emptyList();
    if (!(infoString == null || infoString.isEmpty())) {
      try {
        SeedLinkChannelInfo seedLinkChannelInfo = new SeedLinkChannelInfo(this, infoString);
        channels = seedLinkChannelInfo.getChannels();
      } catch (Exception ex) {
        LOGGER.error("SeedLinkSource Cannot parse station list", ex);
      }
    }

    ChannelUtil.assignChannels(channels, this);
    return Collections.unmodifiableList(channels);
  }


  /**
   * Read channel data from cache.
   * 
   * @return
   */
  private String readChannelCache() {
    String infoString = null;

    if (infoStringFile != null && infoStringFile.canRead()) {
      FileInputStream stream = null;
      try {
        stream = new FileInputStream(infoStringFile);
        FileChannel fc = stream.getChannel();
        MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

        return Charset.defaultCharset().decode(bb).toString();
      } catch (IOException ex) {
        LOGGER.error("SeedLinkSource Cannot read seedlink channel cache. ({})", infoStringFile, ex);
      } finally {
        try {
          if (stream != null) {
            stream.close();
          }
        } catch (IOException ignore) {
          // ignore
        }
      }

    }

    return infoString;
  }

  /**
   * Write channel data to cache file.
   * 
   * @param infoString info string
   */
  private void writeChannelCache(String infoString) {
    if (infoStringFile == null) {
      return;
    }
    FileWriter writer = null;
    try {
      writer = new FileWriter(infoStringFile);
      writer.write(infoString);
    } catch (IOException ex) {
      LOGGER.error("SeedLinkSource Cannot write seedlink channel cache. ({})", infoStringFile, ex);
    } finally {
      try {
        if (writer != null) {
          writer.close();
        }
      } catch (IOException ignore) {
        // ignore
      }
    }
  }

  /**
   * Get the helicorder data.
   * 
   * @param scnl the scnl.
   * @param t1 the start time.
   * @param t2 the end time.
   * @param gl the gulper listener.
   * @return the helicorder data or null if none.
   */
  public synchronized HelicorderData getHelicorder(String scnl, double t1, double t2,
      GulperListener gl) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("SeedLinkSource getHelicorder: {} {} {}", scnl, J2kSec.toDateString(t1),
          J2kSec.toDateString(t2));
    }
    scnl = scnl.replace(' ', '$'); // just to be sure
    startRealtimeClient(scnl);

    CachedDataSource cache = CachedDataSource.getInstance();

    HelicorderData hd = cache.getHelicorder(scnl, t1, t2, gl);

    if (hd == null || hd.rows() == 0) { // no wave; go get all
      double now = J2kSec.now();
      t2 = Math.min(now, t2);
      getData(scnl, t1, t2);
      hd = cache.getHelicorder(scnl, t1, t2, gl);
    } else {
      double startDiff = hd.getStartTime() - t1;
      if (startDiff > 1) {
        getData(scnl, t1, hd.getStartTime()); // get older stuff
        hd = cache.getHelicorder(scnl, t1, t2, gl);
      }
    }

    return hd;
  }


  /**
   * Either returns the wave successfully or null if the data source could not get the wave.
   * 
   * @param scnl the scnl.
   * @param t1 the start time.
   * @param t2 the end time.
   * @return the wave or null if none.
   */
  public Wave getWave(String scnl, double t1, double t2) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("SeedLinkSource getWave: {} {} {}", scnl, J2kSec.toDateString(t1),
          J2kSec.toDateString(t2));
    }
    scnl = scnl.replace(' ', '$'); // just to be sure
    startRealtimeClient(scnl);

    Wave wave = CachedDataSource.getInstance().getBestWave(scnl, t1, t2);

    if (wave == null) {
      double now = J2kSec.now();
      t2 = Math.min(now, t2);
      getData(scnl, t1, t2); // no wave; go get all
      wave = CachedDataSource.getInstance().getBestWave(scnl, t1, t2);
    } else {
      double startDiff = wave.getStartTime() - t1;
      if (startDiff > 1) {
        getData(scnl, t1, wave.getStartTime()); // get older stuff
        wave = CachedDataSource.getInstance().getBestWave(scnl, t1, t2);
      }
    }

    return wave;
  }

  /**
   * Get real-time seedlink data.
   * 
   * @param scnl channel
   */
  private void startRealtimeClient(String scnl) {
    SeedLinkClient client = rtClients.get(scnl);
    if (client == null) {
      final double t1 = J2kSec.now() - 600.0;
      final double t2 = Double.NaN;
      final SeedLinkClient fresh = new SeedLinkClient(host, port, t1, t2, scnl);
      // a helicorder and a monitor asking at once must not both start a client for the channel
      client = rtClients.putIfAbsent(scnl, fresh);
      if (client == null) {
        LOGGER.debug("SeedLinkSource startRealtimeClient: {}", fresh);
        // a client that finishes late must not drop the one that replaced it
        fresh.setDoneListener(new DoneListener() {
          @Override
          public void done(String station) {
            if (rtClients.remove(station, fresh)) {
              LOGGER.debug("SeedLinkSource rtclient done: {}", fresh);
            }
          }
        });
        fresh.updateLastRequestTime();
        fresh.start();
        // wait up to 10 seconds for some data to come back
        fresh.waitForData(10000);
        return;
      }
    }
    client.updateLastRequestTime();
  }

  /**
   * Start new client to get past data if there isn't already one running.
   * 
   * @param scnl channel
   * @param t1 start time
   * @param t2 end time
   */
  private void getData(String scnl, double t1, double t2) {
    // Every refresh asks for the time before the oldest cached sample. When the server has nothing
    // older, asking again only opened another connection and waited out the timeout each time.
    final long now = System.currentTimeMillis();
    final double[] asked = backfills.get(scnl);
    if (asked != null && asked[0] <= t1 && now - asked[1] < BACKFILL_RETRY_MS) {
      return;
    }
    backfills.put(scnl, new double[] {t1, now});

    SeedLinkClient client = clients.get(scnl);
    if (client != null) {
      // the new request starts no later, so it covers what this one was fetching
      client.closeConnection();
    }
    final SeedLinkClient fresh = new SeedLinkClient(host, port, t1, t2, scnl);
    LOGGER.debug("SeedLinkSource getData: {}", fresh);
    // a closed client that finishes late must not drop the one that replaced it
    fresh.setDoneListener(new DoneListener() {
      @Override
      public void done(String station) {
        if (clients.remove(station, fresh)) {
          LOGGER.debug("SeedLinkSource client done: {}", fresh);
        }
      }
    });
    clients.put(scnl, fresh);
    fresh.updateLastRequestTime();
    fresh.start();
    // wait up to 10 seconds for client to get data
    fresh.waitForWindow(10000);
  }

  /**
   * Check if data source is active. That is, is new data being added in real-time to this data
   * source?
   * 
   * @return whether or not this is an active data source.
   */
  public boolean isActiveSource() {
    return true;
  }

  /**
   * Notify client that a station is no longer needed.
   * 
   * @see gov.usgs.volcanoes.swarm.data.SeismicDataSource#notifyDataNotNeeded (java.lang.String,
   *      double, double, gov.usgs.volcanoes.swarm.data.GulperListener)
   */
  public synchronized void notifyDataNotNeeded(String station, double t1, double t2,
      GulperListener gl) {
    if (!CLOSE_WHEN_DATA_NOT_NEEDED) {
      return;
    }
    // This is called on the EDT
    // Other viewers may be using the station and there is no good way to check
    // Could be removed and added back later if other frames are using it but may lead to gaps in
    // data
    station = station.replace(' ', '$');
    SeedLinkClient client = rtClients.remove(station);
    if (client != null) {
      LOGGER.debug("SeedLinkSource notifyDataNotNeeded: {}", client);
      client.closeConnection();
    }
  }

  /**
   * Get the configuration string.
   * 
   * @return the configuration string.
   */
  public String toConfigString() {
    return String.format("%s;%s:%s:%d", name, DataSourceType.getShortName(SeedLinkSource.class),
        host, port);
  }

}
