package gov.usgs.volcanoes.swarm.file;

public interface DataStructureConst {
  /** Buffer of Uniform Data */
  String BUD = "NET/STA/STA.NET.LOC.CHAN.YEAR.DAY";
  /** BUD text */
  String BUD_TEXT = "BUD (" + BUD + ")";
  /**
   * The data source configuration delimiter character (cannot be ':' because it is in Windows path)
   */
  char DS_CONFIG_DELIM_CHAR = '\0';
  /** The data source configuration delimiters */
  String DS_CONFIG_DELIMITERS = "[" + DS_CONFIG_DELIM_CHAR + "]";
  /** Data structure title */
  String DATA_STRUCTURE_TITLE = "Local files";
  /** The file name delimiter */
  char FILE_NAME_DELIMTER = '.';
  /** File name delimiters */
  String FILE_NAME_DELIMITERS = "[" + FILE_NAME_DELIMTER + "]";
  /** The path delimiter */
  char PATH_DELIMETER = java.io.File.separatorChar;
  /** Path delimiters */
  String PATH_DELIMITERS = "[/\\\\]";
  /** SeisComp Data Structure */
  String SDS = "YEAR/NET/STA/CHAN_TYPE/NET.STA.LOC.CHAN.TYPE.YEAR.DAY";
  /** SDS text */
  String SDS_TEXT = "SDS (" + SDS + ")";
  /** The type text for data */
  String TYPE_TEXT_DATA = "D";
  /** The default data structure */
  String DEFAULT_DATA_STRUCTURE = SDS;
  /** The default data structure text */
  String DEFAULT_DATA_STRUCTURE_TEXT = SDS_TEXT;

  /** Channel separator character */
  char CHANNEL_SEP_CHAR = ' ';
  /** The configuration delimiter character */
  char CONFIG_DELIM_CHAR = ':';
  /** ISO-8601 date format tool tip */
  String ISO_8601_TOOLTIP = "'YYYY-MM-DD'T'hh:mm:ss'";
  /** the max number of samples in a single wave. */
  int MAX_WAVE_NUM_SAMPLES = 250000;
  /** Need update text */
  String NEED_UPDATE = "---Need Update---";
  /** Station key separator character */
  char STATION_SEP_CHAR = '$';
  /** Updating list text */
  String UPDATING_LIST = "---Updating List---";
}
