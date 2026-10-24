package app.metak;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.URISyntaxException;
import java.util.List;

public class HomeActivity extends Activity {
    private ScrollView scroll;
    private GridLayout apps;
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
        hero = findViewById(R.id.hero);
        backdrop = findViewById(R.id.backdrop);
        heroTitle = findViewById(R.id.hero_title);
        heroSubtitle = findViewById(R.id.hero_subtitle);
        shelf = findViewById(R.id.shelf);
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
    public void onBackPressed() {
        // Home has nowhere to go back to; jump to the top like tvOS does.
        scroll.smoothScrollTo(0, 0);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
        list.sort(new ResolveInfo.DisplayNameComparator(pm));

        apps.removeAllViews();
        for (ResolveInfo ri : list) {
            ActivityInfo a = ri.activityInfo;
            if (a.packageName.equals(getPackageName())) continue;

            View tile = getLayoutInflater().inflate(R.layout.app, apps, false);
            ImageView icon = tile.findViewById(R.id.icon);
            TextView label = tile.findViewById(R.id.label);
            icon.setClipToOutline(true);
            Drawable banner = a.loadBanner(pm);
            if (banner != null) {
                icon.setImageDrawable(banner);
            } else {
                icon.setImageDrawable(a.loadIcon(pm));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int pad = dp(16);
                icon.setPadding(pad, pad, pad, pad);
            }
            label.setText(ri.loadLabel(pm));
            tile.setContentDescription(label.getText());

            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(a.packageName, a.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            tile.setOnClickListener(v -> start(launch));
            tile.setOnFocusChangeListener((v, focused) -> {
                zoom(icon, focused);
                label.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            });
            apps.addView(tile);
        }
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
            backdrop.setImageDrawable(null);
            heroTitle.setText(R.string.continue_watching);
            boolean allowed = checkSelfPermission(WatchNext.PERMISSION) == PackageManager.PERMISSION_GRANTED;
            heroSubtitle.setText(allowed ? R.string.nothing_to_continue : R.string.needs_permission);
            return;
        }
        Images.load(backdrop, item.image);
        heroTitle.setText(item.title);
        long left = (item.duration - item.position) / 60000;
        String meta = item.progress() < 0 ? item.source() : getString(R.string.time_left, item.source(), left);
        heroSubtitle.setText(item.subtitle == null ? meta : item.subtitle + "\n" + meta);
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
