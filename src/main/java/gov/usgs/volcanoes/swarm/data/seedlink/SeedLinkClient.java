/**
 * I waive copyright and related rights in the this work worldwide through the CC0 1.0 Universal
 * public domain dedication. https://creativecommons.org/publicdomain/zero/1.0/legalcode
 */

package gov.usgs.volcanoes.swarm.data.seedlink;

import gov.usgs.volcanoes.core.data.Wave;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.swarm.ChannelInfo;
import gov.usgs.volcanoes.swarm.Swarm;
import gov.usgs.volcanoes.swarm.SwarmConst;
import gov.usgs.volcanoes.swarm.TimeConstants;
import gov.usgs.volcanoes.swarm.data.CachedDataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.iris.Fissures.seed.container.Blockette;
import edu.iris.Fissures.seed.container.BlocketteDecoratorFactory;
import edu.iris.Fissures.seed.container.Btime;
import edu.iris.Fissures.seed.container.Waveform;
import edu.iris.Fissures.seed.exception.SeedException;
import nl.knmi.orfeus.seedlink.SLLog;
import nl.knmi.orfeus.seedlink.SLPacket;
import nl.knmi.orfeus.seedlink.SeedLinkException;
import nl.knmi.orfeus.seedlink.client.SeedLinkConnection;

/**
 * SeedLink client.
 * 
 * @author Kevin Frechette (ISTI)
 */
public class SeedLinkClient implements Runnable, SwarmConst, TimeConstants {
  private static final Logger LOGGER = LoggerFactory.getLogger(SeedLinkClient.class);
  private static final long timeout;
  static {
    long value = -1;
    String s = System.getProperty("SL_TIMEOUT");
    if (s != null && !s.isEmpty()) {
      try {
        value = Long.parseLong(s);
      } catch (Exception ex) {
      }
    }
    if (value < 0) {
      // default to 5 minutes
      value = MS_PER_MINUTE * 5;
    }
    timeout = value;
  }

  private final Object dataSync = new Object();
  private final Object doneSync = new Object();
  private boolean dataFlag;
  private boolean doneFlag;

  /** SeedLink server address. */
  private final String sladdr;

  /** BaseSLConnection object for communicating with the BaseSLConnection over a socket. */
  private final SeedLinkConnection slconn;

  /** INFO LEVEL for info request only. */
  private String infolevel = null;

  /** Multiselect string. Example: "IU_KONO:BHE BHN,GE_WLF,MN_AQU:HH?.D" */
  private final String multiselect;

  /** Client thread. */
  private Thread thread;

  /** Start and end time of thread. In J2k seconds. */
  private final double startTime;
  private final double endTime;

  private final String[] scnls;
  private DoneListener doneListener = DoneListener.NO_DONE_LISTENER;
  private final Date lastRequestTime = new Date(0L);

  /**
   * Create SeedLink client with channel.
   * 
   * @param host seedlink server host
   * @param port seedlink server port
   * @param scnls channels to get
   */
  public SeedLinkClient(String host, int port, String... scnls) {
    this(host, port, Double.NaN, Double.NaN, scnls);
  }

  /**
   * Create SeedLink client with channel, start and end time.
   * 
   * @param host seedlink server host
   * @param port seedlink server port
   * @param st data request start time or <code>NaN</code> if none
   * @param et data request end time or <code>NaN</code> if none
   * @param scnls channels to get
   */
  public SeedLinkClient(String host, int port, double st, double et, String... scnls) {
    super();
    sladdr = host + ":" + port;
    this.startTime = st;
    this.endTime = et;
    if (scnls == null) {
      scnls = EMPTY_STRINGS;
    }
    this.scnls = scnls;
    if (scnls.length == 0) {
      multiselect = null;
    } else {
      String tmpMs = "";
      String prevStation = "";
      for (String scnl : scnls) {
        ChannelInfo channelInfo = new ChannelInfo(scnl);
        String station = channelInfo.getNetwork() + "_" + channelInfo.getStation();
        String selector = channelInfo.getLocation() + channelInfo.getChannel();
        if (station.equals(prevStation)) {
          tmpMs += " " + selector;
        } else {
          if (!tmpMs.equals("")) {
            tmpMs += ",";
          }
          tmpMs += station + ":" + selector;
        }
        prevStation = station;
      }
      tmpMs += "." + SeedLinkChannelInfo.DATA_TYPE;
      multiselect = tmpMs;
    }

    slconn = new SeedLinkConnection(new SLLog());
    slconn.setBeginTime(j2kToSeedLinkDateString(st));
    slconn.setEndTime(j2kToSeedLinkDateString(et));
    slconn.setSLAddress(sladdr);

    // Make sure a server was specified
    if (slconn.getSLAddress() == null) {
      LOGGER.error("SeedLinkClient No SeedLink server specified: {} {}", this);
      return;
    }

    // If no host is given for the SeedLink server, add 'localhost'
    if (slconn.getSLAddress().startsWith(":")) {
      try {
        slconn.setSLAddress(InetAddress.getLocalHost().toString() + slconn.getSLAddress());
      } catch (UnknownHostException ex) {
        LOGGER.error("SeedLinkClient error: {} {}", this, ex.getMessage());
        return;
      }
    }

    if (multiselect != null) {
      try {
        slconn.parseStreamlist(multiselect, null);
      } catch (SeedLinkException ex) {
        LOGGER.error("SeedLinkClient Unable to parse stream list: {}", this);
      }
    }
  }

