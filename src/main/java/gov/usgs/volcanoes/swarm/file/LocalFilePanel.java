package gov.usgs.volcanoes.swarm.file;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.FormLayout;
import gov.usgs.volcanoes.swarm.CancelDialog;
import gov.usgs.volcanoes.swarm.SwarmConst;
import gov.usgs.volcanoes.swarm.SwarmModalDialog;
import gov.usgs.volcanoes.swarm.SwarmUtil;
import gov.usgs.volcanoes.swarm.chooser.DataSourcePanel;
import gov.usgs.volcanoes.swarm.data.DataSourceType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

public class LocalFilePanel extends DataSourcePanel implements DataStructureConst, SwarmConst {
  private static final int DATE_TEXT_COLUMNS =
      Math.max(NEED_UPDATE.length(), UPDATING_LIST.length());
  private static final String[] DATE_TEXT_SUFFIXES = { ".000Z", "Z" };
  private static final String DATE_TOOLTIP_TEXT =
      "'YYYYMMDD', " + ISO_8601_TOOLTIP + " or empty for any";

  /**
   * Get the text for the specified date.
   *
   * @param date the date.
   * @return the text.
   */
  public static String getDateText(Date date) {
    return getDateText(date, EMPTY_STRING);
  }

  /**
   * Get the text for the specified date.
   *
   * @param date the date.
   * @param defaultText the default text.
   * @return the text.
   */
  public static String getDateText(final Date date, String defaultText) {
    if (date == null || date.getTime() == 0) {
      return defaultText;
    }
    String s = SwarmUtil.getDateText(date);
    for (String suffix : DATE_TEXT_SUFFIXES) {
      if (s.endsWith(suffix)) {
        return s.substring(0, s.length() - suffix.length());
      }
    }
    return s;
  }

  private JComboBox<String> dataStructure;
  private volatile DataStructureFormat dataStructureFormat;
  private final SwarmModalDialog dialog;
  private JTextField dirText;
  private JTextField endDate;
  private volatile String lastDataStructure;
  private JTextComponent maxDateText;
  private JTextComponent minDateText;
  private boolean resetInProgress;
  private JTextField startDate;
  private JButton update;

  /**
   * Create the local file panel.
   * 
   * @param dialog the dialog.
   */
  public LocalFilePanel(SwarmModalDialog dialog) {
    super(DataSourceType.getShortName(LocalFileSource.class), DATA_STRUCTURE_TITLE);
    this.dialog = dialog;
  }

  @Override
  public boolean allowOk(boolean edit) {
    boolean b = dataStructureFormat != null;
    if (!b && isNeedUpdate()) {
      JOptionPane.showMessageDialog(dialog, NEED_UPDATE, "Error", JOptionPane.ERROR_MESSAGE);
    }
    return b;
  }

  private void checkDataStructure() {
    if (dataStructure.getSelectedIndex() == -1) {
      return;
    }
    final String select = LocalFileUtil.getString(dataStructure.getSelectedItem(), EMPTY_STRING);
    String s = dirText.getText();
    if (s.length() < 3) {
      return;
    }
    int index = s.lastIndexOf(File.separatorChar);
    if (index != -1) {
      s = s.substring(index + 1);
    }
    if (s.equalsIgnoreCase("BUD")) {
      if (select.isEmpty() || select.equals(SDS_TEXT)) {
        dataStructure.setSelectedItem(BUD_TEXT);
      }
    } else if (s.equalsIgnoreCase("SDS")) {
      if (select.isEmpty() || select.equals(BUD_TEXT)) {
        dataStructure.setSelectedItem(SDS_TEXT);
      }
    }
  }

  private JTextComponent creatDateText(String text) {
    JTextField textComponent = new JTextField(text, DATE_TEXT_COLUMNS);
    textComponent.setEditable(false);
    return textComponent;
  }

  private DefaultComboBoxModel<String> createModel(String... items) {
    final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    if (items != null) {
      for (String item : items) {
        model.addElement(item);
      }
    }
    return model;
  }

