package gov.usgs.volcanoes.swarm;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Throbber class.
 * 
 * @author Dan Cervelli
 */
public class Throbber extends JLabel implements Runnable {
  private static final long serialVersionUID = 1L;
  private static int instances = 0;

  private static ImageIcon offIcon;
  private static ImageIcon[] onIcons;

  private int onCount = 0;
  private Thread thread;
  private boolean quit;
  private boolean off;

  private int cycle = 0;

  /**
   * Constructor.
   */
  public Throbber() {
    super();
    if (offIcon == null) {
      offIcon = Icons.throbber_off;
      onIcons = new ImageIcon[8];
      onIcons[0] = Icons.throbber_0;
      onIcons[1] = Icons.throbber_1;
      onIcons[2] = Icons.throbber_2;
      onIcons[3] = Icons.throbber_3;
      onIcons[4] = Icons.throbber_4;
      onIcons[5] = Icons.throbber_5;
      onIcons[6] = Icons.throbber_6;
      onIcons[7] = Icons.throbber_7;
    }
    setIcon(offIcon);
    setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
    thread = new Thread(this, "Throbber-" + instances++);
    thread.start();
  }

  public synchronized int getCount() {
    return onCount;
  }

  public synchronized void increment() {
    onCount++;
    notifyAll();
  }

  /**
   * Decrement count.
   */
  public synchronized void decrement() {
    onCount--;
    if (onCount <= 0) {
      off = true;
      notifyAll();
    }
  }

  public synchronized void close() {
    quit = true;
    notifyAll();
  }

  private void setIcon(final ImageIcon icon) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        Throbber.super.setIcon(icon);
        repaint();
      }
    });
  }

  /**
   * Run.
   * 
   * @see java.lang.Runnable#run()
   */
  public void run() {
    final long frameNs = (1000 / 8) * 1000000L;
    long nextFrameNs = System.nanoTime() + frameNs;
    while (!quit) {
      try {
        synchronized (this) {
          while (!quit && onCount <= 0 && !off) {
            wait();
          }
        }
        if (quit) {
          break;
        }
        long now = System.nanoTime();
        if (nextFrameNs < now) {
          long behind = now - nextFrameNs;
          long steps = behind / frameNs;
          nextFrameNs += steps * frameNs;
          if (nextFrameNs < now) {
            nextFrameNs += frameNs;
          }
        }
        long remainNs = nextFrameNs - now;
        if (remainNs > 0) {
          Thread.sleep(remainNs / 1000000L, (int) (remainNs % 1000000L));
        }
        nextFrameNs += frameNs;
        if (onCount > 0) {
          setIcon(onIcons[cycle++ % 8]);
        }

        if (off) {
          setIcon(offIcon);
          off = false;
          cycle = 0;
        }
      } catch (InterruptedException e) {
        // close() wakes this thread
      } catch (Exception e) {
        //
      }
    }
  }
}