  /**
   * Get the SeedLink information string.
   * 
   * @param info should be ID, STATIONS, STREAMS, GAPS, CONNECTIONS, ALL
   * @return the SeedLink information string or null if error.
   */
  public String getInfoString(String info) {
    try {
      infolevel = info;
      run();
      return slconn.getInfoString();
    } catch (Exception ex) {
      LOGGER.warn("SeedLinkClient Could not get channels: {}", this, ex);
    }
    return null;
  }

  /**
   * Cache the wave.
   * 
   * @param scnl the SCNL.
   * @param wave the wave.
   */
  private void cacheWave(String scnl, Wave wave) {
    if (scnl == null || wave == null) {
      return;
    }
    CachedDataSource.getInstance().putWave(scnl, wave);
    CachedDataSource.getInstance().cacheWaveAsHelicorder(scnl, wave);
    notifyData();
  }

  /**
   * Get the end time.
   * 
   * @return the end time or <code>NaN</code> if none.
   */
  public double getEndTime() {
    return endTime;
  }

  /*
   * taken from Robert Casey's PDCC seed code.
   */
  private float getSampleRate(double factor, double multiplier) {
    float sampleRate = (float) 10000.0; // default (impossible) value;
    if ((factor * multiplier) != 0.0) { // in the case of log records
      sampleRate = (float) (Math.pow(Math.abs(factor), (factor / Math.abs(factor)))
          * Math.pow(Math.abs(multiplier), (multiplier / Math.abs(multiplier))));
    }
    return sampleRate;
  }

  /**
   * Get the start time.
   * 
   * @return the start time or <code>NaN</code> if none.
   */
  public double getStartTime() {
    return startTime;
  }

  private Date btimeToDate(Btime btime) {
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    cal.set(Calendar.YEAR, btime.getYear());
    cal.set(Calendar.DAY_OF_YEAR, btime.getDayOfYear());
    cal.set(Calendar.HOUR_OF_DAY, btime.getHour());
    cal.set(Calendar.MINUTE, btime.getMinute());
    cal.set(Calendar.SECOND, btime.getSecond());
    cal.set(Calendar.MILLISECOND, btime.getTenthMill() / 10);
    return cal.getTime();
  }

  /**
   * Converts a j2ksec to a SeedLink date string ("year,month,day,hour,minute,second").
   * 
   * @param j the j2ksec or NaN if none.
   * @return a SeedLink date string or null if none.
   */
  private static String j2kToSeedLinkDateString(double j) {
    if (Double.isNaN(j)) {
      return null;
    }
    return J2kSec.format("yyyy,MM,dd,HH,mm,ss", j);
  }

  /**
   * Get the Btime value from the specified blockette field.
   * 
   * @param blockette the blockette.
   * @param fieldNum the field number.
   * @return the Btime value.
   * @throws SeedException if error.
   */
  private static Btime getBtime(Blockette blockette, int fieldNum) throws SeedException {
    Object obj = blockette.getFieldVal(fieldNum);
    if (obj instanceof Btime) {
      return (Btime) obj;
    }
    return new Btime(obj.toString());
  }

  /**
   * Get the double value from the specified blockette field.
   * 
   * @param blockette the blockette.
   * @param fieldNum the field number.
   * @return the double value.
   * @throws SeedException if error.
   */
  private static double getDouble(Blockette blockette, int fieldNum) throws SeedException {
    Object obj = blockette.getFieldVal(fieldNum);
    if (obj instanceof Number) {
      return ((Number) obj).doubleValue();
    }
    return Double.parseDouble(obj.toString());
  }

