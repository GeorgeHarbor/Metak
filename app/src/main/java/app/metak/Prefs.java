package app.metak;

import android.content.Context;
import android.content.SharedPreferences;

/** The one SharedPreferences file the launcher and its settings screen share. */
final class Prefs {
    // Matches Activity.getPreferences()'s file name, so dock/city saved before this change still load.
    static final String FILE = "HomeActivity";

    static final String CITY = "city";
    static final String UNITS = "units";            // "auto" | "c" | "f"
    static final String SHOW_WEATHER = "show_weather";
    static final String CLOCK = "clock_fmt";        // "auto" | "12" | "24"
    static final String DIM_MIN = "dim_min";        // minutes idle before dimming; 0 = never
    static final String EFFECTS = "fx";             // CRT power on/off + wake animation
    static final String WN_BLUR = "wn_blur";        // blur the backdrop while browsing Watch Next

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean bool(Context c, String key, boolean def) {
        return get(c).getBoolean(key, def);
    }

    private Prefs() {}
}
