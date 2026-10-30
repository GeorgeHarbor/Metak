package app.metak;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract.WatchNextPrograms;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Continue-watching rows that apps publish to the system Watch Next table. */
final class WatchNext {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";

    static final class Item {
        String pkg, title, subtitle, image, intentUri;
        long id, position, duration, lastEngaged;

        /** Identifies this row as last watched; watching it again changes the key, so a hidden row comes back. */
        String key() {
            return pkg + "/" + id + "@" + lastEngaged;
        }

        String source(Context c) {
            if (pkg.startsWith("com.nuvio")) return "Nuvio";
            return wanted(pkg) ? "YouTube" : label(c, pkg);
        }

        /** Playback progress in thousandths, or -1 when the app did not report a duration. */
        int progress() {
            return duration > 0 ? (int) Math.min(1000, 1000 * position / duration) : -1;
        }
    }

    /** Whether the user shows this app's rows; Nuvio and SmartTube are on until switched off, other apps off until switched on. */
    static boolean enabled(SharedPreferences prefs, String pkg) {
        return wanted(pkg) != prefs.getStringSet(Prefs.WN_FLIPPED, Collections.emptySet()).contains(pkg);
    }

    /** Every app with rows in the table, most recently watched first. */
    static Set<String> sources(ContentResolver resolver) {
        Set<String> pkgs = new LinkedHashSet<>();
        for (Item item : load(resolver)) pkgs.add(item.pkg);
        return pkgs;
    }

    static String label(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    static boolean wanted(String pkg) {
        if (pkg == null) return false;
        return pkg.startsWith("com.nuvio")               // com.nuvio.tv, com.nuvio.app, com.nuviodebug.com
                || pkg.startsWith("app.smarttube")
                || pkg.startsWith("org.smarttube")
                || pkg.startsWith("com.liskovsoft.smarttubetv")
                || pkg.equals("com.teamsmart.videomanager.tv")
                || pkg.equals("com.georgeharbor.youtube");
    }

    /** All browsable rows from every app; callers filter by {@link #enabled}. */
    static List<Item> load(ContentResolver resolver) {
        List<Item> items = new ArrayList<>();
        // TvProvider rejects any selection from non-system callers, so filter in code.
        try (Cursor c = resolver.query(WatchNextPrograms.CONTENT_URI, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                Item item = new Item();
                item.pkg = string(c, WatchNextPrograms.COLUMN_PACKAGE_NAME);
                if (item.pkg == null || number(c, WatchNextPrograms.COLUMN_BROWSABLE, 1) == 0) continue;
                item.id = number(c, WatchNextPrograms._ID, 0);
                item.title = string(c, WatchNextPrograms.COLUMN_TITLE);
                item.subtitle = string(c, WatchNextPrograms.COLUMN_EPISODE_TITLE);
                item.image = string(c, WatchNextPrograms.COLUMN_THUMBNAIL_URI);
                if (item.image == null) item.image = string(c, WatchNextPrograms.COLUMN_POSTER_ART_URI);
                item.intentUri = string(c, WatchNextPrograms.COLUMN_INTENT_URI);
                item.position = number(c, WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, 0);
                item.duration = number(c, WatchNextPrograms.COLUMN_DURATION_MILLIS, 0);
                item.lastEngaged = number(c, WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, 0);
                items.add(item);
            }
        } catch (RuntimeException e) {
            // No TV listings permission, or no TvProvider on this device: show an empty shelf.
        }
        sort(items);
        return items;
    }

    static void sort(List<Item> items) {
        items.sort((a, b) -> Long.compare(b.lastEngaged, a.lastEngaged));
    }

    private static String string(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? null : c.getString(i);
    }

    private static long number(Cursor c, String column, long fallback) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? fallback : c.getLong(i);
    }
}
