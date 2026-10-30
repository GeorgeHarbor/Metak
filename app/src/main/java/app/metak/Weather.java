package app.metak;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** Current weather for a named city via Open-Meteo (geocoding + forecast), no API key. */
final class Weather {
    String icon, city;
    long temp;

    /** A city search result. */
    static final class Place {
        String name, detail, loc;
    }

    /** loc is "lat,lon" from a picked suggestion or the TV's location; null looks the city up by name. */
    // City, not IP: ISPs often route through another city, so IP location shows the wrong weather.
    static Weather load(String city, String loc, boolean fahrenheit) {
        try {
            if (loc == null) {
                JSONObject where = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                        + URLEncoder.encode(city, "UTF-8")).getJSONArray("results").getJSONObject(0);
                city = where.optString("name", city);
                loc = where.getDouble("latitude") + "," + where.getDouble("longitude");
            }
            String[] ll = loc.split(",");
            JSONObject now = get("https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + ll[0]
                    + "&longitude=" + ll[1]
                    + "&current=temperature_2m,weather_code,is_day"
                    + (fahrenheit ? "&temperature_unit=fahrenheit" : "")).getJSONObject("current");
            Weather w = new Weather();
            w.city = city;
            w.temp = Math.round(now.getDouble("temperature_2m"));
            w.icon = icon(now.getInt("weather_code"), now.optInt("is_day", 1) == 1);
            return w;
        } catch (Exception e) {
            return null; // offline or a service is down: the widget just stays hidden
        }
    }

    /** Where the TV is: the last fix any location provider has (if allowed), else the system time zone's city. */
    static Weather here(Context c, boolean fahrenheit) {
        Location fix = lastFix(c);
        String name = fix == null ? null : placeName(c, fix);
        if (name != null) return load(name, fix.getLatitude() + "," + fix.getLongitude(), fahrenheit);
        String zone = TimeZone.getDefault().getID(); // "Europe/Belgrade" -> "Belgrade"
        return load(zone.substring(zone.lastIndexOf('/') + 1).replace('_', ' '), null, fahrenheit);
    }

    private static Location lastFix(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager lm = c.getSystemService(LocationManager.class);
        Location best = null;
        // TVs rarely run a provider live, but the passive one keeps the last fix Play services made.
        for (String p : lm.getAllProviders()) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (RuntimeException ignored) {
                // provider gone or not allowed
            }
        }
        return best;
    }

    @SuppressWarnings("deprecation") // the async variant is API 33+; this runs off the main thread
    private static String placeName(Context c, Location l) {
        if (!Geocoder.isPresent()) return null;
        try {
            List<Address> a = new Geocoder(c).getFromLocation(l.getLatitude(), l.getLongitude(), 1);
            if (a == null || a.isEmpty()) return null;
            return a.get(0).getLocality() != null ? a.get(0).getLocality() : a.get(0).getSubAdminArea();
        } catch (Exception e) {
            return null;
        }
    }

    /** City suggestions for a partly typed name, biggest first so "Kralj" offers Kraljevo before a hamlet. */
    static List<Place> search(String query) {
        List<Place> places = new ArrayList<>();
        List<Integer> pops = new ArrayList<>();
        try {
            JSONArray results = get("https://geocoding-api.open-meteo.com/v1/search?count=10&language="
                    + Locale.getDefault().getLanguage() + "&name=" + URLEncoder.encode(query, "UTF-8"))
                    .optJSONArray("results");
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                Place p = new Place();
                p.name = r.getString("name");
                String region = r.optString("admin1", "");
                String country = r.optString("country", "");
                p.detail = region.isEmpty() || region.equals(p.name) ? country : region + ", " + country;
                p.loc = r.getDouble("latitude") + "," + r.getDouble("longitude");
                int pop = r.optInt("population", 0), at = 0;
                while (at < pops.size() && pops.get(at) >= pop) at++;
                pops.add(at, pop);
                places.add(at, p);
            }
        } catch (Exception e) {
            // offline: no suggestions
        }
        return places.size() > 6 ? places.subList(0, 6) : places;
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
