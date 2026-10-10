package gov.usgs.volcanoes.swarm.file;

/**
 * Data Structure Component.
 * <p>
 * This does not include the base directory since it is always implied at the beginning of each
 * file. All other directories after the base directory must be specified.
 */
public enum DataStructureComponent {
  /** Channel code/identifier */
  CHAN,
  /** Channel code with data type (CHAN.D) - PATH ONLY */
  CHAN_TYPE,
  /** 3 digit day of year */
  DAY,
  /** Location identifier */
  LOC,
  /** Network code/identifier */
  NET,
  /** Station code/identifier */
  STA,
  /** 1 character indicating the data type (D) */
  TYPE,
  /** 4 digit year */
  YEAR;

  /**
   * Returns the constant with the specified name.
   * 
   * @param name the name of the constant to return.
   * @return the constant with the specified name.
   * @throws IllegalArgumentException if there is no constant with the specified name.
   */
  public static DataStructureComponent parse(String name) throws IllegalArgumentException {
    if ("CHAN.TYPE".equals(name)) {
      return CHAN_TYPE;
    }
    return DataStructureComponent.valueOf(name);
  }
}
