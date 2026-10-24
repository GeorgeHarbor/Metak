package app.metak;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny poster loader: http(s) and content URIs, downsampled, memory-cached. */
final class Images {
    private static final int MAX_WIDTH = 1280;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 << 20) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    static void load(ImageView view, String uri) {
        view.setTag(uri);
        Bitmap cached = uri == null ? null : CACHE.get(uri);
        view.setImageBitmap(cached);
        if (uri == null || cached != null) return;
        POOL.execute(() -> {
            Bitmap bitmap = decode(view, uri);
            if (bitmap != null) CACHE.put(uri, bitmap);
            MAIN.post(() -> {
                if (uri.equals(view.getTag())) view.setImageBitmap(bitmap);
            });
        });
    }

    private static Bitmap decode(ImageView view, String uri) {
        try (InputStream in = open(view, uri)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 << 10];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            byte[] bytes = out.toByteArray();

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            o.inJustDecodeBounds = false;
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= MAX_WIDTH) o.inSampleSize *= 2;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        } catch (Exception e) {
            return null;
        }
    }

    private static InputStream open(ImageView view, String uri) throws Exception {
        if (!uri.startsWith("http")) {
            return view.getContext().getContentResolver().openInputStream(Uri.parse(uri));
        }
        HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(10000);
        return c.getInputStream();
    }
}
