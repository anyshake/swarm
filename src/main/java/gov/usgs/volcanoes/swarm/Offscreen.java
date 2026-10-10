package gov.usgs.volcanoes.swarm;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/**
 * Paints a component into a reused offscreen image.
 *
 * <p>
 * A second request that arrives while a frame is still painting is folded into that frame, so
 * resize and live updates do not queue a render per event. The image just published is left alone
 * until the next frame, which keeps a repaint from reading pixels that are being drawn.
 */
public final class Offscreen {
  /**
   * Draws one frame. The graphics is disposed by the caller of this method.
   */
  public interface Painter {
    void paint(Graphics2D graphics);
  }

  private final AtomicBoolean rendering = new AtomicBoolean();
  private volatile boolean stale;
  private volatile BufferedImage displayed;
  private BufferedImage spare;

  /**
   * Packed ARGB matches the old 4-byte image's alpha and is cheaper to copy to the screen.
   *
   * @param width image width
   * @param height image height
   * @return a blank image
   */
  public static BufferedImage image(int width, int height) {
    return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
  }

  public BufferedImage displayed() {
    return displayed;
  }

  /**
   * Paint the latest frame. On the event thread the component is repainted when the frame is
   * ready. Off the event thread the caller repaints, matching the previous synchronous path.
   *
   * @param component component being painted
   * @param painter frame contents
   * @param repaintWhenDone repaint the component after an event-thread request
   */
  public void render(final Component component, final Painter painter,
      final boolean repaintWhenDone) {
    stale = true;
    if (!rendering.compareAndSet(false, true)) {
      return;
    }
    final Runnable task = new Runnable() {
      public void run() {
        boolean holding = true;
        try {
          boolean reuse = true;
          while (holding) {
            while (stale) {
              stale = false;
              draw(component, painter, reuse);
              reuse = false;
            }
            rendering.set(false);
            holding = false;
            if (stale && rendering.compareAndSet(false, true)) {
              holding = true;
              reuse = true;
            }
          }
        } finally {
          if (holding) {
            rendering.set(false);
          }
        }
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      final SwingWorker worker = new SwingWorker() {
        public Object construct() {
          task.run();
          return null;
        }

        public void finished() {
          if (repaintWhenDone) {
            component.repaint();
          }
        }
      };
      worker.start();
    } else {
      task.run();
    }
  }

  private void draw(Component component, Painter painter, boolean reuse) {
    final int width = component.getWidth();
    final int height = component.getHeight();
    if (width <= 0 || height <= 0) {
      return;
    }
    BufferedImage target = reuse ? spare : null;
    if (target == null || target.getWidth() != width || target.getHeight() != height) {
      target = image(width, height);
    }
    final boolean recycled = target == spare;
    final Graphics2D graphics = target.createGraphics();
    try {
      if (recycled) {
        final Composite composite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(0, 0, width, height);
        graphics.setComposite(composite);
      }
      painter.paint(graphics);
    } finally {
      graphics.dispose();
    }
    spare = displayed;
    displayed = target;
  }
}
