package gov.usgs.volcanoes.swarm.file;

import gov.usgs.volcanoes.core.data.Wave;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.swarm.TimeConstants;
import gov.usgs.volcanoes.swarm.data.CachedDataSource;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.sc.seis.seisFile.mseed.Blockette;
import edu.sc.seis.seisFile.mseed.Blockette1000;
import edu.sc.seis.seisFile.mseed.DataRecord;
import edu.sc.seis.seisFile.mseed.SeedRecord;

public class LocalFileReader implements DataStructureConst, TimeConstants {
  private static class LocalFileReaderHolder {
    private static final LocalFileReader INSTANCE = new LocalFileReader();
  }

  /** The logger. */
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileReader.class);

  private static void cacheWave(String station, Wave wave) {
    CachedDataSource.getInstance().putWave(station, wave);
    CachedDataSource.getInstance().cacheWaveAsHelicorder(station, wave);
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("{} cacheWave: {} {} {} samples", station,
          J2kSec.toDateString(wave.getStartTime()), J2kSec.toDateString(wave.getEndTime()),
          wave.numSamples());
    }
  }

  /**
   * Get the local file reader.
   * 
   * @return the local file reader.
   */
  public static LocalFileReader getInstance() {
    return LocalFileReaderHolder.INSTANCE;
  }

  /**
   * Test program.
   * 
   * @param args the program arguments, which are the miniSEED file names to read.
   */
  public static void main(String[] args) {
    long start;
    long end;
    int count;
    final StringBuilder error = new StringBuilder();
    for (String arg : args) {
      start = System.currentTimeMillis();
      count = getInstance().cacheWaves(null, new File(arg), error);
      end = System.currentTimeMillis();
      System.out.println(Duration.ofMillis(end - start));
      if (count != 0 || error.length() == 0) {
        System.out.printf("cached %d samples\n", count);
      }
      if (error.length() != 0) {
        System.err.printf("%s error %s\n", arg, error);
      }
    }
  }

  private final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
  private final int[] dataBuffer;
  private int dataBufferNumSamples;
  private long dataBufferStartTime;
  private int count;

  private LocalFileReader() {
    dataBuffer = new int[MAX_WAVE_NUM_SAMPLES];
  }

  /**
   * Cache the waves from the specified file name.
   * 
   * @param station the station.
   * @param file the file.
   * @param error the error string builder or null if none.
   * @return the number of samples cached.
   */
  public int cacheWaves(String station, File file, StringBuilder error) {
    count = 0;
    try {
      SeedRecord sr;
      DataRecord dr;
      String s;
      int[] blocketteSamples;
      boolean continuation;
      long nextStartTime = 0L;
      int numSamples;
      double sampleRate = 0.0;
      double samplingIntervalMs;
      long maxTimeDiff;
      long startTime;

      dataBufferNumSamples = 0;
      dataBufferStartTime = 0L;
      if (!file.exists()) {
        if (error != null) {
          if (error.length() != 0) {
            error.append("\n");
          }
          error.append("file does not exist: ");
          error.append(file);
        }
      } else {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
          while (dis.available() > 0 && (sr = SeedRecord.read(dis)) instanceof DataRecord) {
            dr = (DataRecord) sr;
            s = LocalFileUtil.getStation(dr.getHeader());
            if (station == null) {
              station = s;
            } else if (!station.equals(s)) {
              if (error.length() != 0) {
                error.append("\n");
              }
              error.append("station ");
              error.append(station);
              error.append(" does not match station from SCNL ");
              error.append(s);
              continue;
            }
            sampleRate = dr.getSampleRate();
            samplingIntervalMs = Math.pow(sampleRate, -1) * (double) MS_PER_SECOND;
            maxTimeDiff = Math.max((long) samplingIntervalMs, 1);
            for (Blockette blockette : dr.getBlockettes(1000)) {
              if (blockette instanceof Blockette1000) {
                numSamples = dr.getHeader().getNumSamples();
                blocketteSamples = LocalFileUtil.getData(dr, (Blockette1000) blockette);
                // this should never happen
                if (blocketteSamples.length != numSamples) {
                  if (error != null) {
                    if (error.length() != 0) {
                      error.append("\n");
                    }
                    error.append("invalid samples length: ");
                    error.append(blocketteSamples.length);
                    error.append(", expected ");
                    error.append(numSamples);
                  }
                  return count;
                }
                LocalFileUtil.setTime(cal, dr);
                startTime = cal.getTimeInMillis();
                continuation = dr.getHeader().isContinuation();
                if (dataBufferStartTime == 0L) {
                  dataBufferStartTime = startTime;
                } else if (!continuation && dataBufferNumSamples != 0 && nextStartTime != 0L) {
                  long timeDiff = Math.abs(startTime - nextStartTime);
                  // if start time is expected
                  if (timeDiff <= maxTimeDiff) {
                    continuation = true;
                  } else {
                    continuation = false;
                    if (error.length() != 0) {
                      error.append("\n");
                    }
                    error.append("gap detected: start time=");
                    error.append(J2kSec.toDateString(J2kSec.fromEpoch(startTime)));
                    error.append(", expected start time=");
                    error.append(J2kSec.toDateString(J2kSec.fromEpoch(nextStartTime)));
                  }
                }
                // if data is too large for the data buffer
                if (numSamples > dataBuffer.length) {
                  if (error != null) {
                    if (error.length() != 0) {
                      error.append("\n");
                    }
                    error.append("invalid number of samples: ");
                    error.append(numSamples);
                  }
                  return count;
                }
                int totalNumSamples = dataBufferNumSamples + numSamples;
                // if data cannot be added to the data buffer
                if (totalNumSamples > dataBuffer.length) {
                  createWave(station, sampleRate);
                  totalNumSamples = numSamples;
                }
                if (!continuation && dataBufferNumSamples != 0) {
                  createWave(station, sampleRate);
                  totalNumSamples = numSamples;
                }
                if (dataBufferStartTime == 0L) {
                  dataBufferStartTime = startTime;
                }
                // copy samples into the data buffer
                System.arraycopy(blocketteSamples, 0, dataBuffer, dataBufferNumSamples, numSamples);
                dataBufferNumSamples = totalNumSamples;
                nextStartTime = dataBufferStartTime
                    + Math.round((double) dataBufferNumSamples * samplingIntervalMs);
              }
            }
          }
        }
      }
      createWave(station, sampleRate);
    } catch (Exception ex) {
      if (error != null) {
        if (error.length() != 0) {
          error.append("\n");
        }
        error.append(ex);
      }
    }
    return count;
  }

  private void createWave(String station, double sampleRate) {
    if (dataBufferNumSamples == 0) {
      return;
    }
    double j2k = J2kSec.fromEpoch(dataBufferStartTime);
    final Wave wave = new Wave();
    wave.setSamplingRate(sampleRate);
    wave.setStartTime(j2k);
    wave.buffer = Arrays.copyOf(dataBuffer, dataBufferNumSamples);
    wave.register();
    cacheWave(station, wave);
    dataBufferNumSamples = 0;
    dataBufferStartTime = 0L;
    count += wave.numSamples();
  }
}
