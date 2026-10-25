package app.metak;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** The launcher's settings, in tvOS style: grouped sections, white focus fill, live values. */
public class SettingsActivity extends Activity {
    private static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    private SharedPreferences prefs;
    private LinearLayout content;   // the whole column of sections
    private LinearLayout group;     // the section rows are being added to
    private View firstRow;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.settings);
        prefs = Prefs.get(this);
        content = findViewById(R.id.list);

        section(R.string.sec_weather);
        text(R.string.set_city, Prefs.CITY, "Kraljevo");
        choice(R.string.set_units, Prefs.UNITS, "auto",
                new String[] {"auto", "c", "f"},
                new String[] {getString(R.string.units_auto), "Celsius (°C)", "Fahrenheit (°F)"});
        toggle(R.string.set_show_weather, Prefs.SHOW_WEATHER, true);

        section(R.string.sec_clock);
        choice(R.string.set_clock, Prefs.CLOCK, "auto",
                new String[] {"auto", "12", "24"},
                new String[] {getString(R.string.clock_auto), "12-hour", "24-hour"});

        section(R.string.sec_screen);
        intChoice(R.string.set_dim, Prefs.DIM_MIN, 3,
                new int[] {0, 1, 3, 5, 10},
                new String[] {getString(R.string.dim_never), "1 minute", "3 minutes", "5 minutes", "10 minutes"});
        toggle(R.string.set_effects, Prefs.EFFECTS, true);

        section(R.string.sec_wn);
        toggle(R.string.set_wn_blur, Prefs.WN_BLUR, true);
        action(R.string.set_clear_hidden, R.string.set_clear_hidden_confirm, () -> {
            prefs.edit().remove("hidden").apply();
            toast(R.string.set_done);
        });

        section(R.string.sec_general);
        action(R.string.set_reset_dock, R.string.set_reset_dock_confirm, () -> {
            prefs.edit().remove("dock").remove("order").apply();
            toast(R.string.set_done);
        });
        info(getString(R.string.set_version), version());

        if (firstRow != null) firstRow.post(() -> firstRow.requestFocus());
    }

    // --- section + row scaffolding ---

    private void section(int header) {
        TextView h = new TextView(this);
        h.setText(getString(header).toUpperCase());
        h.setTextColor(0xFF8E8E93);
        h.setTextSize(13);
        h.setLetterSpacing(0.07f);
        h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), content.getChildCount() == 0 ? 0 : dp(28), 0, dp(8));
        content.addView(h, lp);

        group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackgroundResource(R.drawable.group_bg);
        group.setClipChildren(false); // let the focused row lift past the group edge
        content.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View addRow(String title, String value) {
        if (group.getChildCount() > 0) {
            View divider = new View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            dlp.setMargins(dp(22), 0, 0, 0);
            divider.setBackgroundColor(0xFF38383A);
            group.addView(divider, dlp);
        }
        View row = getLayoutInflater().inflate(R.layout.settings_row, group, false);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        group.addView(row);
        return row;
    }

    private View actionRow(String title, String value, Runnable onClick) {
        View row = addRow(title, value);
        row.setOnClickListener(v -> onClick.run());
        // tvOS focus: the row lifts a touch as it turns white.
        row.setOnFocusChangeListener((v, f) -> v.animate()
                .scaleX(f ? 1.02f : 1f).scaleY(f ? 1.02f : 1f).setDuration(140).start());
        if (firstRow == null) firstRow = row;
        return row;
    }

    private void setValue(View row, CharSequence value) {
        ((TextView) row.findViewById(R.id.value)).setText(value);
    }

    // --- row types ---

    private void toggle(int title, String key, boolean def) {
        boolean[] on = {prefs.getBoolean(key, def)};
        View row = actionRow(getString(title), onOff(on[0]), null);
        row.setOnClickListener(v -> {
            on[0] = !on[0];
            prefs.edit().putBoolean(key, on[0]).apply();
            setValue(row, onOff(on[0]));
        });
    }

    private void choice(int title, String key, String def, String[] values, String[] labels) {
        View row = actionRow(getString(title), labels[indexOf(values, prefs.getString(key, def))], () -> {});
        row.setOnClickListener(v -> new AlertDialog.Builder(this, DIALOG)
                .setTitle(title)
                .setItems(labels, (d, which) -> {
                    prefs.edit().putString(key, values[which]).apply();
                    setValue(row, labels[which]);
                })
                .show());
    }

    private void intChoice(int title, String key, int def, int[] values, String[] labels) {
        View row = actionRow(getString(title), labels[indexOf(values, prefs.getInt(key, def))], () -> {});
        row.setOnClickListener(v -> new AlertDialog.Builder(this, DIALOG)
                .setTitle(title)
                .setItems(labels, (d, which) -> {
                    prefs.edit().putInt(key, values[which]).apply();
                    setValue(row, labels[which]);
                })
                .show());
    }

    private void text(int title, String key, String def) {
        View row = actionRow(getString(title), prefs.getString(key, def), () -> {});
        row.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setText(prefs.getString(key, def));
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            input.setSingleLine();
            new AlertDialog.Builder(this, DIALOG)
                    .setTitle(title)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        String t = input.getText().toString().trim();
                        if (t.isEmpty()) return;
                        prefs.edit().putString(key, t).apply();
                        setValue(row, t);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private void action(int title, int confirm, Runnable run) {
        actionRow(getString(title), "", () -> new AlertDialog.Builder(this, DIALOG)
                .setTitle(title)
                .setMessage(confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> run.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private void info(String title, String value) {
        View row = addRow(title, value);
        row.setFocusable(false);
        row.setClickable(false);
    }

    // --- helpers ---

    private String onOff(boolean on) {
        return getString(on ? R.string.set_on : R.string.set_off);
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
