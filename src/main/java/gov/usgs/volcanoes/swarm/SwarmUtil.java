package gov.usgs.volcanoes.swarm;

import com.jgoodies.looks.plastic.PlasticLookAndFeel;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.JTextComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * $Log: not supported by cvs2svn $ Revision 1.1 2006/04/17 04:16:36 dcervelli More 1.3 changes.
 *
 * @author Dan Cervelli
 */
public class SwarmUtil implements SwarmConst {
  private static final Logger LOGGER = LoggerFactory.getLogger(SwarmUtil.class);
  private static final DateTimeFormatter DATE_FORMATTER;
  private static final DateTimeFormatter DATE_PARSER;
  static {
    DATE_FORMATTER =
        new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    DATE_PARSER = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral('T')
    .append(DateTimeFormatter.ISO_LOCAL_TIME).parseLenient()
    .parseCaseInsensitive().optionalStart().appendOffsetId()
    .optionalEnd().optionalStart().appendOffset("+HHMMSS", "Z")
    .optionalEnd().optionalStart().appendOffset("+HHMM", "Z")
    .optionalEnd().optionalStart().appendOffset("+HH", "Z")
    .optionalEnd().parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
    .toFormatter();
  }

  /**
   * Launches the default browser to display a {@code URI}.
   * 
   * @param uri the URI to be displayed in the user default browser
   * @see #isBrowseSupported()
   */
  public static void browse(URI uri) {
    try {
      Desktop.getDesktop().browse(uri);
    } catch (Exception ex) {
      LOGGER.warn(ex.toString());
    }
  }

  /**
   * Constructs a URI by parsing the given string.
   * 
   * @param str The string to be parsed into a URI.
   * @return the URI or null if error.
   */
  public static URI createURI(String str) {
    URI uri = null;
    try {
      uri = new URI(str);
    } catch (Exception ex) {
    }
    return uri;
  }

  /**
   * Tests whether the "browse" action is supported on the current platform.
   * 
   * @return true if supported, false otherwise.
   */
  public static boolean isBrowseSupported() {
    return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
  }

  /**
   * Create stripped split pane.
   * 
   * @param orient orientation
   * @param comp1 component 1
   * @param comp2 component 2
   * @return
   */
  public static JSplitPane createStrippedSplitPane(int orient, JComponent comp1, JComponent comp2) {
    JSplitPane split = new JSplitPane(orient, comp1, comp2);
    split.setBorder(BorderFactory.createEmptyBorder());
    SplitPaneUI splitPaneUi = split.getUI();
    if (splitPaneUi instanceof BasicSplitPaneUI) {
      BasicSplitPaneUI basicUi = (BasicSplitPaneUI) splitPaneUi;
      basicUi.getDivider().setBorder(BorderFactory.createEmptyBorder());
    }
    return split;
  }

  /**
   * Create a text component.
   * @param text the text to be displayed.
   * @param columns the number of columns.
   * @return the text component.
   */
  public static JTextComponent createTextComponent(String text, int columns) {
    JTextArea c = new JTextArea(text, 0, columns);
    c.setBackground(null);
    if (columns != 0) {
      c.setLineWrap(true);
      c.setWrapStyleWord(true);
    }
    return c;
  }

  /**
   * Create tool bar.
   * 
   * @return
   */
  public static JToolBar createToolBar() {
    JToolBar tb = new JToolBar();
    tb.setFloatable(false);
    tb.setRollover(true);
    tb.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
    return tb;
  }

  /**
   * Create tool bar button.
   * 
   * @param ic image icon
   * @param toolTip tool tip string
   * @param al action listener
   * @return
   */
  public static JButton createToolBarButton(ImageIcon ic, String toolTip, ActionListener al) {
    JButton button = new JButton(ic);
    fixButton(button, toolTip);
    if (al != null) {
      button.addActionListener(al);
    }

    return button;
  }

