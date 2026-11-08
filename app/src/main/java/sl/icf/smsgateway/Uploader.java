package sl.icf.smsgateway;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends queued SMS to the Apps Script server in batches and marks each one
 * by the server's answer: saved or duplicate means done, invalid means the
 * message is not a field app record, anything else stays queued for retry.
 */
public final class Uploader {

    private static final int BATCH = 25;

    private Uploader() {
    }

    /** Returns null when everything pending was handled, otherwise a short problem text. */
    public static synchronized String run(Context ctx) {
        Context c = ctx.getApplicationContext();
        String url = Config.url(c);
        if (url.isEmpty()) return finish(c, "No server address set");

        Db db = Db.get(c);
        int done = 0;
        for (int round = 0; round < 40; round++) {
            List<Db.Msg> batch = db.pending(BATCH);
            if (batch.isEmpty()) break;

            JSONObject res;
            try {
                JSONObject req = new JSONObject();
                req.put("type", "sms");
                JSONArray items = new JSONArray();
                for (Db.Msg m : batch) {
                    JSONObject o = new JSONObject();
                    o.put("key", String.valueOf(m.id));
                    o.put("from", m.sender);
                    o.put("body", m.body);
                    o.put("received", m.received);
                    items.put(o);
                }
                req.put("items", items);
                res = post(url, req.toString());
            } catch (Exception ex) {
                String why = "Could not reach the server (" + ex.getClass().getSimpleName() + ")";
                for (Db.Msg m : batch) db.attempt(m.id, why);
                return finish(c, why);
            }

            if (!res.optBoolean("ok", false)) {
                String why = "Server problem: " + res.optString("error", "no confirmation");
                for (Db.Msg m : batch) db.attempt(m.id, why);
                return finish(c, why);
            }

            Map<String, JSONObject> byKey = new HashMap<>();
            JSONArray results = res.optJSONArray("results");
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject r = results.optJSONObject(i);
                    if (r != null) byKey.put(r.optString("key"), r);
                }
            }

            int handled = 0;
            for (Db.Msg m : batch) {
                JSONObject r = byKey.get(String.valueOf(m.id));
                String status = r == null ? "" : r.optString("status");
                if ("saved".equals(status) || "duplicate".equals(status)) {
                    db.mark(m.id, Db.SENT, status);
                    handled++;
                } else if ("invalid".equals(status)) {
                    db.mark(m.id, Db.REJECTED, r.optString("reason", "Not a field app record"));
                    handled++;
                } else {
                    db.attempt(m.id, "Server did not confirm this message");
                }
            }
            done += handled;
            if (handled == 0) return finish(c, "Server did not confirm the messages");
        }
        return finish(c, null, done);
    }

    private static String finish(Context c, String problem) {
        return finish(c, problem, 0);
    }

    private static String finish(Context c, String problem, int done) {
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        Config.setLastResult(c, problem == null
                ? "Last check " + time + ": " + done + " sent, nothing waiting"
                : "Last check " + time + ": " + problem);
        return problem;
    }

    /**
     * Apps Script answers a POST with a redirect to the result page, so the
     * redirect is followed by hand with a GET.
     */
    static JSONObject post(String address, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(20000);
        c.setReadTimeout(40000);
        c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        OutputStream os = c.getOutputStream();
        try {
            os.write(bytes);
        } finally {
            os.close();
        }

        int code = c.getResponseCode();
        String text;
        if (code >= 300 && code < 400) {
            String location = c.getHeaderField("Location");
            c.disconnect();
            if (location == null) throw new Exception("Redirect without an address");
            HttpURLConnection g = (HttpURLConnection) new URL(location).openConnection();
            g.setInstanceFollowRedirects(true);
            g.setConnectTimeout(20000);
            g.setReadTimeout(40000);
            code = g.getResponseCode();
            text = read(code < 400 ? g.getInputStream() : g.getErrorStream());
            g.disconnect();
        } else {
            text = read(code < 400 ? c.getInputStream() : c.getErrorStream());
            c.disconnect();
        }
        if (code >= 400) throw new Exception("HTTP " + code);
        return new JSONObject(text.trim());
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            in.close();
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
