package gov.usgs.volcanoes.swarm.chooser;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.FormLayout;

import gov.usgs.volcanoes.swarm.CancelDialog;
import gov.usgs.volcanoes.swarm.SwarmConfig;
import gov.usgs.volcanoes.swarm.SwarmUtil;
import gov.usgs.volcanoes.swarm.data.fdsnws.OutputLevel;
import gov.usgs.volcanoes.swarm.data.fdsnws.WebServiceConst;
import gov.usgs.volcanoes.swarm.data.fdsnws.WebServiceStationClient;
import gov.usgs.volcanoes.swarm.data.fdsnws.WebServiceStationClientFactory;
import gov.usgs.volcanoes.swarm.data.fdsnws.WebServiceUtils;
import gov.usgs.volcanoes.swarm.data.fdsnws.WebServicesSource;

/**
 * The Web Services panel is a data source panel for Web Services.
 * 
 * @author Kevin Frechette (ISTI)
 */
public class WebServicesPanel extends DataSourcePanel implements WebServiceConst {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebServicesPanel.class);
  private static final String codeText = ";" + WebServicesSource.typeString + ":";
  private static final String portableFdsnwsDataselectTooltipText = "Select if portable fdsnws-dataselect instead of fdsnws-station";
  private static final int MAX_ERROR_COLUMNS = 40;
  private static final String NEED_UPDATE = "---Need Update---";
  private static final String UPDATING_LIST = "---Updating List---";

  private static int getNetworkLength(String net) {
    int len = net.indexOf(VALUE_DELIMITER);
    if (len == -1) {
      len = net.length();
    }
    return len;
  }

  private static void setText(JTextComponent comp, String text) {
    if (text == null) {
      text = "";
    }
    // if text changed
    if (!comp.getText().equals(text)) {
      comp.setText(text); // set the text
    }
  }

  private JTextField channel;
  private String currentStationUrl = "";
  private JTextField gulperDelay;
  private JTextField gulperSize;
  private JTextField location;
  private JComboBox<String> network;
  private JTextField station;
  private JButton updateNetworkList;
  private JCheckBox portableFdsnwsDataselect;
  private JTextField wsDataselectUrlField;
  private JTextField wsStationUrlField;

  /**
   * Create the Web Services server panel.
   */
  public WebServicesPanel() {
    super(WebServicesSource.typeString, WebServicesSource.TAB_TITLE);
  }

  /**
   * Determines if the OK should be allowed.
   *
   * @return true if allowed, false otherwise.
   */
  public boolean allowOk(boolean edit) {
    final StringBuilder message = new StringBuilder();
    double gs = -1;
    try {
      gs = Double.parseDouble(gulperSize.getText());
    } catch (Exception e) {
      //
    }
    if (gs <= 0) {
      message.append("The gulper size must be greater than 0 minutes.");
    }

    double gd = -1;
    try {
      gd = Double.parseDouble(gulperDelay.getText());
    } catch (Exception e) {
      //
    }
    if (gd < 0) {
      if (message.length() != 0) {
        message.append("\n");
      }
      message.append("The gulper delay must be greater than or equal to 0 seconds.");
    }

    final String selnet = getSelectedNetwork();
    if (selnet != null && !selnet.isEmpty()) {
      if (NEED_UPDATE.equals(selnet)) {
        if (message.length() != 0) {
          message.append("\n");
        }
        message.append("The network needs update.");
      } else if (UPDATING_LIST.equals(selnet)) {
        if (message.length() != 0) {
          message.append("\n");
        }
        message.append("The network is updating.");
      } else if (selnet.indexOf('-') != -1) {
        if (message.length() != 0) {
          message.append("\n");
        }
        message.append("The network is invalid.");
      }
    }

    if (message.length() != 0) {
      showErrorMessage("Update Error", message.toString());
      return false;
    } else {
      return true;
    }
  }

  private void showErrorMessage(String title, String error) {
    LOGGER.warn("{}: {}", title, error);
    int columns = error.length();
    Object message;
    if (columns > MAX_ERROR_COLUMNS) {
      columns = MAX_ERROR_COLUMNS;
      message = SwarmUtil.createTextComponent(error, columns);
    } else {
      message = error;
    }
    JOptionPane.showMessageDialog(panel, message, title, JOptionPane.ERROR_MESSAGE);
  }

  private void updateWsDataselectUrlField() {
    String s = getText(wsDataselectUrlField, false);
    if (s.startsWith("http") && s.endsWith(FDSN_DATASELECT_QUERY_SUFFIX)) {
      if (portableFdsnwsDataselect.isSelected()) {
        s = s.replace(DATASELECT_QUERY_SUFFIX, DATASELECT_SUMMARY_SUFFIX);
      } else {
        s = s.replace("/dataselect", "/station");
      }
      if (!wsStationUrlField.getText().equals(s)) {
        wsStationUrlField.setText(s);
      }
    }
  }

  /**
   * Create fields.
   */
  protected void createFields() {
    network = new JComboBox<String>();
    network.setEditable(true);
    network.addItem(NEED_UPDATE);

    station = new JTextField();
    location = new JTextField();
    channel = new JTextField();
    gulperSize = new JTextField();
    gulperDelay = new JTextField();
    portableFdsnwsDataselect = new JCheckBox((String) null, false);
    portableFdsnwsDataselect.setToolTipText(portableFdsnwsDataselectTooltipText);
    wsDataselectUrlField = new JTextField();
    wsStationUrlField = new JTextField();
    // listen for changes to portable fdsnws-dataselect
    portableFdsnwsDataselect.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean b = !portableFdsnwsDataselect.isSelected();
        wsStationUrlField.setEditable(b);
        wsStationUrlField.setEnabled(b);
        updateWsDataselectUrlField();
      }
    });
    // Listen for changes in the text
    wsDataselectUrlField.getDocument().addDocumentListener(new DocumentListener() {
      public void changedUpdate(DocumentEvent e) {
        updateWsDataselectUrlField();
      }

      public void insertUpdate(DocumentEvent e) {
        updateWsDataselectUrlField();
      }

      public void removeUpdate(DocumentEvent e) {
        updateWsDataselectUrlField();
      }
    });
    // Listen for changes in the text
    wsStationUrlField.getDocument().addDocumentListener(new DocumentListener() {
      public void changedUpdate(DocumentEvent e) {
        update();
      }

      public void insertUpdate(DocumentEvent e) {
        update();
      }

      public void removeUpdate(DocumentEvent e) {
        update();
      }

      private void update() {
        final String s = getText(wsStationUrlField, false);
        if (!currentStationUrl.equals(s)) {
          boolean b = s.endsWith(DATASELECT_SUMMARY_SUFFIX);
          currentStationUrl = s;
          if (b != portableFdsnwsDataselect.isSelected()) {
            portableFdsnwsDataselect.setSelected(b);
          }
          showNeedUpdate();
        }
      }
    });
    String net = null;
    String sta = "";
    String loc = "";
    String chan = "";
    String gs = "60";
    String gd = "1.0";
    String wsDataSelectUrl = getDefaultText(wsDataselectUrlField);
    String wsStationUrl = getDefaultText(wsStationUrlField);
    int index;
    if (source != null && (index = source.indexOf(codeText)) != -1) {
      String[] ss = source.substring(index + codeText.length()).split(WebServicesSource.PARAM_SPLIT_TEXT);
      int ssIndex = 0;
      net = ss[ssIndex++];
      sta = ss[ssIndex++];
      loc = ss[ssIndex++];
      chan = ss[ssIndex++];
      gs = String.format("%.0f", Integer.parseInt(ss[ssIndex++]) / 60.0);
      gd = String.format("%.1f", Integer.parseInt(ss[ssIndex++]) / 1000.0);
      wsDataSelectUrl = ss[ssIndex++];
      wsStationUrl = ss[ssIndex++];
    }
    setText(wsDataselectUrlField, wsDataSelectUrl);
    setText(wsStationUrlField, wsStationUrl);
    selectNetwork(net);
    setText(station, sta);
    setText(location, loc);
    setText(channel, chan);
    setText(gulperSize, gs);
    setText(gulperDelay, gd);
    updateNetworkList = new JButton("Update");
    updateNetworkList.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        getWebServicesNetworks();
      }
    });
  }

  /**
   * Create panels.
   */
  protected void createPanel() {
    createFields();
    int columnSpan = 11;
    FormLayout layout = new FormLayout(
        "right:max(10dlu;pref), 3dlu, right:max(20dlu;pref), 3dlu, 80dlu, 0dlu, 5dlu, 3dlu, right:max(20dlu;pref), 3dlu, 40dlu, 0dlu, 40dlu",
        "");

    DefaultFormBuilder builder = new DefaultFormBuilder(layout).border(Borders.DIALOG);
    builder.append(new JLabel("Use this data source to connect to " + WebServicesSource.DESCRIPTION + "."), columnSpan);
    builder.nextLine();
    builder.append("Is portable fdsnws-dataselect:").setToolTipText(portableFdsnwsDataselectTooltipText);
    builder.append(portableFdsnwsDataselect, columnSpan);
    builder.nextLine();
    builder.append("Dataselect URL");
    builder.append(wsDataselectUrlField, columnSpan);
    builder.nextLine();
    builder.append("Station URL");
    builder.append(wsStationUrlField, columnSpan);

    builder.nextLine();

    builder.appendSeparator();

    JLabel scnlLabel = new JLabel(
        "<HTML>Enter Station, Channel, Network and Location. An empty field is the same as '*'. Use "
            + WebServiceUtils.EMPTY_LOC_CODE + " for an empty location code."
            + " Wildcards (\"?\" for any single character and \"*\" for zero or more characters)"
            + " and comma-separated lists are accepted. All Networks channels will not be displayed on the map.</HTML>");

    builder.append(scnlLabel, 11);
    builder.nextLine();
    builder.append(updateNetworkList);
    builder.append("Network");
    builder.append(network, 9);

    builder.nextLine();
    builder.append("");
    builder.append("Station");
    builder.append(station, 1);
    builder.append("");
    builder.append("Gulp size");

    builder.append(gulperSize);
    builder.append(" minutes");
    // builder.append("Gulp delay:");
    builder.nextLine();
    builder.append("");
    builder.append("Channel");
    builder.append(channel, 1);
    builder.append("");
    builder.append("Gulp delay");

    builder.append(gulperDelay);
    builder.append(" seconds");
    builder.nextLine();
    builder.append("");
    builder.append("Location");
    builder.append(location, 1);
    // add some space
    builder.nextLine();
    builder.append(" ");
    builder.nextLine();
    builder.append(" ");
    panel = builder.getPanel();
  }

  /**
   * Get the default text for the specified component.
   * 
   * @param component the component.
   * @return the default text.
   */
  private String getDefaultText(Component component) {
    String s;
    if (component == wsDataselectUrlField) {
      s = WebServicesSource.DEFAULT_DATASELECT_URL;
    } else if (component == wsStationUrlField) {
      s = WebServicesSource.DEFAULT_STATION_URL;
    } else {
      s = "";
    }
    return s;
  }

  /**
   * Get the network combination box text.
   * 
   * @param s the network text.
   * @return the network combination box text.
   */
  protected String getNetworkText(String s) {
    switch (s) {
    case NEED_UPDATE:
    case UPDATING_LIST:
      return s;
    default:
      int index = s.indexOf(VALUE_DELIMITER);
      if (index != -1) {
        s = s.substring(0, index);
      }
      return s.trim();
    }
  }

  private String getSelectedNetwork() {
    Object item = network.getSelectedItem();
    if (item == null) { // if no selection
      // if there is a network
      if (network.getItemCount() != 0) {
        // select first network
        network.setSelectedIndex(0);
        item = network.getItemAt(0);
      }
    }
    if (item != null) {
      return getNetworkText(item.toString());
    }
    return null;
  }

  /**
   * Get the current text for the specified component.
   * 
   * @param component the component.
   * @param upper     true to convert to upper case, false otherwise.
   * @return the current text, not null.
   */
  private String getText(Component component, boolean upper) {
    Object value;
    if (component instanceof JComboBox) {
      value = ((JComboBox<?>) component).getSelectedItem();
    } else if (component instanceof JTextComponent) {
      value = ((JTextComponent) component).getText();
    } else {
      value = component.toString();
    }
    String s = "";
    if (value != null) {
      s = value.toString();
      if (component == network) {
        s = getNetworkText(s);
      }
      if (upper) {
        s = s.toUpperCase();
      }
      s = s.trim();
    }
    return s;
  }

  private static class WSSwingWorker extends SwingWorker<List<String>, Void> {
    private final String currentStationUrl;
    private volatile String error = null;

    public WSSwingWorker(String currentStationUrl) {
      this.currentStationUrl = currentStationUrl;
    }

    @Override
    protected List<String> doInBackground() throws Exception {
      final WebServiceStationClient wsc = WebServiceStationClientFactory.createClient(currentStationUrl);
      wsc.setLevel(OutputLevel.NETWORK);
      error = wsc.fetch();
      return wsc.getNetworkList();
    }

    /**
     * Get the error.
     * @return the error or an empty string if none, not null.
     */
    public String getError() {
      String s = error;
      if (s == null) {
        s = "";
      }
      return error;
    }
  }

  /**
   * Initialize the web service networks in the network selection.
   */
  protected void getWebServicesNetworks() {
    final String selnet = getSelectedNetwork();
    network.removeAllItems();
    network.addItem(UPDATING_LIST);
    final WSSwingWorker worker = new WSSwingWorker(currentStationUrl);
    final CancelDialog cancelDialog = CancelDialog.createCancelDialog(worker, panel, "Updating Network List");
    cancelDialog.start();
    final List<String> nets;
    try {
      nets = worker.get();
    } catch (Exception ex) {
      // cancelled
      network.removeAllItems();
      showNeedUpdate();
      return;
    }
    network.removeAllItems();
    if (nets == null || nets.isEmpty()) {
      showNeedUpdate();
      String error = worker.getError();
      if (error.isEmpty()) {
        error = "No network found. Please ensure you have the correct fdsnws-station URL.";
      }
      showErrorMessage("Station query", error);
    } else {
      for (String net : nets) {
        network.addItem(net);
        if (isNetMatch(selnet, net)) {
          network.setSelectedItem(net);
        }
      }
    }
  }

  /**
   * Determines if the networks match.
   * 
   * @param net1 the first network.
   * @param net2 the second network.
   * @return true if match, false otherwise.
   */
  private boolean isNetMatch(String net1, String net2) {
    if (net1 == net2) {
      return true;
    }
    if (net1 == null || net2 == null) {
      return false;
    }
    final int len = Math.max(getNetworkLength(net1), getNetworkLength(net2));
    if (net1.regionMatches(true, 0, net2, 0, len)) {
      return true;
    }
    return false;
  }

  private boolean isShowNeedUpdate() {
    return NEED_UPDATE.equals(getSelectedNetwork());
  }

  /**
   * Reset source.
   */
  public void resetSource(String src) {
    if (src != null && (source == null || src.compareTo(source) != 0)) {
      source = src;

      String net = "IU";
      String sta = "";
      String loc = "";
      String chan = "";
      String gs = "60";
      String gd = "1.0";
      String wsDataSelectUrl = "";
      String wsStationUrl = "";
      int index;
      if (source != null && (index = source.indexOf(codeText)) != -1) {
        String[] ss = source.substring(index + codeText.length()).split(WebServicesSource.PARAM_SPLIT_TEXT);
        int ssIndex = 0;
        net = ss[ssIndex++];
        sta = ss[ssIndex++];
        loc = ss[ssIndex++];
        chan = ss[ssIndex++];
        gs = String.format("%.0f", Integer.parseInt(ss[ssIndex++]) / 60.0);
        gd = String.format("%.1f", Integer.parseInt(ss[ssIndex++]) / 1000.0);
        wsDataSelectUrl = ss[ssIndex++];
        wsStationUrl = ss[ssIndex++];
      }
      setText(wsDataselectUrlField, wsDataSelectUrl);
      setText(wsStationUrlField, wsStationUrl);
      selectNetwork(net);
      setText(station, sta);
      setText(location, loc);
      setText(channel, chan);
      setText(gulperSize, gs);
      setText(gulperDelay, gd);
    }
  }

  /**
   * Select the network.
   * 
   * @param net the network or null if none.
   */
  protected void selectNetwork(String net) {
    String item = getSelectedNetwork();
    if (net != null) {
      // if match
      if (isNetMatch(item, net)) {
        return; // no change
      }
      network.removeItem(NEED_UPDATE);
      // boolean found = false;
      int netidx = -1;
      for (int i = 0; i < network.getItemCount(); i++) {
        item = network.getItemAt(i);
        if (isNetMatch(item, net)) {
          netidx = i;
          break;
        }
      }
      // if network was not found
      if (netidx == -1) {
        netidx = 0;
        network.insertItemAt(net, netidx);
      }
      network.setSelectedIndex(netidx);
    } else if (network.getItemCount() == 0) {
      network.addItem(NEED_UPDATE);
    }
  }

  protected void showNeedUpdate() {
    if (isShowNeedUpdate()) {
      return;
    }
    if (network.getItemCount() != 0) {
      network.removeAllItems();
    }
    network.addItem(NEED_UPDATE);
  }

  /**
   * Process the OK.
   */
  public String wasOk() {
    final int gs = (int) (Double.parseDouble(gulperSize.getText()) * 60);
    final int gd = (int) (Double.parseDouble(gulperDelay.getText()) * 1000);
    final String fdsnDataselectUrl = getText(wsDataselectUrlField, false);
    final String fdsnStationUrl = getText(wsStationUrlField, false);
    final String result = String.format(getCode() + ":" + WebServicesSource.PARAM_FMT_TEXT, getText(network, true),
        getText(station, true), getText(location, true), getText(channel, true), gs, gd, fdsnDataselectUrl,
        fdsnStationUrl);
    SwarmConfig.getInstance().fdsnDataselectUrl = fdsnDataselectUrl;
    SwarmConfig.getInstance().fdsnStationUrl = fdsnStationUrl;
    return result;
  }
}
