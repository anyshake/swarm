package gov.usgs.volcanoes.swarm;

import java.util.concurrent.CountDownLatch;
import javax.swing.SwingUtilities;

/**
 * This is the 3rd version of SwingWorker (also known as SwingWorker 3), an abstract class that you
 * subclass to perform GUI-related work in a dedicated thread. For instructions on and examples of
 * using this class, see:
 * 
 * <p>
 * http://java.sun.com/docs/books/tutorial/uiswing/misc/threads.html
 * 
 * <p>
 * Note that the API changed slightly in the 3rd version: You must now invoke start() on the
 * SwingWorker after creating it. The work runs on a reused thread from {@link Workers}.
 */
public abstract class SwingWorker {
  private Object value; // see getValue(), setValue()
  private final CountDownLatch constructed = new CountDownLatch(1);

  /**
   * Get the value produced by the worker thread, or null if it hasn't been constructed yet.
   */
  protected synchronized Object getValue() {
    return value;
  }

  /**
   * Set the value produced by worker thread.
   */
  private synchronized void setValue(Object x) {
    value = x;
  }

  /**
   * Compute the value to be returned by the <code>get</code> method.
   */
  public abstract Object construct();

  /**
   * Called on the event dispatching thread (not on the worker thread) after the
   * <code>construct</code> method has returned.
   */
  public void finished() {}

  /**
   * Return the value created by the <code>construct</code> method, waiting for it if needed.
   * Returns null if the current thread was interrupted before a value was produced.
   * 
   * @return the value created by the <code>construct</code> method
   */
  public Object get() {
    try {
      constructed.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // propagate
      return null;
    }
    return getValue();
  }

  /**
   * Start the worker thread.
   */
  public void start() {
    Workers.execute(new Runnable() {
      public void run() {
        try {
          setValue(construct());
        } finally {
          constructed.countDown();
        }
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            finished();
          }
        });
      }
    });
  }
}
