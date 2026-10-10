package gov.usgs.volcanoes.swarm.file;

import gov.usgs.volcanoes.core.data.HelicorderData;
import gov.usgs.volcanoes.core.data.Wave;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.swarm.data.CachedDataSource;
import gov.usgs.volcanoes.swarm.data.GulperListener;
import gov.usgs.volcanoes.swarm.data.SeismicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for active seismic data sources.
 * <p>
 * One of the getData methods should be overridden to get the data.
 * 
 * @see #getData(String, double, double)
 * @see #getData(String, long, long)
 */
public abstract class ActiveDataSource extends SeismicDataSource implements Cloneable {
  /** The logger. */
  private final Logger LOGGER = LoggerFactory.getLogger(getClass());

  @Override
  protected abstract ActiveDataSource clone();

  /**
   * Get data.
   * 
   * @param station the station
   * @param t1 start time
   * @param t2 end time
   */
  protected void getData(String station, double t1, double t2) {
    station = LocalFileUtil.getStation(station);
    getData(station, J2kSec.asEpoch(t1), J2kSec.asEpoch(t2));
  }

  /**
   * Get data.
   * 
   * @param station the station
   * @param t1 start time
   * @param t2 end time
   */
  protected void getData(String station, long t1, long t2) {}

  @Override
  public HelicorderData getHelicorder(String station, final double t1, final double t2,
      final GulperListener gl) {
    CachedDataSource cache = CachedDataSource.getInstance();
    station = LocalFileUtil.getStation(station);
    HelicorderData hd = cache.getHelicorder(station, t1, t2, gl);
    if (hd != null && hd.rows() == 0) { // if cache exists but no data
      hd = null; // no data
    }
    final double now = J2kSec.now();
    double startTime = t1;
    double endTime = t2;
    if (endTime > now) {
      endTime = now;
    }
    final boolean getDataFlag;
    if (hd == null) {
      getDataFlag = true;
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("getHelicorder({}, {}, {}):  no rows in cache", station,
            J2kSec.toDateString(t1), J2kSec.toDateString(t2));
      }
    } else {
      double st = hd.getStartTime();
      double et = hd.getEndTime();
      if (st - startTime > 1) {
        endTime = st; // get older stuff
        getDataFlag = true;
        hd.rows();
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("getHelicorder({}, {}, {}): new {} rows in cache {} {}", station,
              J2kSec.toDateString(t1), J2kSec.toDateString(t2), hd.rows(),
              J2kSec.toDateString(hd.getStartTime()), J2kSec.toDateString(hd.getEndTime()));
        }
      } else if (endTime - et > 1) {
        startTime = et; // get newer stuff
        getDataFlag = true;
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("getHelicorder({}, {}, {}): old {} rows in cache {} {}", station,
              J2kSec.toDateString(t1), J2kSec.toDateString(t2), hd.rows(),
              J2kSec.toDateString(hd.getStartTime()), J2kSec.toDateString(hd.getEndTime()));
        }
      } else {
        getDataFlag = false;
        LOGGER.trace("getHelicorder({}, {}, {}): all {} rows in cache {} {}", station,
            J2kSec.toDateString(t1), J2kSec.toDateString(t2), hd.rows(),
            J2kSec.toDateString(hd.getStartTime()), J2kSec.toDateString(hd.getEndTime()));
      }
    }
    // if need to get data
    if (getDataFlag) {
      getData(station, startTime, endTime);
      hd = cache.getHelicorder(station, t1, t2, gl);
      if (hd != null && hd.rows() == 0) { // if cache exists but no data
        hd = null; // no data
      }
      if (LOGGER.isTraceEnabled()) {
        if (hd == null) {
          LOGGER.trace("getHelicorder({}, {}, {}):  no rows in cache", station,
              J2kSec.toDateString(t1), J2kSec.toDateString(t2));
        } else {
          LOGGER.trace("getHelicorder({}, {}, {}): {} rows in cache {} {}", station,
              J2kSec.toDateString(t1), J2kSec.toDateString(t2), hd.rows(),
              J2kSec.toDateString(hd.getStartTime()), J2kSec.toDateString(hd.getEndTime()));
        }
      }
    }
    return hd;
  }

  @Override
  public Wave getWave(String station, final double t1, final double t2) {
    CachedDataSource cache = CachedDataSource.getInstance();
    station = LocalFileUtil.getStation(station);
    Wave wave = cache.getBestWave(station, t1, t2);
    double startTime = t1;
    double endTime = t2;
    final double now = J2kSec.now();
    if (endTime > now) {
      endTime = now;
    }
    final boolean getDataFlag;
    if (wave == null) {
      getDataFlag = true;
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("getWave({}, {}, {}):  no samples in cache", station, J2kSec.toDateString(t1),
            J2kSec.toDateString(t2));
      }
    } else {
      double st = wave.getStartTime();
      double et = wave.getEndTime();
      if (st - startTime > 1) {
        endTime = st; // get older stuff
        getDataFlag = true;
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("getWave({}, {}, {}): new {} samples in cache {} {}", station,
              J2kSec.toDateString(t1), J2kSec.toDateString(t2), wave.numSamples(),
              J2kSec.toDateString(wave.getStartTime()), J2kSec.toDateString(wave.getEndTime()));
        }
      } else if (endTime - et > 1) {
        startTime = et; // get newer stuff
        getDataFlag = true;
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("getWave({}, {}, {}): old {} samples in cache {} {}", station,
              J2kSec.toDateString(t1), J2kSec.toDateString(t2), wave.numSamples(),
              J2kSec.toDateString(wave.getStartTime()), J2kSec.toDateString(wave.getEndTime()));
        }
      } else {
        getDataFlag = false;
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("getWave({}, {}, {}): all {} samples in cache {} {}", station,
              J2kSec.toDateString(t1), J2kSec.toDateString(t2), wave.numSamples(),
              J2kSec.toDateString(wave.getStartTime()), J2kSec.toDateString(wave.getEndTime()));
        }
      }
    }
    // if need to get data
    if (getDataFlag) {
      getData(station, startTime, endTime);
      wave = cache.getBestWave(station, t1, t2);
      if (LOGGER.isTraceEnabled()) {
        if (wave == null) {
          LOGGER.trace("getWave({}, {}, {}):  no samples in cache", station,
              J2kSec.toDateString(t1), J2kSec.toDateString(t2));
        } else {
          LOGGER.trace("getWave({}, {}, {}): {} samples in cache {} {}", station,
              J2kSec.toDateString(t1), J2kSec.toDateString(t2),
              J2kSec.toDateString(wave.getStartTime()), wave.numSamples(),
              J2kSec.toDateString(wave.getEndTime()));
        }
      }
    }
    return wave;
  }

  @Override
  public boolean isActiveSource() {
    return true;
  }
}
