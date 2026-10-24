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
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public class HomeActivity extends Activity {
    private ScrollView scroll;
    private GridLayout apps;

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

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packagesChanged, filter);
        loadApps();
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
