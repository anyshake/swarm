/**
 * I waive copyright and related rights in the this work worldwide through the CC0 1.0 Universal
 * public domain dedication. https://creativecommons.org/publicdomain/zero/1.0/legalcode
 */

package gov.usgs.volcanoes.swarm.chooser.node;

import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.time.Time;
import gov.usgs.volcanoes.swarm.Icons;
import gov.usgs.volcanoes.swarm.Metadata;
import gov.usgs.volcanoes.swarm.SwarmConfig;
import java.text.SimpleDateFormat;
import java.util.Locale;
import javax.swing.Icon;

public class ChannelNode extends AbstractChooserNode {
  public static final int ONE_DAY_S = 60 * 60 * 24;
  public static final String TOOL_TIP_DATE_FORMAT = "MMM dd, yyyy";

  private static final long serialVersionUID = 1L;

  /** Guards the shared tool tip date format, which is only rebuilt when the locale changes. */
  private static final Object TOOL_TIP_LOCK = new Object();
  private static SimpleDateFormat toolTipFormat;
  private static Locale toolTipLocale;

  private String channel;

  public ChannelNode(String c) {
    channel = c;
    label = channel;
  }

  /**
   * Get icon for channel bullet.
   * 
   * @see gov.usgs.volcanoes.swarm.chooser.node.AbstractChooserNode#getIcon()
   */
  public Icon getIcon() {
    Metadata md = SwarmConfig.getInstance().getMetadata(channel);
    if (md == null || !md.isTouched()) {
      return Icons.graybullet;
    } else if (md.hasLonLat()) {
      return Icons.bluebullet;
    } else {
      return Icons.bullet;
    }
  }

  public String getChannel() {
    return channel;
  }

  /**
   * Get tool tip for channel bullet.
   * 
   * @see gov.usgs.volcanoes.swarm.chooser.node.AbstractChooserNode#getToolTip()
   */
  public String getToolTip() {
    Metadata md = SwarmConfig.getInstance().getMetadata(channel);
    if (md == null) {
      return null;
    }
    double minTime = md.getMinTime();
    double maxTime = md.getMaxTime();
    if (Double.isNaN(minTime) || Double.isNaN(maxTime)) {
      return "No data";
    } else {
      return formatToolTipRange(minTime, maxTime);
    }
  }

  /**
   * Format a time range for a tool tip. The text is the same as joining two calls to
   * J2kSec.format(TOOL_TIP_DATE_FORMAT, t) with " - ", without building a new date format for each
   * date of each rendered row.
   *
   * @param minTime start time in j2k
   * @param maxTime end time in j2k
   * @return the formatted range
   */
  public static String formatToolTipRange(double minTime, double maxTime) {
    synchronized (TOOL_TIP_LOCK) {
      Locale locale = Locale.getDefault(Locale.Category.FORMAT);
      if (toolTipFormat == null || !locale.equals(toolTipLocale)) {
        toolTipFormat = new SimpleDateFormat(TOOL_TIP_DATE_FORMAT, locale);
        toolTipFormat.setTimeZone(Time.DEFAULT_TIME_ZONE);
        toolTipLocale = locale;
      }
      return toolTipFormat.format(J2kSec.asDate(minTime)) + " - "
          + toolTipFormat.format(J2kSec.asDate(maxTime));
    }
  }

  /**
   * Check to see if data is stale.
   * 
   * @return
   */
  public boolean isStale() {
    Metadata md = SwarmConfig.getInstance().getMetadata(channel);
    if (md == null) {
      return false;
    }
    double maxTime = md.getMaxTime();
    if (Double.isNaN(maxTime) || J2kSec.now() - maxTime > ONE_DAY_S) {
      return true;
    } else {
      return false;
    }
  }
}
