package app.metak;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** The launcher's settings, in iOS style: icon rows in inset groups, switches, and pages that push in. */
public class SettingsActivity extends Activity {
    private static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    // iOS system colours, as used for Settings icons.
    private static final int BLUE = 0xFF007AFF, ORANGE = 0xFFFF9500, TEAL = 0xFF32ADE6, INDIGO = 0xFF5856D6,
            RED = 0xFFFF3B30, PINK = 0xFFFF2D55, GREEN = 0xFF34C759, GRAY = 0xFF8E8E93;
    private static final int SWITCH_OFF = 0xFF39393D;

    static Bitmap backdrop;         // the home screen as it looked when settings opened

    private SharedPreferences prefs;
    private LinearLayout content;   // the current page's column of sections
    private LinearLayout group;     // the section rows are being added to
    private TextView back, pageTitle;
    private Runnable onBack;        // null on the main page
    private View focusTarget;
    private String returnTo;        // tag of the main-page row to refocus when a sub page closes

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.settings);
        prefs = Prefs.get(this);
        content = findViewById(R.id.list);
        back = findViewById(R.id.back);
        pageTitle = findViewById(R.id.page_title);
        if (backdrop != null) {
            ImageView bg = findViewById(R.id.backdrop);
            bg.setImageBitmap(backdrop);
            // The snapshot is quarter size, so upscaling alone softens it where RenderEffect is missing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ColorMatrix sat = new ColorMatrix();
                sat.setSaturation(1.4f); // iOS materials keep colour vivid under the blur
                bg.setRenderEffect(RenderEffect.createChainEffect(
                        RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(sat)),
                        RenderEffect.createBlurEffect(dp(40), dp(40), Shader.TileMode.CLAMP)));
            }
        }
        main(false);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) backdrop = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (onBack != null) onBack.run();
        else super.onBackPressed();
    }

    private void main(boolean returning) {
        page(getString(R.string.settings), null, !returning);

        section(R.string.sec_weather);
        String city = prefs.getString(Prefs.CITY, null);
        nav(R.drawable.ic_s_location, BLUE, getString(R.string.set_city),
                city != null ? city : getString(R.string.city_here), this::cityPicker);
        choice(R.drawable.ic_s_thermometer, ORANGE, R.string.set_units, Prefs.UNITS, "auto",
                new String[] {"auto", "c", "f"},
                new String[] {getString(R.string.units_auto), "Celsius (°C)", "Fahrenheit (°F)"});
        toggle(R.drawable.ic_s_cloud, TEAL, R.string.set_show_weather, Prefs.SHOW_WEATHER, true);

        section(R.string.sec_clock);
        choice(R.drawable.ic_s_clock, INDIGO, R.string.set_clock, Prefs.CLOCK, "auto",
                new String[] {"auto", "12", "24"},
                new String[] {getString(R.string.clock_auto), "12-hour", "24-hour"});

        section(R.string.sec_screen);
        int[] dims = {0, 1, 3, 5, 10};
        String[] dimLabels = {getString(R.string.dim_never), "1 minute", "3 minutes", "5 minutes", "10 minutes"};
        int dim = indexOf(dims, prefs.getInt(Prefs.DIM_MIN, 3));
        nav(R.drawable.ic_s_moon, BLUE, getString(R.string.set_dim), dimLabels[dim], () -> picker(
                R.string.set_dim, dimLabels, dim, i -> prefs.edit().putInt(Prefs.DIM_MIN, dims[i]).apply()));
        toggle(R.drawable.ic_s_power, RED, R.string.set_effects, Prefs.EFFECTS, true);
        toggle(R.drawable.ic_s_sparkles, PINK, R.string.set_animations, Prefs.ANIMATIONS, true);

        section(R.string.sec_wn);
        int on = 0;
        for (String pkg : WatchNext.sources(getContentResolver())) if (WatchNext.enabled(prefs, pkg)) on++;
        nav(R.drawable.ic_s_play, GREEN, getString(R.string.set_sources),
                getResources().getQuantityString(R.plurals.sources_count, on, on), this::sources);
        toggle(R.drawable.ic_s_drop, INDIGO, R.string.set_wn_blur, Prefs.WN_BLUR, true);
        action(R.drawable.ic_s_restore, GRAY, R.string.set_clear_hidden, R.string.set_clear_hidden_confirm, () -> {
            prefs.edit().remove("hidden").apply();
            toast(R.string.set_done);
        });

        section(R.string.sec_general);
        actionRow(R.drawable.ic_update, GRAY, getString(R.string.set_check_updates), "", this::checkForUpdates);
        action(R.drawable.ic_s_grid, GRAY, R.string.set_reset_dock, R.string.set_reset_dock_confirm, () -> {
            prefs.edit().remove("dock").remove("order").apply();
            toast(R.string.set_done);
        });
        info(R.drawable.ic_s_info, GRAY, getString(R.string.set_version), version());

        showPage();
    }

    // --- sub pages ---

    /** An iOS option list: the current choice carries a checkmark, which moves as another is picked. */
    private void picker(int title, String[] labels, int selected, IntConsumer pick) {
        page(getString(title), () -> main(true), true);
        section(0);
        View[] rows = new View[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int which = i;
            rows[i] = actionRow(0, 0, labels[i], "", () -> {
                pick.accept(which);
                for (View r : rows) accessory(r, r == rows[which] ? R.drawable.ic_s_check : 0);
            });
            if (i == selected) {
                accessory(rows[i], R.drawable.ic_s_check);
                focusTarget = rows[i];
            }
        }
        showPage();
    }

    /** iOS Weather-style city search: suggestions refresh as you type. */
    private void cityPicker() {
        page(getString(R.string.set_city), () -> main(true), true);
        EditText search = new EditText(this);
        search.setHint(R.string.city_search);
        search.setSingleLine();
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // Keep the TV keyboard from going full screen, so the suggestions stay visible above it.
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        search.setBackgroundResource(R.drawable.search_bg);
        search.setTextColor(0xFFF5F5F7);
        search.setHintTextColor(GRAY);
        search.setTextSize(17);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_s_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(12));
        search.setCompoundDrawableTintList(ColorStateList.valueOf(GRAY));
        content.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        focusTarget = search;

        section(0);
        View here = actionRow(R.drawable.ic_s_location, BLUE, getString(R.string.city_here), "", () -> {
            prefs.edit().remove(Prefs.CITY).remove(Prefs.CITY_LOC).apply();
            onBack.run();
        });
        if (!prefs.contains(Prefs.CITY)) accessory(here, R.drawable.ic_s_check);
        View[] idle = {back, pageTitle, group, footer(R.string.city_footer)};

        section(0);
        LinearLayout results = group;
        results.setVisibility(View.GONE);
        Handler debounce = new Handler(Looper.getMainLooper());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence t, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence t, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                // iOS collapses the title while searching; here it also keeps results above the TV keyboard.
                for (View v : idle) v.setVisibility(e.length() > 0 ? View.GONE : View.VISIBLE);
                debounce.removeCallbacksAndMessages(null);
                debounce.postDelayed(() -> suggest(results, search), 300);
            }
        });
        // The keyboard's search key puts the keyboard away and jumps to the first suggestion.
        search.setOnEditorActionListener((v, id, ev) -> {
            if (results.getChildCount() == 0) return false;
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(), 0);
            return results.getChildAt(0).requestFocus();
        });
        showPage();
        // Like iOS search, start typing straight away; the delay lets the page take window focus first.
        search.postDelayed(() -> getSystemService(InputMethodManager.class).showSoftInput(search, 0), 300);
    }

    private void suggest(LinearLayout results, EditText search) {
        String q = search.getText().toString().trim();
        if (q.length() < 2) {
            results.removeAllViews();
            results.setVisibility(View.GONE);
            return;
        }
        new Thread(() -> {
            List<Weather.Place> places = Weather.search(q);
            runOnUiThread(() -> {
                if (!q.equals(search.getText().toString().trim())) return; // typed on since; a newer lookup follows
                results.removeAllViews();
                group = results;
                for (Weather.Place p : places) {
                    actionRow(0, 0, p.name, p.detail, () -> {
                        prefs.edit().putString(Prefs.CITY, p.name).putString(Prefs.CITY_LOC, p.loc).apply();
                        onBack.run();
                    });
                }
                results.setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    private void sources() {
        page(getString(R.string.set_sources), () -> main(true), true);
        section(0);
        Set<String> pkgs = WatchNext.sources(getContentResolver());
        for (String pkg : pkgs) {
            View row = switchRow(R.drawable.ic_s_play, GRAY, WatchNext.label(this, pkg), WatchNext.enabled(prefs, pkg), on -> {
                Set<String> flipped = new HashSet<>(prefs.getStringSet(Prefs.WN_FLIPPED, Collections.emptySet()));
                if (!flipped.remove(pkg)) flipped.add(pkg);
                prefs.edit().putStringSet(Prefs.WN_FLIPPED, flipped).apply();
            });
            Drawable icon;
            try {
                icon = getPackageManager().getApplicationIcon(pkg);
            } catch (Exception e) {
                continue; // keep the generic play tile
            }
            ImageView iv = row.findViewById(R.id.icon);
            iv.setPadding(0, 0, 0, 0);
            iv.setImageTintList(null);
            iv.setBackgroundTintList(ColorStateList.valueOf(0));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);  // round the app icon like an iOS tile
            iv.setImageDrawable(icon);
        }
        if (pkgs.isEmpty()) content.removeView(group);
        footer(pkgs.isEmpty() ? R.string.sources_empty : R.string.sources_footer);
        showPage();
    }

    // --- page + section + row scaffolding ---

    private void page(String title, Runnable backTo, boolean forward) {
        boolean first = content.getChildCount() == 0;
        onBack = backTo;
        focusTarget = null;
        content.removeAllViews();
        pageTitle.setText(title);
        pageTitle.setVisibility(View.VISIBLE); // city search may have collapsed it
        back.setText("‹ " + getString(R.string.settings));
        back.setVisibility(backTo == null ? View.INVISIBLE : View.VISIBLE);
        if (first) return; // the activity transition already animates the first page
        // iOS push/pop: the new page slides in from the side it lives on.
        View column = (View) content.getParent();
        column.setAlpha(0f);
        column.setTranslationX(dp(forward ? 48 : -48));
        column.animate().alpha(1f).translationX(0).setDuration(anim(220)).start();
    }

    private void showPage() {
        if (onBack == null && returnTo != null) {
            View r = content.findViewWithTag(returnTo);
            if (r != null) focusTarget = r;
            returnTo = null;
        }
        View f = focusTarget;
        if (f != null) f.post(f::requestFocus);
        ((View) content.getParent().getParent()).scrollTo(0, 0);
    }

    private void section(int header) {
        if (header != 0) {
            TextView h = new TextView(this);
            h.setText(getString(header).toUpperCase());
            h.setTextColor(GRAY);
            h.setTextSize(13);
            h.setLetterSpacing(0.04f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(16), content.getChildCount() == 0 ? 0 : dp(32), 0, dp(8));
            content.addView(h, lp);
        }
        group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackgroundResource(R.drawable.group_bg);
        group.setClipChildren(false); // let the focused row lift past the group edge
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (header == 0 && content.getChildCount() > 0) lp.topMargin = dp(24); // headerless groups still need air
        content.addView(group, lp);
    }

    private View footer(int text) {
        TextView f = new TextView(this);
        f.setText(text);
        f.setTextColor(GRAY);
        f.setTextSize(13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(8), dp(16), 0);
        content.addView(f, lp);
        return f;
    }

    private View addRow(int icon, int color, String title, String value) {
        if (group.getChildCount() > 0) {
            // iOS insets the separator to start under the title, past the icon.
            View divider = new View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            dlp.setMargins(dp(icon == 0 ? 16 : 66), 0, 0, 0);
            divider.setBackgroundColor(0xFF38383A);
            group.addView(divider, dlp);
        }
        View row = getLayoutInflater().inflate(R.layout.settings_row, group, false);
        row.setTag(title);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        if (icon != 0) {
            ImageView iv = row.findViewById(R.id.icon);
            iv.setVisibility(View.VISIBLE);
            iv.setImageResource(icon);
            iv.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
            iv.setBackgroundTintList(ColorStateList.valueOf(color));
        }
        group.addView(row);
        return row;
    }

    private View actionRow(int icon, int color, String title, String value, Runnable onClick) {
        View row = addRow(icon, color, title, value);
        row.setOnClickListener(v -> onClick.run());
        // The focused row lifts a touch as it lightens.
        row.setOnFocusChangeListener((v, f) -> v.animate()
                .scaleX(f ? 1.02f : 1f).scaleY(f ? 1.02f : 1f).setDuration(anim(140)).start());
        if (focusTarget == null) focusTarget = row;
        return row;
    }

    /** A row that opens a sub page, with a chevron. */
    private void nav(int icon, int color, String title, String value, Runnable open) {
        View row = actionRow(icon, color, title, value, () -> {
            returnTo = title;
            open.run();
        });
        accessory(row, R.drawable.ic_s_chevron);
    }

    private View switchRow(int icon, int color, String title, boolean on, Consumer<Boolean> set) {
        boolean[] state = {on};
        View row = actionRow(icon, color, title, "", null);
        row.findViewById(R.id.switch_track).setVisibility(View.VISIBLE);
        setSwitch(row, on, false);
        row.setOnClickListener(v -> {
            state[0] = !state[0];
            set.accept(state[0]);
            setSwitch(row, state[0], true);
        });
        return row;
    }

    private void setSwitch(View row, boolean on, boolean animate) {
        View track = row.findViewById(R.id.switch_track);
        track.setBackgroundTintList(ColorStateList.valueOf(on ? GREEN : SWITCH_OFF));
        View knob = row.findViewById(R.id.switch_knob);
        float x = on ? dp(20) : 0;
        if (animate) knob.animate().translationX(x).setDuration(anim(180)).start();
        else knob.setTranslationX(x);
    }

    private void accessory(View row, int drawable) {
        ImageView a = row.findViewById(R.id.accessory);
        a.setVisibility(drawable == 0 ? View.GONE : View.VISIBLE);
        if (drawable == 0) return;
        a.setImageResource(drawable);
        a.setImageTintList(ColorStateList.valueOf(drawable == R.drawable.ic_s_check ? 0xFF0A84FF : 0xFF636366));
    }

    // --- row types ---

    private void toggle(int icon, int color, int title, String key, boolean def) {
        switchRow(icon, color, getString(title), prefs.getBoolean(key, def),
                on -> prefs.edit().putBoolean(key, on).apply());
    }

    private void choice(int icon, int color, int title, String key, String def, String[] values, String[] labels) {
        int selected = indexOf(values, prefs.getString(key, def));
        nav(icon, color, getString(title), labels[selected], () -> picker(
                title, labels, selected, i -> prefs.edit().putString(key, values[i]).apply()));
    }

    private void action(int icon, int color, int title, int confirm, Runnable run) {
        actionRow(icon, color, getString(title), "", () -> new AlertDialog.Builder(this, DIALOG)
                .setTitle(title)
                .setMessage(confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> run.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private void info(int icon, int color, String title, String value) {
        View row = addRow(icon, color, title, value);
        row.setFocusable(false);
        row.setClickable(false);
    }

    private void checkForUpdates() {
        toast(R.string.update_checking);
        new Thread(() -> {
            Updater.Info info = Updater.check(this);
            runOnUiThread(() -> {
                if (info == null) {
                    toast(R.string.update_uptodate);
                    return;
                }
                new AlertDialog.Builder(this, DIALOG)
                        .setTitle(getString(R.string.update_prompt, info.versionName))
                        .setPositiveButton(R.string.update_install, (d, w) -> startInstall(info.apkUrl))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }).start();
    }

    private void startInstall(String apkUrl) {
        toast(R.string.update_downloading);
        new Thread(() -> {
            try {
                Updater.install(this, apkUrl);
            } catch (Exception e) {
                runOnUiThread(() -> toast(R.string.update_failed));
            }
        }).start();
    }

    // --- helpers ---

    private int anim(int ms) {
        return prefs.getBoolean(Prefs.ANIMATIONS, true) ? ms : 0;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return 0;
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private void toast(int msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
