package gov.usgs.volcanoes.swarm.data.fdsnws;

public interface WebServiceConst {
  /** The dataselect query suffix */
  String DATASELECT_QUERY_SUFFIX = "/query";
  /** The dataselect summary suffix used with portable fdsnws-dataselect */
  String DATASELECT_SUMMARY_SUFFIX = "/summary";
  /** The default dataselect URL */
  String DEFAULT_DATASELECT_URL = "http://localhost/fdsnws/dataselect/1/query";
  /** The default station URL */
  String DEFAULT_STATION_URL = "http://localhost/fdsnws/station/1/query";
  /** Default web services station client URL */
  String DEFAULT_WS_URL = "https://service.iris.edu/fdsnws/station/1/query";
  /** Web services source description. */
  String DESCRIPTION = "an FDSN Web Services server";
  /** Empty location code. */
  String EMPTY_LOC_CODE = "--";
  /** The FDSN dataselect query suffix */
  String FDSN_DATASELECT_QUERY_SUFFIX = "/fdsnws/dataselect/1/query";
  /** Regular expression delimiter */
  char REGEX_DELIMITER = '|';
  /** Regular expression matches any single character */
  char REGEX_SINGLE = '.';
  /** Regular expression matches zero to many characters */
  char REGEX_ZERO_TO_MANY = '*';
  /** Value delimiter */
  char VALUE_DELIMITER = ',';
  /** The wildcard for matching all */
  String WILDCARD_ALL = "*";
  /** Wildcard matches any single character */
  char WILDCARD_SINGLE = '?';
  /** Wildcard matches zero to many characters (same as regular expression) */
  char WILDCARD_ZERO_TO_MANY = '*';
}
