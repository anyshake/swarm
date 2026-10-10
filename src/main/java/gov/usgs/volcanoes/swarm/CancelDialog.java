package gov.usgs.volcanoes.swarm;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JDialog;
import javax.swing.JOptionPane;

import javax.swing.SwingWorker;

public class CancelDialog implements PropertyChangeListener {
  private static final String CANCEL_TEXT = "Cancel";
  private static final String[] OPTIONS = { CANCEL_TEXT };

  /**
   * Create the cancel dialog.
   * 
   * @param worker          the worker.
   * @param parentComponent the parent component.
   * @param title           the title.
   * @return the cancel dialog.
   */
  public static CancelDialog createCancelDialog(SwingWorker<?, ?> worker, Component parentComponent, String title) {
    return new CancelDialog(worker, parentComponent, title);

  }

  private final JDialog dialog;
  private final JOptionPane pane;
  private final SwingWorker<?, ?> worker;

  /**
   * Create the cancel dialog.
   * 
   * @param worker          the worker.
   * @param parentComponent the parent component.
   * @param title           the title.
   */
  private CancelDialog(SwingWorker<?, ?> worker, Component parentComponent, String title) {
    this.worker = worker;
    worker.addPropertyChangeListener(this);
    pane = new JOptionPane(title, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, OPTIONS, CANCEL_TEXT);
    dialog = pane.createDialog(parentComponent, title);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    // The SwingWorker will be done if the task completes or if it
    // is canceled. Either way, we want the dialog to go away.
    if (worker.isDone()) {
      worker.removePropertyChangeListener(this);
      dialog.setVisible(false);
    }
  }

  /**
   * Start the worker.
   */
  public void start() {
    // Start the worker now (even before the dialog is visible).
    worker.execute();
    dialog.setVisible(true);
    Object value = pane.getValue();
    if (value == CANCEL_TEXT) {
      worker.cancel(true);
    }
  }
}
