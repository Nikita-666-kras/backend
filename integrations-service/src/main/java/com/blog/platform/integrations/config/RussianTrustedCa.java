package com.blog.platform.integrations.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * TLS trust for MAX API ({@code platform-api2.max.ru}) which uses Минцифры Russian Trusted CA,
 * absent from the default JVM cacerts. Certificates: https://www.gosuslugi.ru/crt
 * (files from https://gu-st.ru/content/Other/doc/).
 */
final class RussianTrustedCa {

    private static final Logger log = LoggerFactory.getLogger(RussianTrustedCa.class);
    private static final String[] CLASSPATH_CERTS = {
            "/certs/russian_trusted_root_ca.pem",
            "/certs/russian_trusted_sub_ca.pem"
    };

    private RussianTrustedCa() {
    }

    static SSLContext sslContext() {
        try {
            X509TrustManager jvm = defaultTrustManager();
            X509TrustManager mincifry = mincifryTrustManager();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new FallbackTrustManager(jvm, mincifry)}, null);
            log.info("max tls: JVM cacerts + {} Минцифры certificates", CLASSPATH_CERTS.length);
            return sslContext;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load Минцифры CA for MAX TLS", ex);
        }
    }

    private static X509TrustManager defaultTrustManager() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        return requireX509(tmf);
    }

    private static X509TrustManager mincifryTrustManager() throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);
        int n = 0;
        for (String path : CLASSPATH_CERTS) {
            try (InputStream in = RussianTrustedCa.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("missing classpath resource " + path);
                }
                X509Certificate cert = (X509Certificate) factory.generateCertificate(in);
                trustStore.setCertificateEntry("mincifry-" + n, cert);
                n++;
            }
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return requireX509(tmf);
    }

    private static X509TrustManager requireX509(TrustManagerFactory tmf) {
        return Arrays.stream(tmf.getTrustManagers())
                .filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no X509TrustManager"));
    }

    private record FallbackTrustManager(X509TrustManager primary, X509TrustManager fallback)
            implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            primary.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                primary.checkServerTrusted(chain, authType);
            } catch (CertificateException ignored) {
                fallback.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] a = primary.getAcceptedIssuers();
            X509Certificate[] b = fallback.getAcceptedIssuers();
            X509Certificate[] all = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, all, a.length, b.length);
            return all;
        }
    }
}