  @Override
  protected void createPanel() {
    final DocumentListener dl = new DocumentListener() {
      @Override
      public void changedUpdate(DocumentEvent e) {
        documentEvent(e);
      }

      private void documentEvent(DocumentEvent e) {
        if (resetInProgress) {
          return;
        }
        setDataStructure(null, true);
      }

      @Override
      public void insertUpdate(DocumentEvent e) {
        documentEvent(e);
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        documentEvent(e);
      }
    };
    update = new JButton("Update");
    dirText = new JTextField();
    minDateText = creatDateText(NEED_UPDATE);
    minDateText.setToolTipText("The minimum date for the data");
    maxDateText = creatDateText(NEED_UPDATE);
    maxDateText.setToolTipText("The maximum date for the data");
    startDate = new JTextField();
    startDate.setEditable(true);
    startDate.setToolTipText(DATE_TOOLTIP_TEXT);
    startDate.getDocument().addDocumentListener(dl);
    endDate = new JTextField();
    endDate.setEditable(true);
    endDate.setToolTipText(DATE_TOOLTIP_TEXT);
    endDate.getDocument().addDocumentListener(dl);
    dataStructure = new JComboBox<>(createModel(SDS_TEXT, BUD_TEXT));
    dataStructure.setEditable(true);
    final JFileChooser dirChooser = LocalFileUtil.createDirectoryChooser();
    final JButton browseButton = LocalFileUtil.createBrowseButton();
    browseButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int result = dirChooser.showOpenDialog(getPanel());
        if (result == JFileChooser.APPROVE_OPTION) {
          File selectedDirectory = dirChooser.getSelectedFile();
          if (selectedDirectory != null) {
            dirText.setText(selectedDirectory.toString());
          }
        }
      }
    });
    update.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        update();
      }
    });
    dirText.getDocument().addDocumentListener(dl);
    dataStructure.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (resetInProgress) {
          return;
        }
        setDataStructure(null, false);
      }
    });
    resetSource(source);
    FormLayout layout =
        new FormLayout("left:pref, 3dlu, pref, 3dlu, pref, 3dlu, pref:grow, 0dlu, pref", "");
    DefaultFormBuilder builder = new DefaultFormBuilder(layout).border(Borders.DIALOG);
    builder.append(new JLabel("Use this data source to read local files."), 7);
    builder.nextLine();
    builder.appendSeparator();
    builder.append("Base directory:");
    builder.append(dirText, 5);
    builder.append(browseButton);
    builder.nextLine();
    builder.append("Data Structure:");
    builder.append(dataStructure, 7);
    builder.nextLine();
    builder.appendSeparator();
    builder.append(update);
    builder.nextLine();
    builder.append("Minimum Date:");
    builder.append(minDateText);
    builder.append("Maximum Date:");
    builder.append(maxDateText);
    builder.nextLine();
    builder.append("Start Date:");
    builder.append(startDate);
    builder.append("End Date:");
    builder.append(endDate);
    panel = builder.getPanel();
  }

  private String getDataStructure() {
    String s = lastDataStructure;
    if (s == null) {
      Object obj = dataStructure.getSelectedItem();
      if (obj != null) {
        s = obj.toString();
      }
      s = DataStructureFormat.parseDataStructureText(s);
      lastDataStructure = s;
    }
    return s;
  }

  private boolean isNeedUpdate() {
    return NEED_UPDATE.equals(minDateText.getText());
  }

  private long parseDate(String s, boolean end) {
    long time = 0L;
    if (s != null && !s.isEmpty()) {
      try {
        time = LocalFileUtil.parseInstant(s, end).toEpochMilli();
      } catch (Exception ex) {
        time = -1L;
      }
    }
    return time;
  }

  @Override
  public void resetSource(String s) {
    resetInProgress = true;
    setDataStructure(null, false);
    dirText.setText(EMPTY_STRING);
    dataStructure.setSelectedItem(DEFAULT_DATA_STRUCTURE_TEXT);
    minDateText.setText(NEED_UPDATE);
    maxDateText.setText(NEED_UPDATE);
    startDate.setText(EMPTY_STRING);
    endDate.setText(EMPTY_STRING);
    if (s != null) {
      try {
        int index = s.indexOf(LocalFileSource.CONFIG_STARTING_TEXT);
        if (index != -1) {
          index += LocalFileSource.CONFIG_STARTING_TEXT.length();
          s = s.substring(index);
          DataStructureFormat dsf = DataStructureFormat.parse(s);
          s = dsf.getBaseDir().toString();
          dirText.setText(s);
          s = dsf.getDataStructure();
          if (s.isEmpty() || SDS.equals(s)) {
            s = DEFAULT_DATA_STRUCTURE_TEXT;
          } else if (BUD.equals(s)) {
            s = BUD_TEXT;
          }
          dataStructure.setSelectedItem(s);
          s = getDateText(dsf.startDate);
          startDate.setText(s);
          s = getDateText(dsf.endDate);
          endDate.setText(s);
          // do not need update
          minDateText.setText(EMPTY_STRING);
          maxDateText.setText(EMPTY_STRING);
          setDataStructure(dsf, false);
        }
      } catch (Exception ex) {
      }
    }
    resetInProgress = false;
  }

  private void setDataStructure(DataStructureFormat dsf, boolean check) {
    if (dsf == null) {
      setNeedUpdate();
    }
    if (dsf != dataStructureFormat) {
      dataStructureFormat = dsf;
      if (dsf == null) {
        // if not already NEED_UPDATE
        if (!isNeedUpdate()) {
          setNeedUpdate();
          if (check) {
            checkDataStructure();
          }
        }
      }
    }
  }

  private void setNeedUpdate() {
    minDateText.setText(NEED_UPDATE);
    maxDateText.setText(NEED_UPDATE);
  }

  private void setUpdatingList() {
    minDateText.setText(UPDATING_LIST);
    maxDateText.setText(UPDATING_LIST);
  }

  /**
   * Show the error message in a dialog.
   *
   * @param message the error message.
   */
  public void showErrorMessage(String message) {
    JOptionPane.showMessageDialog(dialog, message, "Error", JOptionPane.ERROR_MESSAGE);
  }

  /**
   * Update the data structure format.
   * 
   * @param dsf the data structure format or null to create a new one.
   */
  private void update() {
    final Path baseDir;
    final long stime;
    final long etime;
    final SwingWorker<Void, Void> worker;
    update.setEnabled(false);
    setUpdatingList();
    this.lastDataStructure = null;
    String s = dirText.getText();
    if (s == null || s.isEmpty()) {
      showErrorMessage("Select the directory");
      worker = null;
    } else if (!Files.isDirectory(baseDir = Paths.get(s))) {
      showErrorMessage("Selection is not a directory: " + s);
      worker = null;
    } else if ((stime = parseDate(s = startDate.getText(), false)) < 0L) {
      showErrorMessage("Invalid start date: " + s);
      worker = null;
    } else if ((etime = parseDate(s = endDate.getText(), true)) < 0L) {
      showErrorMessage("Invalid end date: " + s);
      worker = null;
    } else {
      if (dataStructureFormat == null) {
        checkDataStructure();
      }
      worker = new SwingWorker<Void, Void>() {
        private DataStructureFormat dsf = dataStructureFormat;
        private String error = EMPTY_STRING;
        private final Date maxDate = new Date(0);
        private final Date minDate = new Date(0);

        @Override
        protected Void doInBackground() {
          try {
            if (dsf == null) {
              dsf = new DataStructureFormat(baseDir, getDataStructure());
            }
            dsf.startDate.setTime(stime);
            dsf.endDate.setTime(etime);
            // find the minimum and maximum dates
            dsf.findChannels(null, null, minDate, maxDate);
          } catch (Exception ex) {
            error = ex.toString();
          }
          return null;
        }

        @Override
        protected void done() {
          update.setEnabled(true);
          if (isCancelled()) {
            setNeedUpdate();
          } else if (!error.isEmpty()) {
            showErrorMessage(error);
            setNeedUpdate();
          } else {
            // set the minimum and maximum date text
            minDateText.setText(getDateText(minDate));
            maxDateText.setText(getDateText(maxDate));
            setDataStructure(dsf, true);
          }
        }
      };
    }
    if (worker == null) {
      setNeedUpdate();
      update.setEnabled(true);
    } else {
      final CancelDialog cancelDialog =
          CancelDialog.createCancelDialog(worker, getPanel(), "Updating Network List");
      cancelDialog.start();
    }
  }

  @Override
  public String wasOk() {
    StringBuilder sb = new StringBuilder();
    final DataStructureFormat dsf = dataStructureFormat;
    if (dsf != null) {
      sb.append(getCode());
      sb.append(CONFIG_DELIM_CHAR);
      dsf.appendConfigString(sb);
    }
    String s = sb.toString();
    return s;
  }
}
