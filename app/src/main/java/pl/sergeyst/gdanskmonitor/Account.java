package pl.sergeyst.gdanskmonitor;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public final class Account {
    public String id;
    public String name;
    public String login;
    public String password;
    public boolean enabled;

    public Account(String id, String name, String login, String password, boolean enabled) {
        this.id = id; this.name = name; this.login = login; this.password = password; this.enabled = enabled;
    }

    public static Account create(String name, String login, String password) {
        return new Account(UUID.randomUUID().toString(), name, login, password, true);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id); o.put("name", name); o.put("login", login); o.put("password", password); o.put("enabled", enabled);
        return o;
    }

    public static Account fromJson(JSONObject o) {
        return new Account(o.optString("id", UUID.randomUUID().toString()), o.optString("name", "User"),
                o.optString("login", ""), o.optString("password", ""), o.optBoolean("enabled", true));
    }
}
