package app.metak;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class HomeActivity extends Activity {
    private static final String DOCK_KEY = "dock";
    private static final int DOCK_SIZE = 7;
    private static final float DOCK_GLASS_OPACITY = 0.7f;
    // Starts like the stock launcher's dock; missing apps are skipped.
    private static final String DEFAULT_DOCK = "com.nuvio.tv,com.georgeharbor.youtube,com.spotify.tv.android,"
            + "com.limelight,com.netflix.ninja,com.plexapp.android,org.jellyfin.androidtv";

    private ScrollView scroll;
    private GridLayout apps;
    private LinearLayout dock;
    private View dockFrame;
    private ImageView dockBlur;
    private final Matrix blurMatrix = new Matrix();
    private AlertDialog picker;
    private Drawable wallpaper;
    private View hero;
    private ImageView backdrop;
    private TextView heroTitle, heroSubtitle;
    private LinearLayout shelf;

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.home);
        scroll = findViewById(R.id.scroll);
        apps = findViewById(R.id.apps);
        dock = findViewById(R.id.dock);
        dockFrame = findViewById(R.id.dock_frame);
        dockBlur = findViewById(R.id.dock_blur);
        dockFrame.setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= 31) {
            dockBlur.setRenderEffect(RenderEffect.createBlurEffect(dp(24), dp(24), Shader.TileMode.CLAMP));
        }
        dockBlur.getViewTreeObserver().addOnPreDrawListener(() -> syncDockBlur());
        dock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            ViewGroup.LayoutParams lp = dockBlur.getLayoutParams();
            if (lp.width == v.getWidth() && lp.height == v.getHeight()) return;
            lp.width = v.getWidth();
            lp.height = v.getHeight();
            dockBlur.post(dockBlur::requestLayout);
        });
        hero = findViewById(R.id.hero);
        backdrop = findViewById(R.id.backdrop);
        heroTitle = findViewById(R.id.hero_title);
        heroSubtitle = findViewById(R.id.hero_subtitle);
        shelf = findViewById(R.id.shelf);
        showBackdrop(null);
        // The backdrop belongs to the hero, so fade it out as the app grid scrolls up.
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) ->
                backdrop.setAlpha(Math.max(0f, 1f - y / (float) hero.getHeight())));

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packagesChanged, filter);
        loadApps();
        // Size the hero so the dock sits near the bottom and only a sliver of the app grid peeks out.
        scroll.post(() -> {
            hero.getLayoutParams().height = scroll.getHeight() - dockFrame.getHeight() - dp(24 + 26);
            hero.requestLayout();
        });
        if (checkSelfPermission(WatchNext.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {WatchNext.PERMISSION}, 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadShelf(); // progress changes while the user is away in Nuvio or SmartTube
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        loadShelf();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // This TV never shows the system launcher chooser, so Metak asks on every Home press.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) pickLauncher();
    }

    private void pickLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = new ArrayList<>();
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, 0)) {
            // Negative priority marks the Settings fallback home, which is not a real launcher.
            if (ri.priority >= 0) homes.add(ri);
        }
        if (homes.size() < 2) return;
        homes.sort(Comparator.comparing(ri -> !ri.activityInfo.packageName.equals(getPackageName())));

        CharSequence[] names = new CharSequence[homes.size()];
        for (int i = 0; i < names.length; i++) names[i] = homes.get(i).loadLabel(getPackageManager());
        if (picker != null) picker.dismiss();
        picker = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.pick_launcher)
                .setItems(names, (dialog, which) -> {
                    ActivityInfo a = homes.get(which).activityInfo;
                    if (a.packageName.equals(getPackageName())) return;
                    start(home.setClassName(a.packageName, a.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to; jump to the top like tvOS does.
        scroll.smoothScrollTo(0, 0);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
        list.sort(new ResolveInfo.DisplayNameComparator(pm));

        List<String> docked = dockPackages();
        View[] dockTiles = new View[docked.size()];
        apps.removeAllViews();
        dock.removeAllViews();
        for (ResolveInfo ri : list) {
            ActivityInfo a = ri.activityInfo;
            if (a.packageName.equals(getPackageName())) continue;

            int slot = docked.indexOf(a.packageName);
            View tile = getLayoutInflater().inflate(R.layout.app, slot >= 0 ? dock : apps, false);
            tile.setTag(a.packageName);
            ImageView icon = tile.findViewById(R.id.icon);
            TextView label = tile.findViewById(R.id.label);
            icon.setClipToOutline(true);
            Drawable banner = a.loadBanner(pm);
            if (banner != null) {
                icon.setImageDrawable(banner);
            } else {
                icon.setImageDrawable(a.loadIcon(pm));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int pad = dp(10);
                icon.setPadding(pad, pad, pad, pad);
            }
            label.setText(ri.loadLabel(pm));
            tile.setContentDescription(label.getText());
            if (slot >= 0) label.setVisibility(View.GONE);

            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(a.packageName, a.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            tile.setOnClickListener(v -> start(launch));
            tile.setOnLongClickListener(v -> {
                toggleDock(a.packageName);
                return true;
            });
            tile.setOnFocusChangeListener((v, focused) -> {
                zoom(icon, focused);
                if (slot < 0) label.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            });
            if (slot >= 0) dockTiles[slot] = tile;
            else apps.addView(tile);
        }
        for (View tile : dockTiles) {
            if (tile != null) dock.addView(tile);
        }
        dockFrame.setVisibility(dock.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private List<String> dockPackages() {
        String saved = getPreferences(MODE_PRIVATE).getString(DOCK_KEY, DEFAULT_DOCK);
        return new ArrayList<>(Arrays.asList(saved.isEmpty() ? new String[0] : saved.split(",")));
    }

    private void toggleDock(String pkg) {
        List<String> docked = dockPackages();
        int message = R.string.dock_removed;
        if (!docked.remove(pkg)) {
            if (docked.size() >= DOCK_SIZE) {
                Toast.makeText(this, R.string.dock_full, Toast.LENGTH_SHORT).show();
                return;
            }
            docked.add(pkg);
            message = R.string.dock_added;
        }
        getPreferences(MODE_PRIVATE).edit().putString(DOCK_KEY, String.join(",", docked)).apply();
        loadApps();
        View moved = findViewById(R.id.content).findViewWithTag(pkg);
        if (moved != null) moved.requestFocus();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void loadShelf() {
        new Thread(() -> {
            List<WatchNext.Item> items = WatchNext.load(getContentResolver());
            runOnUiThread(() -> showShelf(items));
        }).start();
    }

    private void showShelf(List<WatchNext.Item> items) {
        boolean inApps = apps.hasFocus();
        shelf.removeAllViews();
        for (WatchNext.Item item : items) {
            View card = getLayoutInflater().inflate(R.layout.card, shelf, false);
            card.setClipToOutline(true);
            card.setContentDescription(item.title);
            Images.load(card.findViewById(R.id.art), item.image);
            ProgressBar progress = card.findViewById(R.id.progress);
            if (item.progress() < 0) progress.setVisibility(View.GONE);
            else progress.setProgress(item.progress());
            card.setOnFocusChangeListener((v, focused) -> {
                zoom(v, focused);
                if (focused) showHero(item);
                // Only a focused card owns the background; otherwise it is the wallpaper.
                showBackdrop(focused ? item.image : null);
            });
            card.setOnClickListener(v -> open(item));
            shelf.addView(card);
        }
        showHero(items.isEmpty() ? null : items.get(0));
        if (inApps) return;
        View first = items.isEmpty() ? apps.getChildAt(0) : shelf.getChildAt(0);
        if (first != null) first.requestFocus();
    }

    private void showHero(WatchNext.Item item) {
        if (item == null) {
            heroTitle.setText(R.string.continue_watching);
            boolean allowed = checkSelfPermission(WatchNext.PERMISSION) == PackageManager.PERMISSION_GRANTED;
            heroSubtitle.setText(allowed ? R.string.nothing_to_continue : R.string.needs_permission);
            return;
        }
        heroTitle.setText(item.title);
        long left = (item.duration - item.position) / 60000;
        String meta = item.progress() < 0 ? item.source() : getString(R.string.time_left, item.source(), left);
        heroSubtitle.setText(item.subtitle == null ? meta : item.subtitle + " · " + meta);
    }

    private void showBackdrop(String uri) {
        if (uri != null) {
            Images.load(backdrop, uri);
            return;
        }
        backdrop.setTag(null); // drop any artwork still loading
        if (wallpaper == null) wallpaper = getDrawable(R.drawable.wallpaper);
        backdrop.setImageDrawable(wallpaper);
    }

    private void open(WatchNext.Item item) {
        Intent intent = null;
        if (item.intentUri != null) {
            try {
                intent = Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException ignored) {
                // Fall back to just opening the app.
            }
        }
        if (intent == null) intent = getPackageManager().getLeanbackLaunchIntentForPackage(item.pkg);
        if (intent != null) start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Mirror the backdrop under the dock, positioned exactly where it sits on screen. Returns true to keep drawing. */
    private boolean syncDockBlur() {
        Drawable d = backdrop.getDrawable();
        Bitmap bitmap = d instanceof BitmapDrawable ? ((BitmapDrawable) d).getBitmap() : null;
        Drawable current = dockBlur.getDrawable();
        if (bitmap == null) {
            if (current != null) dockBlur.setImageDrawable(null);
            return true;
        }
        if (!(current instanceof BitmapDrawable) || ((BitmapDrawable) current).getBitmap() != bitmap) {
            dockBlur.setImageBitmap(bitmap);
        }

        // Same math as the backdrop's centerCrop, shifted by the dock's offset from the backdrop.
        float scale = Math.max(backdrop.getWidth() / (float) bitmap.getWidth(), backdrop.getHeight() / (float) bitmap.getHeight());
        int[] dockAt = new int[2], backdropAt = new int[2];
        dockBlur.getLocationInWindow(dockAt);
        backdrop.getLocationInWindow(backdropAt);
        blurMatrix.setScale(scale, scale);
        blurMatrix.postTranslate(
                (backdrop.getWidth() - bitmap.getWidth() * scale) / 2 + backdropAt[0] - dockAt[0],
                (backdrop.getHeight() - bitmap.getHeight() * scale) / 2 + backdropAt[1] - dockAt[1]);
        // Only touch the view when something moved, or every frame would schedule another.
        if (!blurMatrix.equals(dockBlur.getImageMatrix())) dockBlur.setImageMatrix(blurMatrix);
        float alpha = DOCK_GLASS_OPACITY * backdrop.getAlpha();
        if (dockBlur.getAlpha() != alpha) dockBlur.setAlpha(alpha);
        return true;
    }

    void start(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // App vanished or refuses outside launches; nothing useful to show.
        }
    }

    void zoom(View v, boolean focused) {
        v.animate()
                .scaleX(focused ? 1.1f : 1f)
                .scaleY(focused ? 1.1f : 1f)
                .translationZ(focused ? dp(20) : 0)
                .setDuration(150)
                .start();
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
