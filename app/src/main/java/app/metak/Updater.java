package app.metak;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** Checks the GitHub "latest" release for a newer build and installs it in place. */
final class Updater {
    static final String INSTALL_STATUS = "app.metak.INSTALL_STATUS";
    private static final String MANIFEST =
            "https://github.com/GeorgeHarbor/Metak/releases/download/latest/metak.json";

    static final class Info {
        final int versionCode;
        final String versionName, apkUrl;
        Info(int code, String name, String url) {
            versionCode = code;
            versionName = name;
            apkUrl = url;
        }
    }

    /** The newer release, or null when up to date or offline. Call off the main thread. */
    static Info check(Context ctx) {
        try {
            JSONObject o = new JSONObject(get(MANIFEST));
            int remote = o.getInt("versionCode");
            int current = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
            if (remote <= current) return null;
            return new Info(remote, o.optString("versionName"), o.getString("apk"));
        } catch (Exception e) {
            return null;
        }
    }

    /** Stream the APK straight into a PackageInstaller session. Call off the main thread. */
    static void install(Context ctx, String apkUrl) throws Exception {
        PackageInstaller installer = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            HttpURLConnection c = open(apkUrl);
            try (InputStream in = c.getInputStream();
                 OutputStream out = session.openWrite("metak", 0, c.getContentLength())) {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                session.fsync(out);
            }
            // The system shows the confirm/allow-sources prompt; HomeActivity's receiver forwards it.
            Intent intent = new Intent(INSTALL_STATUS).setPackage(ctx.getPackageName());
            PendingIntent pi = PendingIntent.getBroadcast(ctx, id, intent,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pi.getIntentSender());
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }

    private Updater() {}
}
