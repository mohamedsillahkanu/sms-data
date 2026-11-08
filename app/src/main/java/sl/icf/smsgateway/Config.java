package sl.icf.smsgateway;

import android.content.Context;
import android.content.SharedPreferences;

/** Gateway settings: server address and the message prefix to accept. */
public final class Config {

    public static final String DEFAULT_PREFIX = "ITN1*";
    public static final String DEFAULT_URL =
            "https://script.google.com/macros/s/AKfycby46LeTMy0R6HoBcIBI3ACsg44pi86WijghaPMmBm9oMh5tIjm529ViXJK_uSPbZ2FrTQ/exec";

    private Config() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE);
    }

    public static String url(Context c) {
        String u = prefs(c).getString("url", DEFAULT_URL).trim();
        return u.isEmpty() ? DEFAULT_URL : u;
    }

    public static String prefix(Context c) {
        return prefs(c).getString("prefix", DEFAULT_PREFIX).trim();
    }

    public static String lastResult(Context c) {
        return prefs(c).getString("last_result", "");
    }

    public static void save(Context c, String url, String prefix) {
        prefs(c).edit()
                .putString("url", url == null ? "" : url.trim())
                .putString("prefix", prefix == null || prefix.trim().isEmpty() ? DEFAULT_PREFIX : prefix.trim())
                .apply();
    }

    public static void setLastResult(Context c, String text) {
        prefs(c).edit().putString("last_result", text).apply();
    }
}
