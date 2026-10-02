package pl.sergeyst.gdanskmonitor;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class Repository {
    private final SecureStore store;
    public Repository(Context c) { store = new SecureStore(c); }

    public synchronized List<Account> accounts() {
        ArrayList<Account> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(store.getEncrypted("accounts", "[]"));
            for (int i=0; i<arr.length(); i++) out.add(Account.fromJson(arr.getJSONObject(i)));
        } catch (Exception e) { throw new StorageException(); }
        return out;
    }

    public synchronized void saveAccounts(List<Account> accounts) throws Exception {
        verifyReadable();
        JSONArray arr = new JSONArray();
        for (Account a : accounts) arr.put(a.toJson());
        store.putEncrypted("accounts", arr.toString());
    }

    public synchronized JSONObject statuses() {
        try { return new JSONObject(store.getEncrypted("statuses", "{}")); }
        catch (Exception e) { throw new StorageException(); }
    }

    public synchronized void saveStatuses(JSONObject statuses) throws Exception {
        verifyReadable();
        store.putEncrypted("statuses", statuses.toString());
    }

    public SecureStore settings() { return store; }
    public void verifyReadable() { accounts(); statuses(); }
}
