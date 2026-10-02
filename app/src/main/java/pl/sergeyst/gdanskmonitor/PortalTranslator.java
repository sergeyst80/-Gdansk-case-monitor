package pl.sergeyst.gdanskmonitor;

import android.os.Handler;
import android.os.Looper;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Google ML Kit translation is on-device. Never pass credentials, names or case numbers. */
public final class PortalTranslator implements AutoCloseable {
    public interface Callback { void done(JSONObject translated, boolean failed); }
    private final LanguageIdentifier identifier = LanguageIdentification.getClient();
    private final Map<String, Translator> clients = new HashMap<>();
    private final LinkedHashMap<String, JSONObject> cache = new LinkedHashMap<>();
    private final Set<String> failures = new HashSet<>();
    private final ArrayList<Job> jobs = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean closed;

    public static boolean translatable(String key) {
        return "stage".equals(key) || "stageDescription".equals(key)
                || "notes".equals(key) || "documents".equals(key);
    }

    public void translate(JSONObject snapshot, String targetCode, Callback callback) {
        if (closed) return;
        String target = TranslateLanguage.fromLanguageTag(targetCode);
        if (target == null) { callback.done(snapshot, true); return; }
        String key = target + ":" + snapshot.toString();
        if (cache.containsKey(key)) { callback.done(cache.get(key), false); return; }
        if (failures.contains(key)) { callback.done(snapshot, true); return; }
        try {
            Job job = new Job(new JSONObject(snapshot.toString()), target, key, callback);
            jobs.add(job); AppStatus.translationStarted();
            handler.postDelayed(job.timeout, 120000);
            job.next();
        } catch (Exception e) { callback.done(snapshot, true); }
    }

    private final class Job {
        final JSONObject result;
        final JSONObject original;
        final JSONArray fields;
        final String target, key;
        final Callback callback;
        final Runnable timeout = () -> finish(true, true);
        int index;
        boolean finished, failed;
        Job(JSONObject result, String target, String key, Callback callback) {
            this.result = result; this.fields = result.optJSONArray("fields");
            try { this.original = new JSONObject(result.toString()); }
            catch (Exception e) { throw new IllegalArgumentException("Invalid snapshot", e); }
            this.target = target; this.key = key; this.callback = callback;
        }
        void next() {
            if (finished || closed) return;
            if (fields == null || index >= fields.length()) { finish(failed, true); return; }
            int position = index++;
            JSONObject field = fields.optJSONObject(position);
            if (field == null || !translatable(MonitorEngine.fieldKey(fields, position))) { next(); return; }
            String value = field.optString("value", "").trim();
            if (value.isEmpty()) { next(); return; }
            if (value.length() > 10000) { failed = true; next(); return; }
            AppStatus.translationPhase(R.string.action_translate);
            identifier.identifyLanguage(value).addOnSuccessListener(code -> {
                if (finished || closed) return;
                // The portal's unlabeled short statuses are Polish; longer text is identified.
                String source = TranslateLanguage.fromLanguageTag(code);
                if (source == null) source = TranslateLanguage.POLISH;
                if (source.equals(target)) { next(); return; }
                String pair = source + ":" + target;
                Translator client = clients.get(pair);
                if (client == null) {
                    client = Translation.getClient(new TranslatorOptions.Builder()
                            .setSourceLanguage(source).setTargetLanguage(target).build());
                    clients.put(pair, client);
                }
                final Translator translator = client;
                AppStatus.translationPhase(R.string.action_models);
                translator.downloadModelIfNeeded(new DownloadConditions.Builder().requireWifi().build())
                        .addOnSuccessListener(unused -> {
                            if (finished || closed) return;
                            AppStatus.translationPhase(R.string.action_translate);
                            translator.translate(value).addOnSuccessListener(translated -> {
                                if (finished || closed) return;
                                try { field.put("value", translated); } catch (Exception ignored) { failed = true; }
                                next();
                            }).addOnFailureListener(error -> { if (!finished && !closed) { failed = true; next(); } });
                        }).addOnFailureListener(error -> { if (!finished && !closed) { failed = true; next(); } });
            }).addOnFailureListener(error -> { if (!finished && !closed) { failed = true; next(); } });
        }
        void finish(boolean error, boolean deliver) {
            if (finished) return;
            finished = true; handler.removeCallbacks(timeout); jobs.remove(this); AppStatus.translationEnded();
            if (deliver && !error) {
                if (cache.size() >= 16) cache.remove(cache.keySet().iterator().next());
                cache.put(key, result);
            } else if (deliver) { if (failures.size() >= 16) failures.clear(); failures.add(key); }
            if (deliver && !closed) callback.done(error ? original : result, error);
        }
    }

    public void cancelPending() { for (Job job : new ArrayList<>(jobs)) job.finish(false, false); }
    public void retryFailures() { failures.clear(); }
    @Override public void close() {
        closed = true; cancelPending(); identifier.close();
        for (Translator translator : clients.values()) translator.close();
        clients.clear(); cache.clear(); failures.clear();
    }
}
