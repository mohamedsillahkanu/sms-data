package sl.icf.smsgateway;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Queue of received SMS kept on the phone until the server confirms them. */
public class Db extends SQLiteOpenHelper {

    public static final int PENDING = 0;
    public static final int SENT = 1;
    public static final int REJECTED = 2;

    private static Db instance;

    public static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "gateway.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE msg (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT, body TEXT, received INTEGER, " +
                "status INTEGER DEFAULT 0, attempts INTEGER DEFAULT 0, " +
                "note TEXT, sent_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // first version, nothing to migrate
    }

    public static class Msg {
        public long id;
        public String sender;
        public String body;
        public String note;
        public long received;
        public long sentAt;
        public int status;
        public int attempts;
    }

    public synchronized long add(String sender, String body, long received) {
        ContentValues v = new ContentValues();
        v.put("sender", sender);
        v.put("body", body);
        v.put("received", received);
        v.put("status", PENDING);
        return getWritableDatabase().insert("msg", null, v);
    }

    public synchronized List<Msg> pending(int limit) {
        return query("status=?", new String[]{String.valueOf(PENDING)}, "id ASC", limit);
    }

    public synchronized List<Msg> recent(int limit) {
        return query(null, null, "id DESC", limit);
    }

    private List<Msg> query(String where, String[] args, String order, int limit) {
        List<Msg> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("msg", null, where, args, null, null, order, String.valueOf(limit));
        try {
            while (c.moveToNext()) {
                Msg m = new Msg();
                m.id = c.getLong(c.getColumnIndexOrThrow("id"));
                m.sender = c.getString(c.getColumnIndexOrThrow("sender"));
                m.body = c.getString(c.getColumnIndexOrThrow("body"));
                m.received = c.getLong(c.getColumnIndexOrThrow("received"));
                m.status = c.getInt(c.getColumnIndexOrThrow("status"));
                m.attempts = c.getInt(c.getColumnIndexOrThrow("attempts"));
                m.note = c.getString(c.getColumnIndexOrThrow("note"));
                m.sentAt = c.getLong(c.getColumnIndexOrThrow("sent_at"));
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized void mark(long id, int status, String note) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        v.put("note", note);
        v.put("sent_at", System.currentTimeMillis());
        getWritableDatabase().update("msg", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void attempt(long id, String note) {
        getWritableDatabase().execSQL("UPDATE msg SET attempts = attempts + 1, note = ? WHERE id = ?",
                new Object[]{note, id});
    }

    public synchronized int count(int status) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM msg WHERE status = ?",
                new String[]{String.valueOf(status)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }
}