  /**
   * Method that processes each packet received from the SeedLink server. This is based on code
   * lifted from SeedLinkManager in SeisGram2K with clock logic removed.
   * 
   * @param count the packet to process.
   * @param slpack the packet to process.
   * 
   * @return true if connection to SeedLink server should be closed and session terminated, false
   *         otherwise.
   * 
   * @exception implementation dependent
   * 
   */
  private boolean packetHandler(int count, SLPacket slpack) throws Exception {

    if (count % 10000 == 0) {
      Runtime.getRuntime().gc();
      LOGGER.trace(
          "SeedLinkClient Packet count reached limit of 10000, garbage collection performed: {}",
          this);
    }

    // may not be on AWT-Event Thread, so do not call any GUI methods

    // check if not a complete packet
    if (slpack == null || slpack == SLPacket.SLNOPACKET || slpack == SLPacket.SLERROR) {
      return false; // do not close the connection
    }

    // get basic packet info
    final int type = slpack.getType();

    // process INFO packets here
    // return if unterminated
    if (type == SLPacket.TYPE_SLINF) {
      return false; // do not close the connection
    }
    // process message and return if terminated
    if (type == SLPacket.TYPE_SLINFT) {
      if (infolevel != null) {
        return true; // close the connection
      } else {
        return false; // do not close the connection
      }
    }

    // if here, must be a blockette
    final Blockette blockette = slpack.getBlockette();
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(
          "SeedLinkClient {}: packet seqnum={}, packet type={}, blockette type={}, blockette={}",
          this, slpack.getSequenceNumber(), type, blockette.getType(), blockette);
    }

