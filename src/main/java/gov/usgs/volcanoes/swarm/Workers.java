package gov.usgs.volcanoes.swarm;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared threads for short background jobs. Each job used to start a thread of its own; now an
 * idle thread is reused when there is one and a new one is started otherwise, so jobs still never
 * wait for each other.
 */
public final class Workers {
  private static final AtomicInteger COUNT = new AtomicInteger();

  private static final ExecutorService POOL = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 30L,
      TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
        public Thread newThread(Runnable r) {
          return new Thread(r, "Swarm worker " + COUNT.incrementAndGet());
        }
      });

  private Workers() {}

  /**
   * Run a job on a background thread.
   *
   * @param job the job
   */
  public static void execute(Runnable job) {
    POOL.execute(job);
  }
}
