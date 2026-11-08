package sl.icf.smsgateway;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** One screen: status, settings, and the list of received messages. */
public class MainActivity extends Activity {

    private static final int NAVY = Color.parseColor("#004080");
    private static final int GOLD = Color.parseColor("#FFC107");
    private static final int INK = Color.parseColor("#0B2540");
    private static final int MUTED = Color.parseColor("#4A5B70");
    private static final int BG = Color.parseColor("#EEF2F7");
    private static final int OK = Color.parseColor("#009E73");
    private static final int WAIT = Color.parseColor("#E69F00");
    private static final int BAD = Color.parseColor("#D55E00");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView pendingView, sentView, rejectedView, lastView, permView;
    private Button permButton, batteryButton;
    private EditText urlInput, prefixInput;
    private LinearLayout list;
    private boolean visible;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (visible) handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        setContentView(build());
        UploadJob.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        refresh();
    }

    // ---------- layout ----------

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(NAVY);
        bar.setPadding(dp(18), dp(16), dp(18), dp(14));
        bar.addView(text("ITN SMS Gateway", 20, Color.WHITE, true));
        bar.addView(text("ICF-SL · 2027 Mass ITN Campaign", 13, Color.parseColor("#DCE7F3"), false));
        root.addView(bar);
        View stripe = new View(this);
        stripe.setBackgroundColor(GOLD);
        root.addView(stripe, new LinearLayout.LayoutParams(-1, dp(4)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // status
        LinearLayout status = card();
        status.addView(heading("Status"));
        LinearLayout counts = new LinearLayout(this);
        counts.setOrientation(LinearLayout.HORIZONTAL);
        pendingView = count(counts, "Waiting", WAIT);
        sentView = count(counts, "Sent to server", OK);
        rejectedView = count(counts, "Rejected", BAD);
        status.addView(counts);
        lastView = text("", 13, MUTED, false);
        lastView.setPadding(0, dp(10), 0, dp(10));
        status.addView(lastView);
        Button sendNow = button("Send waiting messages now", NAVY, Color.WHITE);
        sendNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendNow();
            }
        });
        status.addView(sendNow);
        body.addView(status);

        // phone setup
        LinearLayout setup = card();
        setup.addView(heading("Phone setup"));
        permView = text("", 14, INK, false);
        permView.setPadding(0, 0, 0, dp(8));
        setup.addView(permView);
        permButton = button("Allow the app to receive SMS", NAVY, Color.WHITE);
        permButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 1);
            }
        });
        setup.addView(permButton);
        batteryButton = button("Let the app run in the background", Color.WHITE, NAVY);
        batteryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askBattery();
            }
        });
        setup.addView(batteryButton);
        body.addView(setup);

        // settings
        LinearLayout settings = card();
        settings.addView(heading("Settings"));
        settings.addView(label("Server address (Apps Script web app)"));
        urlInput = input("https://script.google.com/macros/s/.../exec");
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(Config.url(this));
        settings.addView(urlInput);
        settings.addView(label("Accept messages starting with"));
        prefixInput = input(Config.DEFAULT_PREFIX);
        prefixInput.setText(Config.prefix(this));
        settings.addView(prefixInput);
        Button save = button("Save settings", NAVY, Color.WHITE);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Config.save(MainActivity.this, urlInput.getText().toString(), prefixInput.getText().toString());
                Toast.makeText(MainActivity.this, "Settings saved", Toast.LENGTH_SHORT).show();
                UploadJob.schedule(MainActivity.this);
                refresh();
            }
        });
        settings.addView(save);
        body.addView(settings);

        // messages
        LinearLayout msgs = card();
        msgs.addView(heading("Received messages (latest 50)"));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        msgs.addView(list);
        body.addView(msgs);

        return root;
    }

    // ---------- actions ----------

    private void sendNow() {
        Toast.makeText(this, "Sending", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String problem = Uploader.run(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, problem == null ? "All waiting messages sent" : problem,
                                Toast.LENGTH_LONG).show();
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void askBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "Open battery settings and allow this app", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refresh() {
        Db db = Db.get(this);
        pendingView.setText(String.valueOf(db.count(Db.PENDING)));
        sentView.setText(String.valueOf(db.count(Db.SENT)));
        rejectedView.setText(String.valueOf(db.count(Db.REJECTED)));
        String last = Config.lastResult(this);
        if (Config.url(this).isEmpty()) last = "Enter the server address in Settings below.";
        lastView.setText(last.isEmpty() ? "No upload yet." : last);

        boolean sms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        permView.setText((sms ? "SMS receiving: allowed" : "SMS receiving: NOT allowed")
                + "\n" + (battery ? "Background running: allowed" : "Background running: limited by battery saver"));
        permView.setTextColor(sms && battery ? OK : BAD);
        permButton.setVisibility(sms ? View.GONE : View.VISIBLE);
        batteryButton.setVisibility(battery ? View.GONE : View.VISIBLE);

        List<Db.Msg> recent = db.recent(50);
        list.removeAllViews();
        if (recent.isEmpty()) {
            list.addView(text("No messages yet. Messages from the field app appear here.", 14, MUTED, false));
            return;
        }
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (Db.Msg m : recent) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView who = text(m.sender + " · " + df.format(new Date(m.received)), 13, INK, true);
            top.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));
            String label = m.status == Db.SENT ? "Sent" : m.status == Db.REJECTED ? "Rejected" : "Waiting";
            int color = m.status == Db.SENT ? OK : m.status == Db.REJECTED ? BAD : WAIT;
            top.addView(chip(label, color));
            row.addView(top);

            TextView msg = text(m.body, 12.5f, MUTED, false);
            msg.setTypeface(Typeface.MONOSPACE);
            msg.setPadding(0, dp(4), 0, 0);
            row.addView(msg);
            if (m.status != Db.SENT && m.note != null && !m.note.isEmpty()) {
                row.addView(text(m.note + (m.attempts > 0 ? " (tries: " + m.attempts + ")" : ""), 12, color, false));
            }
            list.addView(row);
            View line = new View(this);
            line.setBackgroundColor(Color.parseColor("#E3EAF1"));
            list.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    // ---------- small builders ----------

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView heading(String s) {
        TextView t = text(s.toUpperCase(), 12.5f, NAVY, true);
        t.setLetterSpacing(0.1f);
        t.setPadding(0, 0, 0, dp(10));
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 13, NAVY, true);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(16));
        g.setStroke(dp(1), Color.parseColor("#CFD9E6"));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView count(LinearLayout parent, String name, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(6), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(2), color);
        box.setBackground(g);
        TextView n = text("0", 22, INK, true);
        box.addView(n);
        box.addView(text(name, 12, MUTED, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = dp(8);
        parent.addView(box, lp);
        return n;
    }

    private TextView chip(String s, int color) {
        TextView t = text(s, 12, Color.WHITE, true);
        t.setPadding(dp(10), dp(3), dp(10), dp(3));
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(20));
        t.setBackground(g);
        return t;
    }

    private Button button(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(12));
        if (bg == Color.WHITE) g.setStroke(dp(2), fg);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(INK);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(10));
        g.setStroke(dp(1), Color.parseColor("#CFD9E6"));
        e.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        e.setLayoutParams(lp);
        return e;
    }
}