    final Waveform waveform = blockette.getWaveform();
    // if waveform and FSDH
    if (waveform != null && blockette.getType() == 999 && Swarm.getApplicationFrame() != null) {
      // convert waveform to wave (also done in
      // gov.usgs.swarm.data.FileDataSource)
      try {
        final Btime bTime = getBtime(blockette, 8);
        final double factor = getDouble(blockette, 10);
        final double multiplier = getDouble(blockette, 11);
        final double startTime = J2kSec.fromDate(btimeToDate(bTime));
        final double samplingRate = getSampleRate(factor, multiplier);
        final Wave wave = new Wave();
        wave.setSamplingRate(samplingRate);
        wave.setStartTime(startTime);
        wave.buffer = waveform.getDecodedIntegers();
        wave.register();
        String network = (String) blockette.getFieldVal(7);
        String station = (String) blockette.getFieldVal(4);
        String location = (String) blockette.getFieldVal(5);
        String channel = (String) blockette.getFieldVal(6);
        String scnl = station + " " + channel + " " + network + " " + location;
        scnl = scnl.trim().replace(" ", "$");
        cacheWave(scnl, wave);
      } catch (Exception ex) {
        LOGGER.warn("SeedLinkClient packetHandler: could not create wave: {}", this, ex);
        return true; // close the connection
      }
    }
    return false; // do not close the connection
  }

  /**
   * Start this SeedLinkClient.
   */
  public void run() {
    int count = 0;
    long time = 0;
    try {
      if (infolevel != null) {
        LOGGER.trace("SeedLinkClient Requesting SeedLink info: {} {}", this, infolevel);
        slconn.requestInfo(infolevel);
      }

      // Loop with the connection manager
      SLPacket slpack = slconn.collect();
      if (slpack != null && slpack != SLPacket.SLTERMINATE) {
        count = 1;
        do {
          slpack.getType(); // ensure the blockette is created if needed
          // reset the volume counter
          BlocketteDecoratorFactory.reset();
          try {
            // do something with packet
            boolean terminate = packetHandler(count, slpack);
            if (terminate) {
              break;
            }
          } catch (SeedLinkException ex) {
            LOGGER.info("SeedLinkClient packetHandler error: {}", this, ex);
          }
          // 20081127 AJL - test modification to prevent "Error: out of java heap space" problems
          // identified by pwiejacz@igf.edu.pl
          if (count >= Integer.MAX_VALUE) {
            count = 1;
            LOGGER.debug("SeedLinkClient: Packet count reset to 1: {}", this);
          } else {
            count++;
          }
          if ((time = getLastRequestTime()) != 0) { // if should check for timeout
            final long now = System.currentTimeMillis();
            // check for timeout
            if (now - time >= timeout) {
              LOGGER.info("SeedLinkClient no longer needed: {}", this);
              break;
            }
          }
        } while ((slpack = slconn.collect()) != null && slpack != SLPacket.SLTERMINATE);
      }
    } catch (Exception ex) {
      LOGGER.warn("SeedLinkClient error: {} {}", this, ex);
    }

    if (multiselect != null) {
      if (endTime > 0) {
        LOGGER.debug("SeedLinkClient ended ({} packets): {}", count, this);
      }
    }
    // Close the BaseSLConnection
    slconn.close();
    thread = null;
    notifyDone();
    if (scnls.length == 0) {
      getDoneListener().done(EMPTY_STRING);
    } else {
      for (String scnl : scnls) {
        getDoneListener().done(scnl);
      }
    }
  }

  /**
   * Get the done listener.
   * 
   * @return the done listener, not null.
   */
  public synchronized DoneListener getDoneListener() {
    return doneListener;
  }

  /**
   * Determines if the data is available.
   * 
   * @return true if data is available.
   */
  private boolean isData() {
    synchronized (dataSync) {
      return dataFlag;
    }
  }


  /**
   * Determines if the run is done.
   * 
   * @return true if the run is done.
   */
  private boolean isDone() {
    synchronized (doneSync) {
      return doneFlag;
    }
  }

  /**
   * Notify data is available to all waiting threads.
   */
  private void notifyData() {
    synchronized (dataSync) {
      dataFlag = true;
      dataSync.notifyAll();
    }
  }

  /**
   * Notify done to all waiting threads.
   */
  private void notifyDone() {
    synchronized (doneSync) {
      doneFlag = true;
      doneSync.notifyAll();
    }
  }

  /**
   * Set the done listener.
   * 
   * @param dl the done listener or null if none.
   */
  public synchronized void setDoneListener(DoneListener dl) {
    if (dl == null) {
      dl = DoneListener.NO_DONE_LISTENER;
    }
    doneListener = dl;
  }

  /**
   * Wait for data if the client is running.
   * 
   * @param timeout the maximum time to wait in milliseconds.
   * @return true if the run was started and data is available.
   */
  public boolean waitForData(long timeout) {
    if (isData()) {
      return true;
    }
    if (isRunning()) {
      try {
        synchronized (dataSync) {
          if (!dataFlag) {
            dataSync.wait(timeout);
          }
        }
      } catch (Exception ex) {
      }
    }
    return isData();
  }

  /**
   * Wait for done if the client is running.
   * 
   * @param timeout the maximum time to wait in milliseconds.
   * @return true if the run was started and is done.
   */
  public boolean waitForDone(long timeout) {
    if (isDone()) {
      return true;
    }
    if (isRunning()) {
      try {
        synchronized (doneSync) {
          if (!doneFlag) {
            doneSync.wait(timeout);
          }
        }
      } catch (Exception ex) {
      }
    }
    return isDone();
  }

  protected synchronized boolean isRunning() {
    if (thread == null) {
      return false;
    }
    return thread.isAlive();
  }

  protected synchronized void start() {
    if (thread == null) {
      synchronized (doneSync) {
        doneFlag = false;
      }
      thread = new Thread(this);
      thread.start();
    }
  }

  /**
   * Close the SeedLink connection.
   */
  public synchronized void closeConnection() {
    slconn.terminate();
  }

  @Override
  public String toString() {
    int capacity = sladdr.length();
    if (multiselect != null) {
      capacity += multiselect.length() + 1;
    }
    final String st = j2kToSeedLinkDateString(startTime);
    final String et = j2kToSeedLinkDateString(endTime);
    if (st != null) {
      capacity += st.length() + 1;
    }
    if (et != null) {
      capacity += et.length() + 1;
    }
    StringBuilder sb = new StringBuilder(capacity);
    sb.append(sladdr);
    if (multiselect != null) {
      sb.append(' ');
      sb.append(multiselect);
    }
    if (st != null) {
      sb.append(' ');
      sb.append(st);

    }
    if (et != null) {
      sb.append(' ');
      sb.append(et);
    }
    return sb.toString();
  }

  /**
   * Get the last request time.
   * 
   * @return the last request time.
   */
  public long getLastRequestTime() {
    synchronized (lastRequestTime) {
      return lastRequestTime.getTime();
    }
  }

  /**
   * Update the last request time.
   */
  public void updateLastRequestTime() {
    if (timeout <= 0) { // if no timeout
      return;
    }
    if (infolevel != null) { // if info request
      return;
    }
    synchronized (lastRequestTime) {
      final long last = lastRequestTime.getTime();
      final long now = System.currentTimeMillis();
      if (now > last) {
        lastRequestTime.setTime(now);
      }
    }
  }
}
