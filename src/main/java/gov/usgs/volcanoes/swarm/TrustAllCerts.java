package gov.usgs.volcanoes.swarm;

import java.io.InputStream;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrustAllCerts {
  private final static Logger LOGGER = LoggerFactory.getLogger(TrustAllCerts.class);
  private static SSLSocketFactory sslSocketFactory;

  /** System property name to trust all certificates */
  public static String TRUST_ALL_CERTS = "TRUST_ALL_CERTS";

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.printf("Usage: %s URLs\n", TrustAllCerts.class.getName());
      return;
    }
    trustAllCerts(false);
    for (String arg : args) {
      System.out.println(arg);
      try (InputStream in = new URL(arg).openStream()) {
        SwarmUtil.writeText(in, System.out);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }

  /**
   * Trust all certificates.
   * 
   * @param force true to force, otherwise trust only if
   *              <code>TRUST_ALL_CERTS</code> environment property is set to
   *              true.
   */
  public static void trustAllCerts(boolean force) {
    if (!force && !Boolean.valueOf(System.getProperty(TRUST_ALL_CERTS))) {
      return;
    }
    try {
      SSLSocketFactory sf = sslSocketFactory;
      if (sf == null) {
        // Create a trust manager that does not validate certificate chains
        final TrustManager[] tm = new TrustManager[] { new X509TrustManager() {
          private final X509Certificate[] NO_CERTS = new X509Certificate[0];

          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
          }

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
          }

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return NO_CERTS;
          }
        } };
        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, tm, new java.security.SecureRandom());
        sf = sc.getSocketFactory();
        sslSocketFactory = sf;
      }
      if (HttpsURLConnection.getDefaultSSLSocketFactory() != sf) {
        HttpsURLConnection.setDefaultSSLSocketFactory(sf);
        LOGGER.info(TRUST_ALL_CERTS);
      }
    } catch (Exception ex) {
      LOGGER.warn("trustAllCerts({}) failed", force, ex);
    }
  }
}
