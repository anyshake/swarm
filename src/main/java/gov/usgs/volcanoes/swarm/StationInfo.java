package gov.usgs.volcanoes.swarm;

/**
 * Station information.
 * 
 * @author Kevin Frechette (ISTI)
 */
public class StationInfo implements Comparable<StationInfo> {
  /**
   * Get the double value for the specified text.
   * 
   * @param s the text or null or empty if none.
   * @return the double or NaN if none.
   */
  public static double parseDouble(String s) {
    if (s != null && s.length() != 0) {
      try {
        return Double.parseDouble(s);
      } catch (Exception ex) {
        //
      }
    }
    return Double.NaN;
  }

  /** The height. */
  private final double elevation;

  /** The latitude. */
  private final double latitude;

  /** The longitude. */
  private final double longitude;

  /** The network name. */
  private final String network;

  /** The site name. */
  private final String siteName;

  /** The station name. */
  private final String station;

  /** the text */
  private final String text;

  /**
   * Constructor.
   * 
   * @param station   station
   * @param network   network
   * @param latitude  latitude or NaN if none
   * @param longitude longitude or NaN if none
   * @param elevation elevation or NaN if none
   * @param siteName  name or null if same as station
   */
  public StationInfo(String station, String network, double latitude, double longitude, double elevation,
      String siteName) {
    this.station = station;
    this.network = network;
    this.latitude = latitude;
    this.longitude = longitude;
    this.elevation = elevation;
    if (siteName != null && siteName.equals(station)) {
      siteName = null;
    }
    this.siteName = siteName;
    if (Double.isNaN(latitude) && Double.isNaN(longitude) && Double.isNaN(elevation) && siteName == null) {
      text = station + " " + network;
    } else {
      text = station + " " + network + " " + latitude + " " + longitude + " " + elevation + " " + getSiteName();
    }
  }

  @Override
  public int compareTo(StationInfo o) {
    return text.compareTo(o.text);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof StationInfo && text.equals(((StationInfo) obj).text);
  }

  /**
   * Get elevation.
   * 
   * @return the elevation
   */
  public double getElevation() {
    return elevation;
  }

  @Override
  public int hashCode() {
    return text.hashCode();
  }

  /**
   * Get the latitude.
   * 
   * @return the latitude.
   */
  public double getLatitude() {
    return latitude;
  }

  /**
   * Get the longitude.
   * 
   * @return the longitude.
   */
  public double getLongitude() {
    return longitude;
  }

  /**
   * Get the network name.
   * 
   * @return the network name.
   */
  public String getNetwork() {
    return network;
  }

  /**
   * Get the site name.
   * 
   * @return the site name or the station name if no site name.
   */
  public String getSiteName() {
    String s = siteName;
    if (s == null) {
      s = station;
    }
    return s;
  }

  /**
   * Get the station name.
   * 
   * @return the station name.
   */
  public String getStation() {
    return station;
  }

  /**
   * Get the string representation of the station information.
   * 
   * @return the string representation of the station information.
   */
  public String toString() {
    return text;
  }
}
