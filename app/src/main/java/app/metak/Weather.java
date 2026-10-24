package app.metak;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONObject;

/** Current weather for a named city via Open-Meteo (geocoding + forecast), no API key. */
final class Weather {
    String icon, city;
    long temp;

    // City, not IP: ISPs often route through another city, so IP location shows the wrong weather.
    static Weather load(String city, boolean fahrenheit) {
        try {
            JSONObject where = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                    + URLEncoder.encode(city, "UTF-8")).getJSONArray("results").getJSONObject(0);
            JSONObject now = get("https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + where.getDouble("latitude")
                    + "&longitude=" + where.getDouble("longitude")
                    + "&current=temperature_2m,weather_code,is_day"
                    + (fahrenheit ? "&temperature_unit=fahrenheit" : "")).getJSONObject("current");
            Weather w = new Weather();
            w.city = where.optString("name", city);
            w.temp = Math.round(now.getDouble("temperature_2m"));
            w.icon = icon(now.getInt("weather_code"), now.optInt("is_day", 1) == 1);
            return w;
        } catch (Exception e) {
            return null; // offline or a service is down: the widget just stays hidden
        }
    }

    /** WMO weather code to an emoji. */
    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code <= 2) return day ? "🌤️" : "☁️";
        if (code == 3) return "☁️";
        if (code <= 48) return "🌫️";
        if (code <= 67 || (code >= 80 && code <= 82)) return "🌧️";
        if (code <= 77 || code == 85 || code == 86) return "🌨️";
        return "⛈️";
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(10000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            c.disconnect();
        }
    }
}
