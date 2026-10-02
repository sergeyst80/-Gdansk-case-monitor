package pl.sergeyst.gdanskmonitor;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MonitorEngine {
    public interface Listener {
        void accountDone(Account a, JSONObject status, boolean changed, String error);
        void allDone();
    }
    private final Context context;
    private final Repository repo;
    private final PortalWebSession session;
    private List<Account> queue;
    private int index;
    private Listener listener;
    private boolean checking;

    public boolean isChecking() { return checking; }

    public MonitorEngine(Context c) {
        context = c.getApplicationContext(); repo = new Repository(context); session = new PortalWebSession(context);
    }

    public void checkAll(Listener listener) {
        ArrayList<Account> active = new ArrayList<>();
        for (Account a : repo.accounts()) if (a.enabled) active.add(a);
        check(active, listener);
    }

    public void checkOne(Account account, Listener listener) { check(java.util.Collections.singletonList(account), listener); }

    private void check(List<Account> accounts, Listener l) {
        if (checking) return;
        repo.verifyReadable();
        this.checking = true; this.queue = accounts; this.index = 0; this.listener = l; next();
    }

    private void next() {
        if (index >= queue.size()) { checking = false; if (listener != null) listener.allDone(); return; }
        Account a = queue.get(index++);
        session.check(a, (snapshot, error) -> {
            try {
                JSONObject statuses = repo.statuses();
                JSONObject old = statuses.optJSONObject(a.id);
                JSONObject status = old != null ? old : new JSONObject();
                boolean changed = false;
                String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                if (error == null && snapshot != null) {
                    AppStatus.portal(R.string.action_save, true);
                    String canonical = canonical(snapshot);
                    String hash = sha256(canonical);
                    JSONObject previousSnapshot = status.optJSONObject("snapshot");
                    String oldHash = previousSnapshot == null ? status.optString("hash", "") : sha256(canonical(previousSnapshot));
                    changed = !oldHash.isEmpty() && !oldHash.equals(hash);
                    status.put("hash", hash);
                    status.put("snapshot", snapshot);
                    status.put("lastSuccess", now);
                    status.put("lastError", "");
                    status.put("changed", changed);
                } else {
                    status.put("lastError", error == null ? I18n.errorCode("unknown_error") : error);
                    status.put("lastAttempt", now);
                }
                statuses.put(a.id, status); repo.saveStatuses(statuses);
                if (!PortalWebSession.isBusy()) AppStatus.portal(R.string.action_idle, false);
                if (listener != null) listener.accountDone(a, status, changed, error);
            } catch (Exception e) {
                if (!PortalWebSession.isBusy()) AppStatus.portal(R.string.action_idle, false);
                if (e instanceof StorageException) index = queue.size();
                if (listener != null) listener.accountDone(a, new JSONObject(), false,
                        I18n.errorCode(e instanceof StorageException ? "storage_error" : "unknown_error"));
            }
            next();
        });
    }

    public static String summary(Context context, JSONObject status) {
        JSONObject snap = status.optJSONObject("snapshot");
        if (snap == null) return status.optString("lastError", "").isEmpty() ? I18n.text(context, R.string.not_checked) : I18n.error(context, status.optString("lastError"));
        JSONArray fields = snap.optJSONArray("fields");
        StringBuilder sb = new StringBuilder();
        if (fields != null) for (int i=0; i<fields.length() && i<8; i++) {
            JSONObject f = fields.optJSONObject(i); if (f == null) continue;
            String label = displayLabel(context, f, fields, i);
            String value = f.optString("value", "—");
            if (sb.length() > 0) sb.append("\n"); sb.append(label).append(": ").append(value.isEmpty()?"—":value);
        }
        if (sb.length() == 0) {
            JSONArray lines = snap.optJSONArray("lines");
            if (lines != null) for (int i=0; i<lines.length() && i<10; i++) { if (sb.length()>0) sb.append("\n"); sb.append(lines.optString(i)); }
        }
        return sb.length()==0 ? I18n.text(context, R.string.unrecognized) : sb.toString();
    }

    public static String fieldKey(JSONArray fields, int index) {
        JSONObject field = fields.optJSONObject(index);
        if (field == null) return "";
        String key = field.optString("key", "");
        if (!key.isEmpty()) return key;
        JSONObject caseField = fields.optJSONObject(1), dateField = fields.optJSONObject(2);
        if ((fields.length() == 6 || fields.length() == 7) && caseField != null && dateField != null
                && caseField.optString("value").startsWith("WSC-")
                && dateField.optString("value").matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] keys = fields.length() == 6
                    ? new String[]{"name", "caseNumber", "filedDate", "stage", "stageDescription", "documents"}
                    : new String[]{"name", "caseNumber", "filedDate", "stage", "stageDescription", "notes", "documents"};
            return keys[index];
        }
        return "";
    }

    public static String displayLabel(Context context, JSONObject field, JSONArray fields, int index) {
        String key = fieldKey(fields, index);
        switch (key) {
            case "name": return I18n.text(context, R.string.name);
            case "caseNumber": return I18n.text(context, R.string.case_number);
            case "filedDate": return I18n.text(context, R.string.filed_date);
            case "stage": return I18n.text(context, R.string.stage);
            case "stageDescription": return I18n.text(context, R.string.stage_description);
            case "notes": return I18n.text(context, R.string.notes);
            case "documents": return I18n.text(context, R.string.documents);
        }
        String label = field.optString("label", "").trim();
        if (!label.isEmpty()) return label;
        // Compatibility with already saved snapshots from 1.0.2, before refresh.
        // Match the verified portal schema, not arbitrary unlabeled fields.
        JSONObject caseField = fields.optJSONObject(1), dateField = fields.optJSONObject(2);
        if ((fields.length() == 6 || fields.length() == 7) && caseField != null && dateField != null
                && caseField.optString("value").startsWith("WSC-")
                && dateField.optString("value").matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] names = fields.length() == 6
                    ? new String[]{I18n.text(context, R.string.name), I18n.text(context, R.string.case_number), I18n.text(context, R.string.filed_date), I18n.text(context, R.string.stage), I18n.text(context, R.string.stage_description), I18n.text(context, R.string.documents)}
                    : new String[]{I18n.text(context, R.string.name), I18n.text(context, R.string.case_number), I18n.text(context, R.string.filed_date), I18n.text(context, R.string.stage), I18n.text(context, R.string.stage_description), I18n.text(context, R.string.notes), I18n.text(context, R.string.documents)};
            return names[index];
        }
        return I18n.text(context, R.string.portal_data);
    }

    private static String canonical(JSONObject snapshot) throws Exception {
        // Label improvements alone must not trigger a "case changed" notification.
        JSONObject data = new JSONObject(snapshot.toString());
        JSONArray fields = data.optJSONArray("fields");
        if (fields != null) for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.optJSONObject(i);
            if (field != null) { field.remove("label"); field.remove("key"); }
        }
        return data.toString();
    }
    private static String sha256(String s) throws Exception {
        byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte x : b) out.append(String.format(Locale.ROOT, "%02x", x)); return out.toString();
    }
    public void destroy() { checking = false; listener = null; session.destroy(); }
}
