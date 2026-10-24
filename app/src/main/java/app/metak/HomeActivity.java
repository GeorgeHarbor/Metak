package app.metak;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends Activity {
    private static final String DOCK_KEY = "dock";
    private static final int DOCK_SIZE = 7;
    private static final float DOCK_GLASS_OPACITY = 0.7f;
    private static final long BACKDROP_FADE_MS = 400;
    private static final long WEATHER_TTL_MS = 30 * 60 * 1000;
    private static final String CITY_KEY = "city";
    private static final String ORDER_KEY = "order";
    private static final String HIDDEN_KEY = "hidden";
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
    private long weatherAt;
    private View hero;
    private View backdrop;
    private ImageView[] backdrops;
    private int front; // index of the backdrop layer currently on top
    private String backdropUri;
    private TextView heroTitle, heroSubtitle;
    private LinearLayout shelf;
    private HorizontalScrollView shelfScroll;
    private final Set<String> installed = new HashSet<>();
    private final List<ResolveInfo> gridApps = new ArrayList<>();
    private String moving; // package being moved with the arrows, or null
    private boolean movingInDock;
    private ObjectAnimator wiggle;
    private PopupWindow menu;
    private Bitmap blurSource; // the background the dock blur was made from

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
        backdrops = new ImageView[] {findViewById(R.id.backdrop_a), findViewById(R.id.backdrop_b)};
        heroTitle = findViewById(R.id.hero_title);
        heroSubtitle = findViewById(R.id.hero_subtitle);
        shelf = findViewById(R.id.shelf);
        shelfScroll = findViewById(R.id.shelf_scroll);
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
        setCity(getIntent());
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
    protected void onPause() {
        if (menu != null) menu.dismiss();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        finishMove();
        loadShelf(); // progress changes while the user is away in Nuvio or SmartTube
        loadWeather();
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
        setCity(intent);
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // This TV's remote sends NOTIFICATION from its settings button; others send SETTINGS.
        if (keyCode == KeyEvent.KEYCODE_SETTINGS || keyCode == KeyEvent.KEYCODE_NOTIFICATION) {
            start(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
        // Apps the user moved keep their spot; the rest follow alphabetically (the sort is stable).
        List<String> order = savedList(ORDER_KEY, "");
        list.sort(Comparator.comparingInt(ri -> {
            int i = order.indexOf(ri.activityInfo.packageName);
            return i < 0 ? Integer.MAX_VALUE : i;
        }));
        installed.clear();
        for (ResolveInfo ri : list) installed.add(ri.activityInfo.packageName);
        gridApps.clear();

        List<String> docked = dockPackages();
        View[] dockTiles = new View[docked.size()];
        // Removing the focused tile would hand focus to the first card (and swap the background),
        // so block focus while rebuilding and put it back on the same app afterwards.
        View current = getCurrentFocus();
        Object focusedApp = (apps.hasFocus() || dock.hasFocus()) && current != null ? current.getTag() : null;
        ViewGroup content = findViewById(R.id.content);
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
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
                appMenu(v, a, slot >= 0);
                return true;
            });
            tile.setOnFocusChangeListener((v, focused) -> {
                zoom(icon, focused);
                if (slot < 0) label.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            });
            if (slot >= 0) {
                dockTiles[slot] = tile;
            } else {
                apps.addView(tile);
                gridApps.add(ri);
            }
        }
        for (View tile : dockTiles) {
            if (tile != null) dock.addView(tile);
        }
        dockFrame.setVisibility(dock.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (focusedApp != null && focusTile((String) focusedApp) == null) focusDefault();
    }

    /** Docked packages that are still installed, in dock order. */
    private List<String> dockPackages() {
        List<String> docked = savedList(DOCK_KEY, DEFAULT_DOCK);
        docked.removeIf(pkg -> !installed.contains(pkg));
        return docked;
    }

    private List<String> savedList(String key, String fallback) {
        String saved = getPreferences(MODE_PRIVATE).getString(key, fallback);
        return new ArrayList<>(Arrays.asList(saved.isEmpty() ? new String[0] : saved.split(",")));
    }

    private void saveList(String key, List<String> values) {
        getPreferences(MODE_PRIVATE).edit().putString(key, String.join(",", values)).apply();
    }

    /** Apple-style context menu: a small rounded panel anchored to the item, not a centered dialog. */
    private void showMenu(View anchor, Map<String, Runnable> options) {
        boolean[] holding = {true};
        ScrollView panel = new ScrollView(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                // The menu opens while OK is still held; its repeats and release must not pick the first row.
                boolean ok = e.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || e.getKeyCode() == KeyEvent.KEYCODE_ENTER;
                if (holding[0] && ok) {
                    if (e.getAction() == KeyEvent.ACTION_UP) holding[0] = false;
                    return true;
                }
                holding[0] = false;
                return super.dispatchKeyEvent(e);
            }
        };
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(6), dp(6), dp(6), dp(6));
        for (Map.Entry<String, Runnable> option : options.entrySet()) {
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.menu_item, rows, false);
            row.setText(option.getKey());
            if (option.getKey().equals(getString(R.string.menu_uninstall)) || option.getKey().equals(getString(R.string.menu_remove))) {
                row.setTextColor(getColorStateList(R.color.menu_destructive));
            }
            row.setOnClickListener(v -> {
                menu.dismiss();
                option.getValue().run();
            });
            rows.addView(row);
        }
        panel.addView(rows);
        panel.setBackgroundResource(R.drawable.menu_bg);
        panel.setClipToOutline(true);
        panel.setVerticalScrollBarEnabled(false);

        menu = new PopupWindow(panel, dp(240), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        if (options.size() > 8) menu.setHeight(dp(8 * 44 + 12)); // long lists (Replace with…) scroll
        menu.setElevation(dp(16));
        menu.setAnimationStyle(android.R.style.Animation_Dialog);
        // Below the item when it fits, otherwise above, and always fully on screen.
        int height = menu.getHeight() > 0 ? menu.getHeight() : measuredHeight(panel, dp(240));
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int screen = getWindow().getDecorView().getHeight(), gap = dp(10);
        int y = at[1] + anchor.getHeight() + gap;
        if (y + height > screen - gap) y = at[1] - gap - height;
        y = Math.max(gap, Math.min(y, screen - gap - height));
        int x = Math.max(gap, Math.min(at[0], getWindow().getDecorView().getWidth() - gap - dp(240)));
        menu.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        rows.getChildAt(0).requestFocus();
    }

    private static int measuredHeight(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return view.getMeasuredHeight();
    }

    private void appMenu(View tile, ActivityInfo a, boolean docked) {
        Map<String, Runnable> options = new LinkedHashMap<>();
        if (docked) {
            options.put(getString(R.string.menu_replace), () -> replaceInDock(tile, a.packageName));
        } else if (dock.getChildCount() < DOCK_SIZE) {
            options.put(getString(R.string.menu_add_to_dock), () -> addToDock(a.packageName));
        }
        options.put(getString(R.string.menu_move), () -> startMove(a.packageName, docked));
        if ((a.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            options.put(getString(R.string.menu_uninstall), () -> start(new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:" + a.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }
        showMenu(tile, options);
    }

    private void replaceInDock(View tile, String pkg) {
        Map<String, Runnable> options = new LinkedHashMap<>();
        for (ResolveInfo ri : gridApps) {
            String chosen = ri.activityInfo.packageName;
            options.put(ri.loadLabel(getPackageManager()).toString(), () -> {
                List<String> docked = dockPackages();
                docked.set(docked.indexOf(pkg), chosen);
                saveList(DOCK_KEY, docked);
                loadApps();
                focusTile(chosen);
            });
        }
        showMenu(tile, options);
    }

    private void addToDock(String pkg) {
        List<String> docked = dockPackages();
        docked.add(pkg);
        saveList(DOCK_KEY, docked);
        loadApps();
        focusTile(pkg);
        Toast.makeText(this, R.string.dock_added, Toast.LENGTH_SHORT).show();
    }

    private View focusTile(String pkg) {
        View tile = findViewById(R.id.content).findViewWithTag(pkg);
        if (tile != null) tile.requestFocus();
        return tile;
    }

    private void startMove(String pkg, boolean inDock) {
        moving = pkg;
        movingInDock = inDock;
        wiggleMoving();
        Toast.makeText(this, R.string.move_hint, Toast.LENGTH_SHORT).show();
    }

    /** tvOS-style wiggle on the tile being moved. Re-run after every reload, since tiles are rebuilt. */
    private void wiggleMoving() {
        if (wiggle != null) wiggle.cancel();
        View tile = focusTile(moving);
        if (tile == null) {
            moving = null;
            return;
        }
        wiggle = ObjectAnimator.ofFloat(tile.findViewById(R.id.icon), View.ROTATION, -2f, 2f);
        wiggle.setDuration(120);
        wiggle.setRepeatCount(ObjectAnimator.INFINITE);
        wiggle.setRepeatMode(ObjectAnimator.REVERSE);
        wiggle.start();
    }

    private void shiftMoving(int delta) {
        List<String> order = new ArrayList<>();
        if (movingInDock) order = dockPackages();
        else for (ResolveInfo ri : gridApps) order.add(ri.activityInfo.packageName);
        int from = order.indexOf(moving);
        int to = Math.max(0, Math.min(order.size() - 1, from + delta));
        if (from < 0 || to == from) return;
        order.remove(from);
        order.add(to, moving);
        saveList(movingInDock ? DOCK_KEY : ORDER_KEY, order);
        loadApps();
        wiggleMoving();
    }

    private void finishMove() {
        if (wiggle != null) {
            wiggle.cancel();
            ((View) wiggle.getTarget()).setRotation(0);
        }
        moving = null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving == null) return super.dispatchKeyEvent(e);
        // While moving, the arrows move the app instead of the focus.
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int columns = movingInDock ? 0 : apps.getColumnCount();
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT: shiftMoving(-1); break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: shiftMoving(1); break;
                case KeyEvent.KEYCODE_DPAD_UP: shiftMoving(-columns); break;
                case KeyEvent.KEYCODE_DPAD_DOWN: shiftMoving(columns); break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK: finishMove(); break;
                default: break;
            }
        }
        return true;
    }

    /** adb shell am start -n app.metak/.HomeActivity --es city "Novi Sad" */
    private void setCity(Intent intent) {
        String city = intent.getStringExtra(CITY_KEY);
        if (city == null || city.trim().isEmpty()) return;
        getPreferences(MODE_PRIVATE).edit().putString(CITY_KEY, city.trim()).apply();
        weatherAt = 0;
        loadWeather();
    }

    private void loadWeather() {
        long now = System.currentTimeMillis();
        if (now - weatherAt < WEATHER_TTL_MS) return;
        weatherAt = now;
        boolean fahrenheit = "US".equals(Locale.getDefault().getCountry());
        String city = getPreferences(MODE_PRIVATE).getString(CITY_KEY, "Kraljevo");
        new Thread(() -> {
            Weather w = Weather.load(city, fahrenheit);
            runOnUiThread(() -> {
                if (w == null) {
                    weatherAt = 0; // retry on the next resume
                    return;
                }
                ((TextView) findViewById(R.id.weather_temp)).setText(w.icon + " " + w.temp + "°");
                ((TextView) findViewById(R.id.weather_city)).setText(w.city);
                findViewById(R.id.weather).setVisibility(View.VISIBLE);
            });
        }).start();
    }

    private void loadShelf() {
        loadShelf(-1);
    }

    /** focusIndex: the card to focus afterwards, or -1 for the default (the dock). */
    private void loadShelf(int focusIndex) {
        new Thread(() -> {
            List<WatchNext.Item> items = WatchNext.load(getContentResolver());
            // Other apps' rows can't be deleted by a launcher, so "removed" ones are hidden here.
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            Set<String> hidden = new HashSet<>(prefs.getStringSet(HIDDEN_KEY, Collections.emptySet()));
            Set<String> current = new HashSet<>();
            for (WatchNext.Item item : items) current.add(item.key());
            // Forget rows that are gone or were watched again; skip when the load failed and returned nothing.
            if (!items.isEmpty() && hidden.retainAll(current)) prefs.edit().putStringSet(HIDDEN_KEY, hidden).apply();
            items.removeIf(item -> hidden.contains(item.key()));
            runOnUiThread(() -> showShelf(items, focusIndex));
        }).start();
    }

    private void hideFromShelf(WatchNext.Item item, int index) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        Set<String> hidden = new HashSet<>(prefs.getStringSet(HIDDEN_KEY, Collections.emptySet()));
        hidden.add(item.key());
        prefs.edit().putStringSet(HIDDEN_KEY, hidden).apply();
        loadShelf(index);
    }

    private void showShelf(List<WatchNext.Item> items, int focusIndex) {
        shelf.removeAllViews();
        for (WatchNext.Item item : items) {
            View card = getLayoutInflater().inflate(R.layout.card, shelf, false);
            card.setClipToOutline(true);
            card.setContentDescription(item.title);
            Images.load(card.findViewById(R.id.art), item.image);
            ProgressBar progress = card.findViewById(R.id.progress);
            if (item.progress() < 0) progress.setVisibility(View.GONE);
            else progress.setProgress(item.progress());
            boolean first = shelf.getChildCount() == 0, last = shelf.getChildCount() == items.size() - 1;
            card.setOnFocusChangeListener((v, focused) -> {
                zoom(v, focused);
                if (focused) showHero(item);
                showHeroText(focused);
                // Only a focused card owns the background; otherwise it is the wallpaper.
                // Card to card goes straight from art to art; only leaving the row brings the wallpaper back.
                if (focused) showBackdrop(item.image);
                else shelf.post(() -> {
                    if (!shelf.hasFocus()) showBackdrop(null);
                });
                // The ends snap fully into view instead of stopping just past the card edge.
                if (focused && first) shelfScroll.post(() -> shelfScroll.smoothScrollTo(0, 0));
                if (focused && last) shelfScroll.post(() -> shelfScroll.fullScroll(View.FOCUS_RIGHT));
            });
            card.setOnClickListener(v -> open(item));
            int index = shelf.getChildCount();
            card.setOnLongClickListener(v -> {
                Map<String, Runnable> options = new LinkedHashMap<>();
                options.put(getString(R.string.menu_remove), () -> hideFromShelf(item, index));
                showMenu(v, options);
                return true;
            });
            shelf.addView(card);
        }
        showHero(items.isEmpty() ? null : items.get(0));
        // With no cards there is nothing to focus, so the empty-state hint stays readable.
        showHeroText(items.isEmpty() || shelf.hasFocus());
        // Stay on the row after removing a card; otherwise (every return home) start at the dock.
        View card = focusIndex < 0 ? null : shelf.getChildAt(Math.min(focusIndex, shelf.getChildCount() - 1));
        if (card != null) card.requestFocus();
        else focusDefault();
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

    private void focusDefault() {
        View first = dock.getChildCount() > 0 ? dock.getChildAt(0) : apps.getChildAt(0);
        if (first != null) first.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private void showHeroText(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.INVISIBLE; // invisible keeps the cards from jumping
        heroTitle.setVisibility(visibility);
        heroSubtitle.setVisibility(visibility);
    }

    private void showBackdrop(String uri) {
        backdropUri = uri;
        if (uri == null) {
            if (wallpaper == null) wallpaper = getDrawable(R.drawable.wallpaper);
            fadeBackdrop(wallpaper);
            return;
        }
        // Until the art arrives the old background stays; a stale result is dropped.
        Images.fetch(this, uri, bitmap -> {
            if (bitmap != null && uri.equals(backdropUri)) fadeBackdrop(new BitmapDrawable(getResources(), bitmap));
        });
    }

    private void fadeBackdrop(Drawable next) {
        ImageView current = backdrops[front];
        if (sameImage(current.getDrawable(), next)) return;
        // A fade cut short leaves the outgoing layer half transparent; show it fully so nothing flashes black.
        current.animate().cancel();
        current.setAlpha(1f);
        front ^= 1;
        ImageView incoming = backdrops[front];
        incoming.setImageDrawable(next);
        incoming.setAlpha(0f);
        incoming.bringToFront();
        incoming.animate().alpha(1f).setDuration(BACKDROP_FADE_MS).withEndAction(() -> {
            if (backdrops[front] != current) current.setImageDrawable(null); // free the hidden layer
        }).start();
    }

    private static boolean sameImage(Drawable a, Drawable b) {
        if (a == b) return true;
        return a instanceof BitmapDrawable && b instanceof BitmapDrawable
                && ((BitmapDrawable) a).getBitmap() == ((BitmapDrawable) b).getBitmap();
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
        Drawable d = backdrops[front].getDrawable();
        Bitmap bitmap = d instanceof BitmapDrawable ? ((BitmapDrawable) d).getBitmap() : null;
        if (bitmap == null) {
            if (dockBlur.getDrawable() != null) dockBlur.setImageDrawable(null);
            blurSource = null;
            return true;
        }
        if (bitmap != blurSource) {
            // Blur once per background: a tiny copy stretched back up is a cheap, smooth blur,
            // so scrolling only moves a ready-made image instead of re-blurring every frame.
            blurSource = bitmap;
            Bitmap small = bitmap;
            while (small.getWidth() > 96) {
                small = Bitmap.createScaledBitmap(small, small.getWidth() / 2, Math.max(1, small.getHeight() / 2), true);
            }
            dockBlur.setImageBitmap(small);
        }

        // Same math as the backdrop's centerCrop, shifted by the dock's offset from the backdrop.
        float scale = Math.max(backdrop.getWidth() / (float) bitmap.getWidth(), backdrop.getHeight() / (float) bitmap.getHeight());
        float blurScale = scale * bitmap.getWidth() / ((BitmapDrawable) dockBlur.getDrawable()).getBitmap().getWidth();
        int[] dockAt = new int[2], backdropAt = new int[2];
        dockBlur.getLocationInWindow(dockAt);
        backdrop.getLocationInWindow(backdropAt);
        blurMatrix.setScale(blurScale, blurScale);
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
