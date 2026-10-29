package com.cleantv.home;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** One tiny blocking HTTP GET. Call from a background thread only. */
final class Http {
    private Http() {}

    static String get(String url) throws IOException {
        HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
        try {
            cn.setConnectTimeout(10_000);
            cn.setReadTimeout(20_000);
            // An honest user agent on purpose: do not pretend to be a desktop browser.
            cn.setRequestProperty("User-Agent", "TVHome/1.0 (personal Android TV launcher)");
            cn.setRequestProperty("Accept-Language", "en");
            int code = cn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = cn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 8_000_000) throw new IOException("response too large");
                }
                return out.toString("UTF-8");
            }
        } finally {
            cn.disconnect();
        }
    }
}