  /**
   * Create tool bar toggle button.
   * 
   * @param ic image icon
   * @param toolTip tool tip string
   * @param al action listener
   * @return
   */
  public static JToggleButton createToolBarToggleButton(ImageIcon ic, String toolTip,
      ActionListener al) {
    JToggleButton button = new JToggleButton(ic);
    fixButton(button, toolTip);
    if (al != null) {
      button.addActionListener(al);
    }

    return button;
  }

  private static void fixButton(AbstractButton button, String toolTip) {
    button.setFocusable(false);
    button.setMargin(ZERO_INSETS);
    button.setToolTipText(toolTip);
  }

  /**
   * Get the text for the specified date.
   * 
   * @param date the date.
   * @return the text.
   */
  public static String getDateText(final Date date) {
    return getDateText(Instant.ofEpochMilli(date.getTime()));
  }

  /**
   * Get the text for the specified date.
   * 
   * @param temporal the temporal object to format, not null.
   * @return the text.
   */
  public static String getDateText(final TemporalAccessor temporal) {
    return DATE_FORMATTER.format(temporal);
  }

  /**
   * Search for value in array of integers.
   * 
   * @param array of int
   * @param val value
   * @return index in array
   */
  public static int linearSearch(int[] array, int val) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == val) {
        return i;
      }
    }

    return -1;
  }

  /**
   * I've modified the standard jgoodies border to be thicker to make interal frame resizes easier.
   */
  public static Border getInternalFrameBorder() {
    return new InternalFrameBorder();
  }

  private static final class InternalFrameBorder extends AbstractBorder implements UIResource {
    private static final long serialVersionUID = 1L;
    private static final Insets NORMAL_INSETS = new Insets(3, 3, 3, 3);
    private static final Insets MAXIMIZED_INSETS = new Insets(1, 1, 0, 0);

    private void drawInsetThinFlush3DBorder(Graphics g, int x, int y, int w, int h) {
      g.translate(x, y);
      g.setColor(PlasticLookAndFeel.getControlHighlight());
      g.drawLine(2, 2, w - 4, 2);
      g.drawLine(2, 2, 2, h - 4);
      g.setColor(PlasticLookAndFeel.getControlDarkShadow());
      g.drawLine(w - 3, 2, w - 3, h - 4);
      g.drawLine(2, h - 3, w - 3, h - 3);
      g.translate(-x, -y);
    }

    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
      JInternalFrame frame = (JInternalFrame) c;
      if (frame.isMaximum()) {
        paintMaximizedBorder(g, x, y, w, h);
      } else {
        drawInsetThinFlush3DBorder(g, x, y, w, h);
      }
    }

    private void paintMaximizedBorder(Graphics g, int x, int y, int w, int h) {
      g.translate(x, y);
      g.setColor(PlasticLookAndFeel.getControlHighlight());
      g.drawLine(0, 0, w - 2, 0);
      g.drawLine(0, 0, 0, h - 2);
      g.translate(-x, -y);
    }

    public Insets getBorderInsets(Component c) {
      return ((JInternalFrame) c).isMaximum() ? MAXIMIZED_INSETS : NORMAL_INSETS;
    }
  }

  /**
   * Get the date for the text.
   * 
   * @param s the text.
   * @return the date or null if none or error.
   */
  public static Date parseDate(final String s) {
    try {
      Instant date = parseInstant(s);
      return new Date(date.toEpochMilli());
    } catch (Exception ex) {
    }
    return null;
  }

  /**
   * Parse the date text.
   * 
   * @param s the text.
   * @return the parsed temporal object, not null.
   */
  public static TemporalAccessor parseDateText(final String s) {
    return DATE_PARSER.parse(s);
  }

  /**
   * Get the instant for the text.
   * 
   * @param s the text.
   * @return the instant.
   * @throws DateTimeParseException if unable to parse the requested result
   */
  public static Instant parseInstant(final String s) throws DateTimeParseException {
    return Instant.from(DATE_PARSER.parse(s));
  }

  /**
   * Write the contents of the specified URL.
   * 
   * @param in  the input.
   * @param out the output.
   * @throws Exception if error.
   */
  public static void writeText(InputStream in, PrintStream out) throws Exception {
    String line;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
      while ((line = br.readLine()) != null) {
        out.println(line);
      }
    }
  }
}
