package gov.usgs.volcanoes.swarm.wave;

import gov.usgs.volcanoes.core.data.SliceWave;
import gov.usgs.volcanoes.core.data.Wave;
import gov.usgs.volcanoes.core.legacy.plot.Plot;
import gov.usgs.volcanoes.core.legacy.plot.PlotException;
import gov.usgs.volcanoes.core.legacy.plot.color.Inferno;
import gov.usgs.volcanoes.core.legacy.plot.color.Jet2;
import gov.usgs.volcanoes.core.legacy.plot.decorate.FrameDecorator;
import gov.usgs.volcanoes.core.legacy.plot.render.TextRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.wave.ParticleMotionRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.wave.SliceWaveRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.wave.SpectraRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.wave.SpectrogramRenderer;
import gov.usgs.volcanoes.core.math.Util;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.time.Time;
import gov.usgs.volcanoes.quakeml.Pick;
import gov.usgs.volcanoes.swarm.Icons;
import gov.usgs.volcanoes.swarm.Metadata;
import gov.usgs.volcanoes.swarm.Offscreen;
import gov.usgs.volcanoes.swarm.SwarmConfig;
import gov.usgs.volcanoes.swarm.SwingWorker;
import gov.usgs.volcanoes.swarm.data.CachedDataSource;
import gov.usgs.volcanoes.swarm.data.SeismicDataSource;
import gov.usgs.volcanoes.swarm.event.PickData;
import gov.usgs.volcanoes.swarm.event.PickMenu;
import gov.usgs.volcanoes.swarm.event.PickWavePanel;
import gov.usgs.volcanoes.swarm.event.TagData;
import gov.usgs.volcanoes.swarm.event.TagMenu;
import gov.usgs.volcanoes.swarm.time.UiTime;
import gov.usgs.volcanoes.swarm.time.WaveViewTime;
import gov.usgs.volcanoes.swarm.wave.WaveViewSettings.ViewType;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class WaveViewPanel extends JComponent {

  private static final Logger LOGGER = LoggerFactory.getLogger(WaveViewPanel.class);
  protected static NumberFormat numberFormat = NumberFormat.getInstance();
  public static final long serialVersionUID = -1;
  protected static SwarmConfig swarmConfig;
  /**
   * X pixel location of where the main plot axis should be located on the component.
   */
  protected int xOffset = 60;
  /**
   * Y pixel location of where the main plot axis should be located on the component.
   */
  protected int yOffset = 20;
  /** The amount of padding space on the right side. */
  protected int rightWidth = 20;
  /** The amount of padding space on the bottom. */
  protected int bottomHeight = 20;
  protected FrameDecorator decorator;
  protected SliceWaveRenderer waveRenderer;
  protected SpectrogramRenderer spectrogramRenderer;
  protected SpectraRenderer spectraRenderer;
  protected Wave wave;
  protected double startTime;
  protected double endTime;
  protected WaveViewSettings settings;
  protected double minAmp = 1E300;
  protected double maxAmp = -1E300;
  protected double maxSpectraPower = -1E300;
  protected double maxSpectrogramPower = -1E300;
  protected double[] translation;
  protected boolean timeSeries;
  protected String channel;
  /**
   * The data source to use for zoom drags. This should probably be moved from this class to follow
   * a stricter interpretation of MVC.
   */
  protected SeismicDataSource source;
  /**
   * A flag to indicate whether the plot should display a title. Currently used when the plot is on
   * the clipboard or monitor.
   */
  protected boolean displayTitle;
  protected Color backgroundColor;
  protected Color bottomBorderColor;
  protected StatusTextArea statusText;
  protected boolean allowDragging = true;
  protected boolean dragging;
  protected double j2k1;
  protected double j2k2;
  protected int highlightX1;
  protected int highlightX2;
  protected static Image closeImg;
  protected boolean allowClose;
  protected EventListenerList listeners = new EventListenerList();
  /**
   * A flag that indicates whether data are being loaded for this panel.
   */
  protected boolean working;
  /**
   * The wave is rendered to an image that is only updated when the settings change for repaint
   * efficiency.
   */
  protected BufferedImage image;
  private final transient Offscreen offscreen = new Offscreen();
  private Plot plot;
  protected double mark1 = Double.NaN;
  protected double mark2 = Double.NaN;
  protected double cursorMark = Double.NaN;
  protected boolean useFilterLabel = true;
  protected Color borderColor;
  protected static final Color DARK_RED = new Color(168, 0, 0);
  protected static final Color DARK_GREEN = new Color(0, 168, 0);
  private static final Color DRAG_HIGHLIGHT = new Color(255, 255, 0, 128);
  private static final String[] PICK_PHASES = {PickData.P, PickData.S};
  private static final String[] CODA_PHASES = {PickData.CODA1, PickData.CODA2};
  private static final Color UNCERTAINTY_GRAY = halfAlpha(Color.GRAY);
  private static final Color UNCERTAINTY_P = halfAlpha(PickWavePanel.P_BACKGROUND);
  private static final Color UNCERTAINTY_S = halfAlpha(PickWavePanel.S_BACKGROUND);
  private static final Color UNCERTAINTY_CODA = halfAlpha(Color.YELLOW);
  private final Line2D.Double overlayLine = new Line2D.Double();
  private final GeneralPath markPath = new GeneralPath();

  /** Same RGB with alpha divided by two, matching the previous per-paint constructor. */
  private static Color halfAlpha(Color color) {
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 2);
  }

  /**
   * floor(log10) of a finite nonzero double is an integer in this range. Other exponents, including
   * NaN and infinities, still go through Math.pow.
   */
  private static final int POW10_MIN = -324;
  private static final int POW10_MAX = 308;
  private static final double[] POW10 = new double[POW10_MAX - POW10_MIN + 1];

  static {
    for (int exponent = POW10_MIN; exponent <= POW10_MAX; exponent++) {
      POW10[exponent - POW10_MIN] = Math.pow(10.0, exponent);
    }
  }

  private static double pow10(double exp) {
    if (exp >= POW10_MIN && exp <= POW10_MAX) {
      int exponent = (int) exp;
      if ((double) exponent == exp) {
        return POW10[exponent - POW10_MIN];
      }
    }
    return Math.pow(10.0, exp);
  }

  protected boolean pauseCursorMark;
  protected double time;
  private Wave waveInfoSource;
  private String waveInfoText;
  private Wave filteredSource;
  private Wave filteredWave;
  private int[] filteredBuffer;
  private boolean filteredZeroPhase;
  private int filteredOrder = Integer.MIN_VALUE;
  private Object filteredType;
  private double filteredCorner1 = Double.NaN;
  private double filteredCorner2 = Double.NaN;
  private double filteredRate = Double.NaN;
  /**
   * Cursor timestamps stay on this formatter. Mouse handling is on the event thread, so it is not
   * shared with another thread. A new one is built when the default locale changes.
   */
  private SimpleDateFormat cursorFormat;
  private Locale cursorFormatLocale;
  private final Date cursorDate = new Date();
  private boolean cursorTextValid;
  private long cursorTimeBits;
  private TimeZone cursorZone;
  private int cursorRawOffset;
  private int cursorDstSavings;
  private String cursorText;
  private final StringBuilder statusBuffer = new StringBuilder(256);
  private boolean durationTextValid;
  private long durationMark1Bits;
  private long durationMark2Bits;
  private long durationABits;
  private long durationBBits;
  private String durationText;
  private boolean sampleTextValid;
  private long sampleBits;
  private String sampleText;
  private boolean pickStatusValid;
  private long pickSpBits;
  private long pickCodaBits;
  private long pickVpBits;
  private long pickRatioBits;
  private long pickDurationABits;
  private long pickDurationBBits;
  private boolean pickDurationEnabled;
  private int pickWaveCount = -2;
  private double[] pickCodaDurations = new double[0];
  private double[] codaScratch = new double[0];
  private String cachedPickStatus = "";

  // pick and tag data
  private PickData pickData;
  protected ArrayList<TagData> tagData = new ArrayList<TagData>();
  private TagMenu tagMenu;

  /**
   * Default constructor.
   */
  public WaveViewPanel() {
    super();
    swarmConfig = SwarmConfig.getInstance();
    pauseCursorMark = false;
    backgroundColor = new Color(0xf7, 0xf7, 0xf7);
    settings = new WaveViewSettings();
    settings.view = this;
    pickData = new PickData();
    setupMouseHandler();
  }

  /**
   * Constructor.
   * 
   * @param s wave settings
   */
  public WaveViewPanel(WaveViewSettings s) {
    this();
    settings = s;
    s.view = this;
  }

  /**
   * Constructs a WaveViewPanel set up the same as a source WaveViewPanel. Used when copying a
   * waveform to the clipboard.
   * 
   * @param p the source WaveViewPanel
   */
  public WaveViewPanel(WaveViewPanel p) {
    this();

    channel = p.channel;
    source = p.source;
    startTime = p.startTime;
    endTime = p.endTime;
    maxSpectraPower = p.maxSpectraPower;
    maxSpectrogramPower = p.maxSpectrogramPower;
    timeSeries = p.timeSeries;
    allowDragging = p.allowDragging;
    wave = p.wave;
    displayTitle = p.displayTitle;
    backgroundColor = p.backgroundColor;
    mark1 = p.mark1;
    mark2 = p.mark2;
    pickData = p.pickData;

    translation = new double[8];
    if (p.translation != null) {
      System.arraycopy(p.translation, 0, translation, 0, 8);
    }

    settings = new WaveViewSettings(p.settings);
    settings.view = this;

    processSettings();
  }

  /**
   * Set offsets.
   * 
   * @param xo x offset
   * @param yo y offset
   * @param rw right width
   * @param bh bottom height
   */
  public void setOffsets(int xo, int yo, int rw, int bh) {
    xOffset = xo;
    yOffset = yo;
    rightWidth = rw;
    bottomHeight = bh;
  }

  public void addListener(WaveViewPanelListener listener) {
    listeners.add(WaveViewPanelListener.class, listener);
  }

  public void removeListener(WaveViewPanelListener listener) {
    listeners.remove(WaveViewPanelListener.class, listener);
  }

  /**
   * Fire zoom on wave.
   * 
   * @param e mouse event
   * @param oldStartTime old start time
   * @param oldEndTime old end time
   * @param newStartTime new start time
   * @param newEndTime new end time
   */
  public void fireZoomed(MouseEvent e, double oldStartTime, double oldEndTime, double newStartTime,
      double newEndTime) {
    Object[] ls = listeners.getListenerList();
    for (int i = ls.length - 2; i >= 0; i -= 2) {
      if (ls[i] == WaveViewPanelListener.class) {
        ((WaveViewPanelListener) ls[i + 1]).waveZoomed(this, oldStartTime, oldEndTime, newStartTime,
            newEndTime);
      }
    }
  }

  /**
   * Fire time pressed.
   * 
   * @param e mouse event
   * @param j2k J2000 time
   */
  public void fireTimePressed(MouseEvent e, double j2k) {
    Object[] ls = listeners.getListenerList();
    for (int i = ls.length - 2; i >= 0; i -= 2) {
      if (ls[i] == WaveViewPanelListener.class) {
        ((WaveViewPanelListener) ls[i + 1]).waveTimePressed(this, e, j2k);
      }
    }
  }

  /**
   * Fire mouse pressed event.
   * 
   * @param e mouse event
   */
  public void fireMousePressed(MouseEvent e) {
    Object[] ls = listeners.getListenerList();
    for (int i = ls.length - 2; i >= 0; i -= 2) {
      if (ls[i] == WaveViewPanelListener.class) {
        LOGGER.debug("Notifying mouse listeners.");
        ((WaveViewPanelListener) ls[i + 1]).mousePressed(this, e, dragging);
      }
    }
  }

  /**
   * Fire wave close event.
   */
  public void fireClose() {
    Object[] ls = listeners.getListenerList();
    for (int i = ls.length - 2; i >= 0; i -= 2) {
      if (ls[i] == WaveViewPanelListener.class) {
        ((WaveViewPanelListener) ls[i + 1]).waveClosed(this);
      }
    }
  }

  public void setAllowClose(boolean b) {
    allowClose = b;
  }

  /**
   * Process right mouse press.
   * 
   * @param e mouse event
   */
  protected void processRightMousePress(MouseEvent e) {
    if (settings.pickEnabled && (settings.viewType.equals(ViewType.WAVE)
        || settings.viewType.equals(ViewType.SPECTROGRAM))) {
      PickMenu pickMenu = new PickMenu(pickData, this);
      double[] t = getTranslation();
      if (t != null) {
        double j2k = e.getX() * t[0] + t[1];
        if (j2k >= startTime && j2k <= endTime) {
          pickMenu.setJ2k(j2k);
          pickMenu.show(this, e.getX() + 30, e.getY());
        }
      }
    } else if (settings.tagEnabled && (settings.viewType.equals(ViewType.WAVE)
        || settings.viewType.equals(ViewType.SPECTROGRAM))) {
      double[] t = getTranslation();
      if (t != null) {
        double j2k = e.getX() * t[0] + t[1];
        if (j2k >= startTime && j2k <= endTime) {
          tagMenu.setJ2k(j2k);
          tagMenu.show(this, e.getX() + 30, e.getY());
        }
      }

    } else {
      settings.cycleType();
    }
  }

  protected void processRightMouseRelease(MouseEvent e) {

  }

  protected void setupMouseHandler() {
    Cursor crosshair = new Cursor(Cursor.CROSSHAIR_CURSOR);
    this.setCursor(crosshair);
    this.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        UiTime.touchTime();

        if (SwingUtilities.isRightMouseButton(e)) {
          processRightMousePress(e);
        }

        double[] t = getTranslation();
        if (t != null) {
          int x = e.getX();
          double j2k = x * t[0] + t[1];
          /*
           * if (timeSeries) { System.out.printf("%s UTC: %s j2k: %.3f ew: %d\n", channel,
           * J2kSec.toDateString(j2k), j2k, J2kSec.asEpoch(j2k)); }
           */

          if (timeSeries && j2k >= startTime && j2k <= endTime) {
            fireTimePressed(e, j2k);
          }

          if (timeSeries && allowDragging && SwingUtilities.isLeftMouseButton(e)) {
            int y = e.getY();
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            if (t != null && y > yOffset && y < (panelHeight - bottomHeight) && x > xOffset
                && x < panelWidth - rightWidth) {
              j2k1 = j2k2 = j2k;
              if (e.isControlDown()) {
                System.out.println(channel + ": " + J2kSec.toDateString(j2k1));
              } else if (!e.isShiftDown()) {
                highlightX1 = highlightX2 = x;
                dragging = true;
              }
            }
          }
        }

        fireMousePressed(e);
      }

      public void mouseReleased(MouseEvent e) {
        UiTime.touchTime();
        if (SwingUtilities.isLeftMouseButton(e) && dragging) {
          dragging = false;
          if (j2k1 != j2k2 && source != null) {
            double st = Math.min(j2k1, j2k2);
            double et = Math.max(j2k1, j2k2);
            zoom(st, et);
            fireZoomed(e, getStartTime(), getEndTime(), st, et);
          }
          repaint();
        }

        if (SwingUtilities.isRightMouseButton(e)) {
          processRightMouseRelease(e);
        }
        int mx = e.getX();
        int my = e.getY();
        if (allowClose && SwingUtilities.isLeftMouseButton(e) && mx > getWidth() - 17
            && mx < getWidth() - 3 && my > 2 && my < 17) {
          fireClose();
        }
      }

      public void mouseExited(MouseEvent e) {
        time = Double.NaN;
        WaveViewTime.fireTimeChanged(Double.NaN);
        pauseCursorMark = false;
        dragging = false;
        repaint();
      }
    });

    this.addMouseMotionListener(new MouseMotionListener() {
      public void mouseMoved(MouseEvent e) {
        UiTime.touchTime();
        processMousePosition(e.getX(), e.getY());
      }

      public void mouseDragged(MouseEvent e) {
        UiTime.touchTime();
        /*
         * // This used to be the launcher for the microview. // It was removed because it wasn't
         * very useful, but this // stub is left here in case something like it ever gets // put in
         * if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown() && settings.type !=
         * WaveViewSettings.SPECTRA) { Dimension size = getSize(); double[] t = getTranslation();
         * int x = e.getX(); int y = e.getY(); if (t != null && y > Y_OFFSET && y < (size.height -
         * BOTTOM_HEIGHT) && x > X_OFFSET && x < size.width - RIGHT_WIDTH) { double j2k = x * t[0] +
         * t[1]; createMicroView(j2k); } }
         */

        processMousePosition(e.getX(), e.getY());
        if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && dragging) {
          double[] t = getTranslation();
          int x = e.getX();
          int y = e.getY();
          int panelWidth = getWidth();
          int panelHeight = getHeight();
          if (t != null && y > yOffset && y < (panelHeight - bottomHeight) && x > xOffset
              && x < panelWidth - rightWidth) {
            j2k2 = x * t[0] + t[1];
            highlightX2 = x;
            repaint();
          }
        }
      }
    });
  }

  /**
   * Wave zoom.
   * 
   * @param st start time
   * @param et end time
   */
  public void zoom(final double st, final double et) {
    final SwingWorker worker = new SwingWorker() {
      public Object construct() {
        Wave sw = null;
        if (source instanceof CachedDataSource) {
          sw = ((CachedDataSource) source).getBestWave(channel, st, et);
        } else {
          sw = source.getWave(channel, st, et);
        }
        setWave(sw, st, et);
        return null;
      }

      public void finished() {

        repaint();
      }
    };
    worker.start();
  }

  /**
   * Set the working flag. This flag indicates whether data are being loaded for this panel.
   * 
   * @param b the working flag state
   */
  public void setWorking(boolean b) {
    working = b;
  }

  /**
   * Set the allow dragging flag. This flag enables zoom dragging. Currently only allowed on the
   * clipboard, but could be implemented within the helicorder view.
   * 
   * @param b the allow dragging flag state
   */
  public void setAllowDragging(boolean b) {
    allowDragging = b;
  }

  public void setStatusText(StatusTextArea text) {
    statusText = text;
  }

  public int getXOffset() {
    return xOffset;
  }

  public int getYOffset() {
    return yOffset;
  }

  public WaveViewSettings getSettings() {
    return settings;
  }

  public double getStartTime() {
    return startTime;
  }

  public void setStartTime(double startTime) {
    this.startTime = startTime;
  }

  public double getEndTime() {
    return endTime;
  }

  public void setEndTime(double endTime) {
    this.endTime = endTime;
  }

  public Wave getWave() {
    return wave;
  }

  public WaveViewSettings getWaveViewSettings() {
    return settings;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String c) {
    channel = c;
  }

  public void setSettings(WaveViewSettings s) {
    settings = s;
    processSettings();
  }

  public SeismicDataSource getDataSource() {
    return source;
  }

  public void setDataSource(SeismicDataSource s) {
    source = s;
  }

  public void setFrameDecorator(FrameDecorator fd) {
    decorator = fd;
  }

  public void setDisplayTitle(boolean b) {
    displayTitle = b;
  }

  public void settingsChanged() {
    processSettings();
  }

  public boolean isTimeSeries() {
    return timeSeries;
  }

  /**
   * Gets the translation info for this panel. The translation info is used to convert from pixel
   * coordinates on the panel into time or data coordinates.
   * 
   * @return the transformation information
   */
  public double[] getTranslation() {
    return translation;
  }

  /**
   * Set the background color of the panel.
   * 
   * @param c the background color
   */
  public void setBackgroundColor(Color c) {
    backgroundColor = c;
  }

  public void setBottomBorderColor(Color c) {
    bottomBorderColor = c;
  }

  public void setBorderColor(Color c) {
    borderColor = c;
  }

  /**
   * Processes the mouse position variables when the cursor is over the panel. Currently, the only
   * thing this does is set the status bar text.
   * 
   * @param x the mouse x position
   * @param y the mouse y position
   */
  public boolean processMousePosition(int x, int y) {
    statusBuffer.setLength(0);
    String unit = null;

    int panelWidth = getWidth();
    int panelHeight = getHeight();
    double[] t = getTranslation();
    double previousTime = time;
    time = Double.NaN;

    if (wave != null && t != null && y > yOffset && y < (panelHeight - bottomHeight)
        && x > xOffset && x < panelWidth - rightWidth) {
      time = x * t[0] + t[1];
      double yi = y * -t[2] + t[3];

      statusBuffer.append(cachedWaveInfo());

      if (timeSeries) {

        if (swarmConfig.durationEnabled && !Double.isNaN(mark1) && !Double.isNaN(mark2)) {
          statusBuffer.append(",");
          statusBuffer.append(cachedDuration(mark1, mark2));
        }

        String timeString = cursorTimeText(time);
        if (timeString != null) {
          statusBuffer.append("\n");
          statusBuffer.append(timeString);
        }

        double offset = 0;
        double multiplier = 1;

        if (settings.viewType == ViewType.SPECTROGRAM) {
          unit = "Frequency (Hz)";
        } else {
          Metadata md = swarmConfig.getMetadata(channel);
          if (md != null) {
            offset = md.getOffset();
            multiplier = md.getMultiplier();
            unit = md.getUnit();
          }

          if (unit == null) {
            unit = "Counts";
          }
        }
        double yi2 = multiplier * yi + offset;
        statusBuffer.append(", ");
        statusBuffer.append(unit);
        statusBuffer.append(": ");
        statusBuffer.append(formatSample(yi2));
      } else {
        double xi = time;
        if (settings.viewType == ViewType.SPECTRA && settings.spectraLogFreq) {
          xi = Math.pow(10.0, xi);
        }
        if (settings.viewType == ViewType.SPECTRA && settings.spectraLogPower) {
          yi = Math.pow(10.0, yi);
        }
        statusBuffer.append(String.format("\nFrequency (Hz): %.6f, Power: %.3f", xi, yi));
      }

      if (settings.pickEnabled && pickData != null) {
        String pickStatus = cachedPickStatus();
        if (!pickStatus.isEmpty()) {
          statusBuffer.append("\n");
          statusBuffer.append(pickStatus);
        }
      }
    }

    // Copy before notifying listeners so a re-entrant hover cannot clear this buffer.
    final String statusTextValue = statusBuffer.toString();

    if (!pauseCursorMark && Double.compare(previousTime, time) != 0) {
      WaveViewTime.fireTimeChanged(time);
    }

    if (statusText != null) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          statusText.setText(statusTextValue);
        }
      });
    }

    return !statusTextValue.equals(" ");
  }

  /**
   * Wave header for the status bar. Sample range and RSAM stay fixed until the wave object changes.
   */
  private String cachedWaveInfo() {
    if (wave != waveInfoSource) {
      waveInfoSource = wave;
      waveInfoText = StatusTextArea.getWaveInfo(wave);
    }
    return waveInfoText;
  }

  /**
   * Same text as {@link StatusTextArea#getTimeString}. The formatter matches {@link Time#format}
   * and is reused for later samples instead of constructing one per hover.
   */
  private String cursorTimeText(double j2k) {
    TimeZone zone = swarmConfig.getTimeZone(channel);
    long bits = Double.doubleToLongBits(j2k);
    int raw = zone.getRawOffset();
    int dst = zone.getDSTSavings();
    if (cursorTextValid && bits == cursorTimeBits && zone == cursorZone && raw == cursorRawOffset
        && dst == cursorDstSavings) {
      return cursorText;
    }
    cursorText = formatCursorTime(j2k, zone);
    cursorTimeBits = bits;
    cursorZone = zone;
    cursorRawOffset = raw;
    cursorDstSavings = dst;
    cursorTextValid = true;
    return cursorText;
  }

  private String formatCursorTime(double j2k, TimeZone zone) {
    Locale locale = Locale.getDefault();
    if (cursorFormat == null || cursorFormatLocale != locale) {
      cursorFormat = new SimpleDateFormat(Time.STANDARD_TIME_FORMAT_MS);
      cursorFormat.setTimeZone(Time.DEFAULT_TIME_ZONE);
      cursorFormatLocale = locale;
    }
    String utc = formatCursorEpoch(j2k);
    long epoch = J2kSec.asEpoch(j2k);
    double tzo = zone.getOffset(epoch) / 1000.0;
    if (tzo != 0) {
      cursorDate.setTime(epoch);
      String tza = zone.getDisplayName(zone.inDaylightTime(cursorDate), TimeZone.SHORT);
      String local = formatCursorEpoch(j2k + tzo);
      return local + " (" + tza + "), " + utc + " (UTC)";
    }
    return utc;
  }

  private String formatCursorEpoch(double j2k) {
    cursorDate.setTime(J2kSec.asEpoch(j2k));
    return cursorFormat.format(cursorDate);
  }

  /**
   * Duration text for the current marks. Reused until the marks or magnitude coefficients change.
   */
  private String cachedDuration(double start, double end) {
    long mark1Bits = Double.doubleToLongBits(start);
    long mark2Bits = Double.doubleToLongBits(end);
    long aBits = Double.doubleToLongBits(swarmConfig.durationA);
    long bBits = Double.doubleToLongBits(swarmConfig.durationB);
    if (!durationTextValid || mark1Bits != durationMark1Bits || mark2Bits != durationMark2Bits
        || aBits != durationABits || bBits != durationBBits) {
      durationText = StatusTextArea.getDuration(start, end);
      durationMark1Bits = mark1Bits;
      durationMark2Bits = mark2Bits;
      durationABits = aBits;
      durationBBits = bBits;
      durationTextValid = true;
    }
    return durationText;
  }

  /** Count or frequency text. Reused while the scaled sample has the same bits. */
  private String formatSample(double yi2) {
    long bits = Double.doubleToLongBits(yi2);
    if (sampleTextValid && bits == sampleBits) {
      return sampleText;
    }
    double exp = Util.getExp(yi2);
    if (yi2 != 0.0 && (exp >= 5.0 || exp <= -5.0)) {
      sampleText = numberFormat.format(yi2 / pow10(exp)) + "e" + numberFormat.format(exp);
    } else {
      sampleText = numberFormat.format(yi2);
    }
    sampleBits = bits;
    sampleTextValid = true;
    return sampleText;
  }

  /**
   * S-P and coda text. The clipboard is still visited so a changed coda cannot be missed, and the
   * formatted line is reused when that visit sees the same durations and velocities.
   */
  private String cachedPickStatus() {
    double spDuration = pickData.getSpDuration();
    double codaDuration = pickData.getCodaDuration();
    double vp = SwarmConfig.getInstance().pVelocity;
    double ratio = SwarmConfig.getInstance().velocityRatio;
    boolean durationEnabled = swarmConfig.durationEnabled;
    double durationA = swarmConfig.durationA;
    double durationB = swarmConfig.durationB;
    int waveCount = -1;
    if (durationEnabled && !Double.isNaN(codaDuration)) {
      List<WaveViewPanel> waves = WaveClipboardFrame.getInstance().getWaves();
      waveCount = waves.size();
      if (codaScratch.length < waveCount) {
        codaScratch = new double[waveCount];
      }
      for (int i = 0; i < waveCount; i++) {
        codaScratch[i] = waves.get(i).pickData.getCodaDuration();
      }
    }
    if (pickStatusValid && sameBits(spDuration, pickSpBits) && sameBits(codaDuration, pickCodaBits)
        && sameBits(vp, pickVpBits) && sameBits(ratio, pickRatioBits)
        && sameBits(durationA, pickDurationABits) && sameBits(durationB, pickDurationBBits)
        && durationEnabled == pickDurationEnabled && waveCount == pickWaveCount
        && sameCoda(waveCount)) {
      return cachedPickStatus;
    }
    cachedPickStatus =
        buildPickStatus(spDuration, codaDuration, vp, ratio, durationEnabled, waveCount);
    pickSpBits = Double.doubleToLongBits(spDuration);
    pickCodaBits = Double.doubleToLongBits(codaDuration);
    pickVpBits = Double.doubleToLongBits(vp);
    pickRatioBits = Double.doubleToLongBits(ratio);
    pickDurationABits = Double.doubleToLongBits(durationA);
    pickDurationBBits = Double.doubleToLongBits(durationB);
    pickDurationEnabled = durationEnabled;
    pickWaveCount = waveCount;
    if (waveCount >= 0) {
      if (pickCodaDurations.length < waveCount) {
        pickCodaDurations = new double[waveCount];
      }
      System.arraycopy(codaScratch, 0, pickCodaDurations, 0, waveCount);
    }
    pickStatusValid = true;
    return cachedPickStatus;
  }

  private static boolean sameBits(double value, long bits) {
    return Double.doubleToLongBits(value) == bits;
  }

  private boolean sameCoda(int waveCount) {
    if (waveCount < 0) {
      return true;
    }
    if (pickCodaDurations.length < waveCount) {
      return false;
    }
    for (int i = 0; i < waveCount; i++) {
      long left = Double.doubleToLongBits(codaScratch[i]);
      long right = Double.doubleToLongBits(pickCodaDurations[i]);
      if (left != right) {
        return false;
      }
    }
    return true;
  }

  private String buildPickStatus(double spDuration, double codaDuration, double vp, double ratio,
      boolean durationEnabled, int waveCount) {
    String pickStatus = "";
    if (!Double.isNaN(spDuration)) {
      double vs = vp / ratio;
      double spDistance = spDuration * (vp * vs) / (vp - vs);
      pickStatus = String.format("S-P: %.2fs (%.2fkm)", spDuration, spDistance);
    }
    if (durationEnabled && !Double.isNaN(codaDuration)) {
      if (!pickStatus.equals("")) {
        pickStatus += "; ";
      }
      double durationMagnitude = swarmConfig.getDurationMagnitude(codaDuration);
      String coda = String.format("Coda: %.2fs (Mc: %.2f)", codaDuration, durationMagnitude);
      int count = 0;
      double sumDuration = 0;
      for (int i = 0; i < waveCount; i++) {
        double one = codaScratch[i];
        if (!Double.isNaN(one)) {
          sumDuration += one;
          count++;
        }
      }
      if (count == 1) {
        pickStatus += coda;
      } else {
        double avgDuration = sumDuration / count;
        double avgDurationMagnitude = swarmConfig.getDurationMagnitude(avgDuration);
        String avgCoda =
            String.format("Avg Coda: %.2fs (Mc: %.2f)", avgDuration, avgDurationMagnitude);
        pickStatus += coda + ", " + avgCoda;
      }
    }
    return pickStatus;
  }

  /**
   * Set wave.
   * 
   * @param sw wave to set to
   * @param st start time
   * @param et end time
   */
  public void setWave(Wave sw, double st, double et) {
    wave = sw;
    waveInfoSource = null;
    startTime = st;
    endTime = et;
    processSettings();
  }

  /**
   * Reset auto scale memory settings.
   */
  public void resetAutoScaleMemory() {
    minAmp = 1E300;
    maxAmp = -1E300;
    maxSpectraPower = -1E300;
    maxSpectrogramPower = -1E300;
    settings.autoScaleAmp = true;
    settings.autoScaleSpectrogramPower = true;
    settings.autoScaleSpectraPower = true;
    processSettings();
  }

  /**
   * Adjust scale by percent.
   * 
   * @param pct percent to scale to.
   */
  public void adjustScale(double pct) {
    if (settings.viewType == ViewType.WAVE) {
      double maxa = settings.autoScaleAmp ? wave.max() : settings.waveMaxAmp;
      double mina = settings.autoScaleAmp ? wave.min() : settings.waveMinAmp;
      settings.autoScaleAmp = false;
      double range = maxa - mina;
      double center = range / 2 + mina;
      double newRange = range * pct;
      settings.waveMinAmp = center - newRange / 2;
      settings.waveMaxAmp = center + newRange / 2;
    }

    if (settings.viewType == ViewType.SPECTRA) {
      settings.autoScaleSpectraPower = false;
      double maxf = settings.spectraMaxFreq * pct;
      settings.spectraMaxFreq =
          (maxf > wave.getSamplingRate() / 2) ? wave.getSamplingRate() / 2 : maxf;
    }
    
    if (settings.viewType == ViewType.SPECTROGRAM) {
      settings.autoScaleSpectrogramPower = false;
      double maxf = settings.spectrogramMaxFreq * pct;
      settings.spectrogramMaxFreq =
          (maxf > wave.getSamplingRate() / 2) ? wave.getSamplingRate() / 2 : maxf;
    }

    processSettings();
  }

  private synchronized BufferedImage getImage() {
    image = offscreen.displayed();
    return image;
  }

  /**
   * Create image.
   */
  public void createImage() {
    offscreen.render(this, new Offscreen.Painter() {
      public void paint(Graphics2D graphics) {
        constructPlot(graphics);
      }
    }, true);
  }

  /**
   * Does NOT call repaint for efficiency purposes, that is left to the container.
   */
  protected void processSettings() {
    if (wave == null || wave.buffer == null || wave.buffer.length == 0) {
      return;
    }

    if (settings.spectrogramMaxFreq > wave.getNyquist()) {
      settings.spectrogramMaxFreq = wave.getNyquist();
    }

    switch (settings.viewType) {
      case SPECTRA:
      case PARTICLE_MOTION:
        timeSeries = false;
        break;
      default:
        timeSeries = true;
    }

    createImage();
  }

  /**
   * Annotate image with pick marks.
   * 
   * @param g2 graphics
   */
  protected void annotateImage(Graphics2D g2) {
    if (timeSeries) {
      if (!Double.isNaN(mark1)) {
        paintMark(g2, mark1);
      }
      if (!Double.isNaN(mark2)) {
        paintMark(g2, mark2);
      }
    }
    if (settings.pickEnabled && pickData != null) {
      double[] t = getTranslation();
      if (t == null) {
        return;
      }
      if (!pickData.isHidePhases()) {
        for (String phase : PICK_PHASES) {
          drawPick(pickData.getPick(phase), g2, !pickData.isPickChannel(phase));
        }
      }
      if (!pickData.isHideCoda()) {
        for (String coda : CODA_PHASES) {
          drawPick(pickData.getPick(coda), g2, false);
        }
      }
    }
    if (settings.tagEnabled) {
      for (TagData tag : tagData) {
        drawTag(tag, g2, true);
      }
    }
  }

  /**
   * Paints the component on the specified graphics context.
   * 
   * @param g the graphics context
   */
  public void paint(Graphics g) {
    Graphics2D g2 = (Graphics2D) g;
    int width = getWidth();
    int height = getHeight();
    if (wave == null || wave.numSamples() < 1) {
      g2.setColor(backgroundColor);
      g2.fillRect(0, 0, width, height);
      g2.setColor(Color.black);
      if (working) {
        g2.drawString("Retrieving data...", width / 2 - 50, height / 2);
      } else {
        String error = "No wave data.";
        if (channel != null) {
          error = "No wave data for " + channel + ".";
        }
        int w = g2.getFontMetrics().stringWidth(error);
        g2.drawString(error, width / 2 - w / 2, height / 2);
      }
    } else {
      BufferedImage bi = getImage();
      if (bi != null) {
        g2.drawImage(bi, 0, 0, null);
      }

      if (dragging) {
        paintDragBox(g2);
      }

      annotateImage(g2);

      if (!Double.isNaN(cursorMark)) {
        paintCursor(g2);
      }
    }

    if (allowClose) {
      if (closeImg == null) {
        closeImg = Icons.close_view.getImage();
      }

      g2.drawImage(closeImg, width - 17, 3, null);
    }
    if (bottomBorderColor != null) {
      g2.setColor(bottomBorderColor);
      g2.drawLine(0, height - 1, width, height - 1);
    }
    if (borderColor != null) {
      g2.setColor(borderColor);
      g2.drawRect(0, 0, width - 1, height - 2);
    }
  }

  /**
   * Draw duration markers.
   * 
   * @param g2 graphics
   * @param j2k time in j2k
   */
  private void paintMark(Graphics2D g2, double j2k) {
    if (Double.isNaN(j2k) || j2k < startTime || j2k > endTime) {
      return;
    }

    double[] t = getTranslation();
    if (t == null) {
      return;
    }

    double x = (j2k - t[1]) / t[0];
    g2.setColor(DARK_GREEN);
    overlayLine.setLine(x, yOffset, x, getHeight() - bottomHeight - 1);
    g2.draw(overlayLine);

    markPath.reset();
    markPath.moveTo((float) x, yOffset);
    markPath.lineTo((float) x - 5, yOffset - 7);
    markPath.lineTo((float) x + 5, yOffset - 7);
    markPath.closePath();
    g2.setPaint(Color.GREEN);
    g2.fill(markPath);
    g2.setColor(DARK_GREEN);
    g2.draw(markPath);
  }

  /**
   * Draw pick marks.
   * 
   * @param pick pick to draw
   * @param g2 graphics
   * @param transparent true if tags should have transparent background
   */
  private void drawPick(Pick pick, Graphics2D g2, boolean transparent) {
    if (pick == null) {
      return;
    }

    long time = pick.getTime();
    double[] t = getTranslation();
    double j2k = J2kSec.fromEpoch(time);
    double x = (j2k - t[1]) / t[0];
    // draw line
    g2.setColor(PickWavePanel.DARK_GREEN);
    overlayLine.setLine(x, yOffset, x, getHeight() - bottomHeight - 1);
    g2.draw(overlayLine);

    // get color
    String tag = pick.getTag();
    Color color = Color.GRAY;
    Color uncertaintyShade = UNCERTAINTY_GRAY;
    if (tag.indexOf('P') != -1) {
      color = PickWavePanel.P_BACKGROUND;
      uncertaintyShade = UNCERTAINTY_P;
    } else if (tag.indexOf('S') != -1) {
      color = PickWavePanel.S_BACKGROUND;
      uncertaintyShade = UNCERTAINTY_S;
    } else if (tag.indexOf('C') != -1) {
      color = Color.YELLOW;
      uncertaintyShade = UNCERTAINTY_CODA;
    }
    g2.setColor(color);

    // draw uncertainty
    double uncertainty = pick.getTimeQuantity().getUncertainty();
    if (!Double.isNaN(uncertainty)) {
      g2.setColor(uncertaintyShade);
      // lowerUncertainty
      long lowTime = (long) (pick.getTime() - 1000.0 * uncertainty);
      double luJ2k = J2kSec.fromEpoch(lowTime);
      double luX = (luJ2k - t[1]) / t[0];
      g2.fillRect((int) luX, yOffset + 1, (int) (x - luX),
          getHeight() - bottomHeight - yOffset - 1);
      // upperUncertainty
      long highTime = (long) (pick.getTime() + 1000.0 * uncertainty);
      double uuJ2k = J2kSec.fromEpoch(highTime);
      double uuX = (uuJ2k - t[1]) / t[0];
      g2.fillRect((int) x, yOffset + 1, (int) (uuX - x), getHeight() - bottomHeight - yOffset - 1);
    }

    // draw tag/label box
    if (transparent) {
      g2.setColor(Color.WHITE);
    } else {
      g2.setColor(color);
    }
    FontMetrics fm = g2.getFontMetrics();
    int width = fm.stringWidth(tag);
    int height = fm.getAscent();
    int offset = 2;
    int lw = width + 2 * offset;
    g2.fillRect((int) x, 3, lw, height + 2 * offset);

    // draw text in tag/label box
    g2.setColor(Color.BLACK);
    g2.drawRect((int) x, 3, lw, height + 2 * offset);

    g2.drawString(tag, (int) x + offset, 3 + (fm.getAscent() + offset));

  }

  /**
   * Draw tag.
   * 
   * @param tag tag data to draw
   * @param g2 graphics
   * @param transparent true if tags should have transparent background
   */
  private void drawTag(TagData tag, Graphics2D g2, boolean transparent) {
    if (tag == null) {
      return;
    }

    double[] t = getTranslation();
    double j2k = tag.startTime;
    double x = (j2k - t[1]) / t[0];

    // draw line
    g2.setColor(PickWavePanel.DARK_GREEN);
    overlayLine.setLine(x, yOffset, x, getHeight() - bottomHeight - 1);
    g2.draw(overlayLine);

    // get color
    Color color = Color.GRAY;
    g2.setColor(color);

    // draw tag/label box
    if (transparent) {
      g2.setColor(Color.WHITE);
    } else {
      g2.setColor(color);
    }
    FontMetrics fm = g2.getFontMetrics();
    int width = fm.stringWidth(tag.classification);
    int height = fm.getAscent();
    int offset = 2;
    int lw = width + 2 * offset;
    g2.fillRect((int) x, 3, lw, height + 2 * offset);

    // draw text in tag/label box
    g2.setColor(Color.BLACK);
    g2.drawRect((int) x, 3, lw, height + 2 * offset);

    g2.drawString(tag.classification, (int) x + offset, 3 + (fm.getAscent() + offset));

  }

  public void setUseFilterLabel(boolean b) {
    useFilterLabel = b;
  }

  public TextRenderer getFilterLabel() {
    return getFilterLabel(xOffset + 5, 148, TextRenderer.NONE, TextRenderer.NONE);
  }

  /**
   * Get filter label.
   * 
   * @param x x text location
   * @param y y text location
   * @param horizJustification horizontal justification
   * @param vertJustification vertical justification
   * @return text renderer
   */
  public TextRenderer getFilterLabel(int x, int y, int horizJustification, int vertJustification) {
    String ft = "";
    switch (settings.filter.getType()) {
      case BANDPASS:
        ft = "Band pass [" + settings.filter.getCorner1() + "-" + settings.filter.getCorner2()
            + " Hz]";
        break;
      case HIGHPASS:
        ft = "High pass [" + settings.filter.getCorner1() + " Hz]";
        break;
      case LOWPASS:
        ft = "Low pass [" + settings.filter.getCorner1() + " Hz]";
        break;
      default:
        break;
    }
    TextRenderer tr = new TextRenderer(x, y, ft);
    tr.horizJustification = horizJustification;
    tr.vertJustification = vertJustification;
    tr.color = Color.red;
    return tr;
  }

  /**
   * Constructs the plot on the specified graphics context.
   * 
   * @param g2 the graphics context
   */
  private synchronized void constructPlot(Graphics2D g2) {
    if (wave == null) {
      return;
    }
    Dimension dim = this.getSize();

    if (plot == null) {
      plot = new Plot();
    } else {
      plot.clear();
    }
    plot.setBackgroundColor(backgroundColor);
    plot.setSize(dim);
    Wave renderWave = waveForRender();
    switch (settings.viewType) {
      case WAVE:
        plotWave(plot, renderWave);
        break;
      case SPECTRA:
        plotSpectra(plot, renderWave);
        break;
      case SPECTROGRAM:
        plotSpectrogram(plot, renderWave);
        break;
      case PARTICLE_MOTION:
        plotParticleMotion(plot, renderWave);
        break;
      default:
        break;
    }

    try {
      plot.render(g2);
    } catch (PlotException e) {
      e.printStackTrace();
    }

  }

  /**
   * Filtered copy of the current wave. Reused while the samples and filter settings are unchanged.
   */
  private Wave waveForRender() {
    if (!settings.filterOn || wave == null) {
      filteredWave = null;
      filteredSource = null;
      filteredBuffer = null;
      return wave;
    }
    if (filteredWave == null || filteredSource != wave || filteredBuffer != wave.buffer
        || filteredZeroPhase != settings.zeroPhaseShift
        || filteredOrder != settings.filter.getOrder() || filteredType != settings.filter.getType()
        || Double.doubleToLongBits(filteredCorner1) != Double
            .doubleToLongBits(settings.filter.getCorner1())
        || Double.doubleToLongBits(filteredCorner2) != Double
            .doubleToLongBits(settings.filter.getCorner2())
        || Double.doubleToLongBits(filteredRate) != Double
            .doubleToLongBits(wave.getSamplingRate())) {
      filteredWave = new Wave(wave);
      filteredWave.filter(settings.filter, settings.zeroPhaseShift);
      filteredSource = wave;
      filteredBuffer = wave.buffer;
      filteredZeroPhase = settings.zeroPhaseShift;
      filteredOrder = settings.filter.getOrder();
      filteredType = settings.filter.getType();
      filteredCorner1 = settings.filter.getCorner1();
      filteredCorner2 = settings.filter.getCorner2();
      filteredRate = wave.getSamplingRate();
    }
    return filteredWave;
  }

  /**
   * Plots a wave.
   * 
   * @param renderWave the wave to plot
   */
  protected void plotWave(Plot plot, Wave renderWave) {
    if (renderWave == null || renderWave.numSamples() == 0) {
      return;
    }

    SliceWave wv = new SliceWave(renderWave);
    wv.setSlice(startTime, endTime);

    double offset = 0;
    double multiplier = 1;
    Metadata md = swarmConfig.getMetadata(channel);

    if (settings.useUnits && md != null) {
      offset = md.getOffset();
      multiplier = md.getMultiplier();
    }

    double bias = 0;
    if (settings.removeBias) {
      bias = wv.mean();
    }

    double minY = (settings.waveMinAmp - offset) / multiplier;
    double maxY = (settings.waveMaxAmp - offset) / multiplier;

    if (settings.autoScaleAmp) {
      double[] dr = new double[] { wv.min(), wv.max() };
      if (settings.autoScaleAmpMemory) {
        minY = Math.min(minAmp, dr[0] - bias);
        maxY = Math.max(maxAmp, dr[1] - bias);
        minAmp = Math.min(minY, minAmp);
        maxAmp = Math.max(maxY, maxAmp);
      } else {
        minY = dr[0] - bias;
        maxY = dr[1] - bias;
      }
    }

    if (waveRenderer == null) {
      waveRenderer = new SliceWaveRenderer();
    }

    if (decorator != null) {
      waveRenderer.setFrameDecorator(decorator);
    }

    if (settings.useUnits && md != null && md.getUnit() != null) {
      waveRenderer.setYLabelText(md.getUnit());
    } else {
      waveRenderer.setYLabelText("Counts");
    }

    waveRenderer.setYAxisCoefficients(multiplier, offset);
    waveRenderer.setLocation(xOffset, yOffset, this.getWidth() - xOffset - rightWidth,
        this.getHeight() - yOffset - bottomHeight);
    waveRenderer.setYLimits(minY, maxY);
    waveRenderer.setViewTimes(startTime, endTime, "");
    waveRenderer.setWave(wv);
    waveRenderer.setRemoveBias(settings.removeBias);
    if (channel != null && displayTitle) {
      waveRenderer.setTitle(channel);
      waveRenderer.setDate(J2kSec.asDate(startTime));
    }

    waveRenderer.update();
    plot.addRenderer(waveRenderer);
    if (useFilterLabel && settings.filterOn) {
      plot.addRenderer(getFilterLabel(getWidth() - rightWidth, getHeight() - bottomHeight,
          TextRenderer.RIGHT, TextRenderer.BOTTOM));
    }
    translation = waveRenderer.getDefaultTranslation();
  }

  /**
   * Plots frequency spectra.
   * 
   * @param renderWave the wave to plot
   */
  private void plotSpectra(Plot plot, Wave renderWave) {
    if (renderWave == null || renderWave.numSamples() == 0) {
      return;
    }

    SliceWave wv = new SliceWave(renderWave);
    wv.setSlice(startTime, endTime);

    if (spectraRenderer == null) {
      spectraRenderer = new SpectraRenderer();
    }

    if (decorator != null) {
      spectraRenderer.setFrameDecorator(decorator);
    }

    spectraRenderer.setLocation(xOffset, yOffset, this.getWidth() - rightWidth - xOffset,
        this.getHeight() - bottomHeight - yOffset);
    spectraRenderer.setWave(wv);

    spectraRenderer.setLogPower(settings.spectraLogPower);
    spectraRenderer.setLogFreq(settings.spectraLogFreq);
    spectraRenderer.setMaxFreq(settings.spectraMaxFreq);
    spectraRenderer.setMinFreq(settings.spectraMinFreq);
    spectraRenderer.setAutoScale(settings.autoScaleSpectraPower);
    spectraRenderer.setMinY(settings.spectraMinPower);
    spectraRenderer.setMaxY(settings.spectraMaxPower);
    spectraRenderer.setYUnitText("Power");
    if (channel != null && displayTitle) {
      spectraRenderer.setTitle(channel);
      spectraRenderer.setDate(J2kSec.asDate(startTime));
    }

    spectraRenderer.update();
    if (useFilterLabel && settings.filterOn) {
      plot.addRenderer(getFilterLabel(getWidth() - rightWidth, getHeight() - bottomHeight,
          TextRenderer.RIGHT, TextRenderer.BOTTOM));
    }

    translation = spectraRenderer.getDefaultTranslation();
    plot.addRenderer(spectraRenderer);
  }

  /**
   * Plots a spectrogram. TODO: Fix logPower.
   * 
   * @param renderWave the wave to plot
   */
  private void plotSpectrogram(Plot plot, Wave renderWave) {
    if (renderWave == null || renderWave.numSamples() == 0) {
      return;
    }

    SliceWave wv = new SliceWave(renderWave);
    wv.setSlice(startTime, endTime);

    if (spectrogramRenderer == null) {
      spectrogramRenderer = new SpectrogramRenderer();
    }

    if (decorator != null) {
      spectrogramRenderer.setFrameDecorator(decorator);
    }

    if (settings.useAlternateSpectrum) {
      spectrogramRenderer.setSpectrum(Inferno.getInstance());
    } else {
      spectrogramRenderer.setSpectrum(Jet2.getInstance());
    }

    spectrogramRenderer.setLocation(xOffset, yOffset, this.getWidth() - rightWidth - xOffset,
        this.getHeight() - bottomHeight - yOffset);
    spectrogramRenderer.setWave(wv);

    spectrogramRenderer.setViewStartTime(startTime);
    spectrogramRenderer.setViewEndTime(endTime);
    spectrogramRenderer.setAutoScale(settings.autoScaleSpectrogramPower);
    spectrogramRenderer.setLogPower(settings.spectrogramLogPower);

    spectrogramRenderer.setOverlap(settings.spectrogramOverlap);
    spectrogramRenderer.setMaxFreq(settings.spectrogramMaxFreq);
    spectrogramRenderer.setMinFreq(settings.spectrogramMinFreq);

    spectrogramRenderer.setMaxPower(settings.spectrogramMaxPower);
    spectrogramRenderer.setMinPower(settings.spectrogramMinPower);

    spectrogramRenderer.setBinSize(ceilPowerOfTwo(settings.binSize * wave.getSamplingRate()));

    if (channel != null && displayTitle) {
      spectrogramRenderer.setTitle(channel);
      spectrogramRenderer.setDate(J2kSec.asDate(startTime));
    }

    spectrogramRenderer.setYUnitText("Frequency (Hz)");

    spectrogramRenderer.setNfft(settings.nfft);

    double[] power = spectrogramRenderer.update();

    settings.spectrogramMinPower = power[0];
    settings.spectrogramMaxPower = power[1];

    plot.addRenderer(spectrogramRenderer);
    if (useFilterLabel && settings.filterOn) {
      plot.addRenderer(getFilterLabel(getWidth() - rightWidth, getHeight() - bottomHeight,
          TextRenderer.RIGHT, TextRenderer.BOTTOM));
    }
    translation = spectrogramRenderer.getDefaultTranslation();
  }

  /**
   * Plot particle motion using detrended data.
   */
  private void plotParticleMotion(Plot plot, Wave wave) {
    if (source == null) {
      String message = "Not supported.";
      TextRenderer renderer = new TextRenderer(30, 30, message);
      plot.addRenderer(renderer);
      return;
    }
    Metadata md = swarmConfig.getMetadata(channel);
    if (md == null) {
      String message = "Unable to plot due to invalid or missing metadata.";
      TextRenderer renderer = new TextRenderer(30, 30, message);
      plot.addRenderer(renderer);
      message = "Channel: " + channel;
      renderer = new TextRenderer(30, 60, message);
      plot.addRenderer(renderer);
      return;
    }
    String s = md.getScnl().station;
    String c = md.getScnl().channel;
    String n = md.getScnl().network == null ? "" : md.getScnl().network;
    String l = md.getScnl().location == null ? "" : md.getScnl().location;

    if (s == null || c == null) {
      String message = "Unable to plot due to missing Station or Channel information.";
      TextRenderer renderer = new TextRenderer(30, 30, message);
      plot.addRenderer(renderer);
      return;
    }

    SliceWave swave = new SliceWave(wave);
    swave.setSlice(startTime, endTime);
    String component = c.substring(2);
    String[] orientationType = new String[] { "Z", "N", "E" };
    if (component.matches("[ABC]")) {
      orientationType = new String[] { "A", "B", "C" };
    }
    if (component.matches("[123]")) {
      orientationType = new String[] { "1", "2", "3" };
    }
    if (component.matches("[UVW]")) {
      orientationType = new String[] { "U", "V", "W" };
    }
    if (settings.useAlternateOrientationCode
        && component.matches("[" + settings.alternateOrientationCode + "]")) {
      String zOrientation = settings.alternateOrientationCode.substring(0, 1);
      String nOrientation = settings.alternateOrientationCode.substring(1, 2);
      String eOrientation = settings.alternateOrientationCode.substring(2);
      orientationType = new String[] { zOrientation, nOrientation, eOrientation };
    }
    HashMap<String, double[]> data = new HashMap<String, double[]>();
    HashMap<String, String> stations = new HashMap<String, String>();
    data.put(component, swave.getSignal());
    stations.put(component, channel);
    for (String direction : orientationType) {
      if (!component.equals(direction)) {
        String newChannel = c.replaceFirst(".$", direction);
        String newStation = s + " " + newChannel + " " + n;
        if (!l.equals("")) {
          newStation += " " + l;
        }
        stations.put(direction, newStation);
        Wave w = source.getWave(newStation, startTime, endTime);
        if (w != null && w.buffer != null) {
          if (settings.filterOn) {
            w.filter(settings.filter, settings.zeroPhaseShift);
          }
          try {
            SliceWave sw = new SliceWave(w);
            sw.setSlice(startTime, endTime);
            data.put(direction, sw.getSignal());
          } catch (NullPointerException e) {
            data.put(direction, new double[0]);
          }
        } else {
          data.put(direction, new double[0]);
        }
      }
    }

    ParticleMotionRenderer particleMotionRenderer =
        new ParticleMotionRenderer(data.get(orientationType[2]), data.get(orientationType[1]),
            data.get(orientationType[0]), stations.get(orientationType[2]),
            stations.get(orientationType[1]), stations.get(orientationType[0]));
    particleMotionRenderer.setLocation(xOffset, yOffset, this.getWidth() - rightWidth - xOffset,
        this.getHeight());

    if (channel != null && displayTitle) {
      String title = s + " " + c.replaceFirst(".$", "*") + " " + n + " " + l;
      particleMotionRenderer.setTitle(title.trim());
      particleMotionRenderer.setDate(J2kSec.asDate(startTime));
    }
    plot.addRenderer(particleMotionRenderer);
    if (useFilterLabel && settings.filterOn) {
      plot.addRenderer(getFilterLabel(getWidth() - rightWidth, getHeight() - bottomHeight,
          TextRenderer.RIGHT, TextRenderer.BOTTOM));
    }

    translation = null;
  }

  /**
   * Smallest power of two greater than or equal to the sample count.
   */
  private static int ceilPowerOfTwo(double samples) {
    // highestOneBit matches pow(2, ceil(log2)) only through 2^28.
    if (!(samples >= 1.0) || samples > (1 << 28)) {
      return (int) Math.pow(2.0, Math.ceil(Math.log(samples) / Math.log(2.0)));
    }
    int rounded = (int) Math.ceil(samples);
    int bin = Integer.highestOneBit(rounded);
    return bin < rounded ? bin << 1 : bin;
  }

  /**
   * Paints the zoom drag box.
   * 
   * @param g2 the graphics context
   */
  private void paintDragBox(Graphics2D g2) {
    int x1 = Math.min(highlightX1, highlightX2);
    int x2 = Math.max(highlightX1, highlightX2);
    int width = x2 - x1 + 1;
    Paint pnt = g2.getPaint();
    g2.setPaint(DRAG_HIGHLIGHT);
    g2.fillRect(x1, yOffset + 1, width, getHeight() - bottomHeight - yOffset);
    g2.setPaint(pnt);
  }

  public void setCursorMark(double j2k) {
    if (Double.compare(cursorMark, j2k) == 0) {
      return;
    }
    // Repaint only when the old or new time would draw. NaN limits fail both
    // comparisons, so they stay inside, matching paintCursor.
    boolean draw = cursorInView(cursorMark) || cursorInView(j2k);
    cursorMark = j2k;
    if (draw) {
      repaint();
    }
  }

  private boolean cursorInView(double j2k) {
    if (wave == null || wave.numSamples() < 1 || Double.isNaN(j2k) || translation == null) {
      return false;
    }
    return !(j2k < startTime) && !(j2k > endTime);
  }

  private void paintCursor(Graphics2D g2) {
    if (Double.isNaN(cursorMark) || cursorMark < startTime || cursorMark > endTime) {
      return;
    }

    double[] t = getTranslation();
    if (t == null) {
      return;
    }
    double x = (cursorMark - t[1]) / t[0];
    g2.setColor(DARK_RED);
    overlayLine.setLine(x, yOffset + 1, x, getHeight() - bottomHeight - 1);
    g2.draw(overlayLine);
  }


  /**
   * Overload of Component. Always returns the developer-specified size.
   * 
   * @return the size of the component
   */
  public Dimension getPreferredSize() {
    return getSize();
  }

  /**
   * Overload of Component. Always returns the developer-specified size.
   * 
   * @return the size of the component
   */
  public Dimension getMinimumSize() {
    return getSize();
  }

  /**
   * Overload of Component. Always returns the developer-specified size.
   * 
   * @return the size of the component
   */
  public Dimension getMaximumSize() {
    return getSize();
  }

  public void setMarks(double m1, double m2) {
    mark1 = m1;
    mark2 = m2;
  }

  /**
   * Determines if the other wave view panel represents a wave from the same station. This means it
   * has the same station, network, and location.
   * 
   * @param wvp another wave view panel
   * @return true if same station
   */
  public boolean isSameStation(WaveViewPanel wvp) {
    Metadata thisMd = swarmConfig.getMetadata(channel);
    if (thisMd == null) {
      return false;
    }
    String thatChannel = wvp.channel;
    Metadata thatMd = swarmConfig.getMetadata(thatChannel);
    if (thatMd == null) {
      return false;
    }

    // get this station info
    String thisS = thisMd.getScnl().station;
    String thisN = thisMd.getScnl().network;
    String thisL = thisMd.getScnl().location == null ? "" : thisMd.getScnl().location;

    // get that station info
    String thatS = thatMd.getScnl().station;
    String thatN = thatMd.getScnl().network;
    String thatL = thatMd.getScnl().location == null ? "" : thatMd.getScnl().location;

    // compare
    if (!thisS.equals(thatS)) {
      return false;
    }
    if (!thisN.equals(thatN)) {
      return false;
    }
    if (!thisL.equals(thatL)) {
      return false;
    }
    return true;
  }

  /**
   * Get pick data.
   * 
   * @return pick data
   */
  public PickData getPickData() {
    if (pickData == null) {
      pickData = new PickData();
    }
    return pickData;
  }

  public Double getMark1() {
    return mark1;
  }

  public Double getMark2() {
    return mark2;
  }

  public void setTagData(ArrayList<TagData> tagData) {
    this.tagData = tagData;
  }

  public void setTagMenu(TagMenu tagMenu) {
    this.tagMenu = tagMenu;
  }
}
