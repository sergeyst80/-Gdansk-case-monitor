package pl.sergeyst.gdanskmonitor;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Arrays;
import java.util.Locale;

/** App-owned language preference; never changes the device language. */
public final class I18n {
    public static final String[] CODES = {"en", "ru", "pl", "uk", "de", "fr", "es", "pt", "zh", "ja", "ar", "hi"};
    public static final String[] NAMES = {"English", "Русский", "Polski", "Українська", "Deutsch", "Français", "Español", "Português", "中文", "日本語", "العربية", "हिन्दी"};
    private I18n() {}

    public static String language(Context context) {
        String code = new SecureStore(context).getPlain("language", "en");
        return Arrays.asList(CODES).contains(code) ? code : "en";
    }

    public static void setLanguage(Context context, String code) {
        if (!Arrays.asList(CODES).contains(code)) throw new IllegalArgumentException("Unsupported language");
        new SecureStore(context).putPlain("language", code);
    }

    public static Context context(Context base) {
        Locale locale = Locale.forLanguageTag(language(base));
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocales(new LocaleList(locale));
        config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }

    public static String text(Context context, int id, Object... args) {
        return context(context).getString(id, args);
    }

    // Save error identifiers, not translations, so cached errors follow the chosen language.
    public static String errorCode(String name, Object arg) {
        return "@error/" + name + "/" + (arg == null ? "" : arg.toString());
    }
    public static String errorCode(String name) { return errorCode(name, null); }

    public static String error(Context context, String stored) {
        if (stored == null || stored.isEmpty()) return "";
        if (!stored.startsWith("@error/")) return stored; // Legacy snapshots / unexpected errors.
        String[] parts = stored.split("/", 3);
        if (parts.length != 3) return text(context, R.string.unknown_error);
        int id;
        switch (parts[1]) {
            case "portal_busy": id = R.string.portal_busy; break;
            case "redirect_error": id = R.string.redirect_error; break;
            case "network_error": id = R.string.network_error; break;
            case "http_error": id = R.string.http_error; break;
            case "webview_error": id = R.string.webview_error; break;
            case "ssl_error": id = R.string.ssl_error; break;
            case "timeout_error": id = R.string.timeout_error; break;
            case "challenge_error": id = R.string.challenge_error; break;
            case "storage_error": id = R.string.storage_error; break;
            case "rejected_error": id = R.string.rejected_error; break;
            default: id = R.string.unknown_error;
        }
        return text(context, id, parts[2]);
    }
}
