package pl.sergeyst.gdanskmonitor;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public final class MonitorSchedule {
    public static final long[] INTERVALS = {15, 30, 60, 180, 360, 720, 1440};
    private MonitorSchedule() {}
    public static boolean validInterval(long minutes) { return minutes >= 15 && minutes <= 1440; }
    public static long interval(Context c) {
        return Math.max(15, Math.min(1440, new SecureStore(c).getLong("interval", 30)));
    }
    public static void configure(Context c, boolean enabled, long minutes) {
        if (!validInterval(minutes)) throw new IllegalArgumentException("Interval must be 15..1440 minutes");
        SecureStore settings = new SecureStore(c);
        WorkManager manager = WorkManager.getInstance(c);
        if (enabled) {
            Constraints network = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(MonitorWorker.class, minutes, TimeUnit.MINUTES)
                    .setConstraints(network).build();
            manager.enqueueUniquePeriodicWork("portal-monitor", ExistingPeriodicWorkPolicy.UPDATE, request);
        } else manager.cancelUniqueWork("portal-monitor");
        settings.putLong("interval", minutes);
        settings.putBool("monitoring", enabled);
    }
}
