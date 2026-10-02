package pl.sergeyst.gdanskmonitor;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CopyOnWriteArrayList;

/** Process-local progress shared by foreground checks, workers and translation. No credentials. */
public final class AppStatus {
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int portal = R.string.action_idle, translation = R.string.action_translate, translations;
    private static boolean portalBusy;
    private AppStatus() {}
    public static void observe(Runnable listener) { LISTENERS.add(listener); }
    public static void remove(Runnable listener) { LISTENERS.remove(listener); }
    private static void changed() { MAIN.post(() -> { for (Runnable r : LISTENERS) r.run(); }); }
    public static synchronized void portal(int phase, boolean busy) { portal = phase; portalBusy = busy; changed(); }
    public static synchronized void translationStarted() { translations++; translation = R.string.action_models; changed(); }
    public static synchronized void translationPhase(int phase) { translation = phase; changed(); }
    public static synchronized void translationEnded() { translations = Math.max(0, translations - 1); changed(); }
    public static synchronized int phase() { return portalBusy ? portal : translations > 0 ? translation : R.string.action_idle; }
    public static synchronized boolean busy() { return portalBusy || translations > 0; }
}
