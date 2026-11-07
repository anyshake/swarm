package gov.usgs.volcanoes.swarm;

/**
 * Interface TimeConstants defines simple time constants.<br>
 * All of these constants are simple as they have no external dependencies.
 */
public interface TimeConstants {
  /** Number of milliseconds in a second (1000). */
  long MS_PER_SECOND = 1000L;

  /** Number of seconds in a minute (60). */
  long SECONDS_PER_MINUTE = 60L;

  /** Number of milliseconds in an minute (60 * 1000 = 60000). */
  long MS_PER_MINUTE = SECONDS_PER_MINUTE * MS_PER_SECOND;

  /** Number of minutes in an hour (60). */
  long MINUTES_PER_HOUR = 60L;

  /** Number of milliseconds in an hour (60 * 60 * 1000 = 3600000). */
  long MS_PER_HOUR = MS_PER_MINUTE * MINUTES_PER_HOUR;

  /** Number of hours in a day (24). */
  long HOURS_PER_DAY = 24L;

  /** Number of milliseconds in an day (24 * 60 * 60 * 1000 = 86400000). */
  long MS_PER_DAY = MS_PER_HOUR * HOURS_PER_DAY;
}
