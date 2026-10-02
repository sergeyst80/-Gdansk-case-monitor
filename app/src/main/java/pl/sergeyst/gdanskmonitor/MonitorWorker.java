package pl.sergeyst.gdanskmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MonitorWorker extends Worker {
    private static final String CH_CHANGE = "changes";
    private final MonitorEngine[] holder = new MonitorEngine[1];

    public MonitorWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        Context context = I18n.context(getApplicationContext());
        Repository repo = new Repository(context);
        if (!repo.settings().getBool("monitoring", false)) return Result.success();
        try { repo.verifyReadable(); if (repo.accounts().isEmpty()) return Result.success(); }
        catch (StorageException e) { return Result.failure(); }

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_CHANGE, I18n.text(context, R.string.channel_changes), NotificationManager.IMPORTANCE_HIGH));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean(false);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (isStopped()) { latch.countDown(); return; }
                holder[0] = new MonitorEngine(context);
                holder[0].checkAll(new MonitorEngine.Listener() {
                    @Override public void accountDone(Account a, JSONObject status, boolean changed, String error) {
                        if (error != null) failed.set(true);
                        if (changed) notifyChange(context, a);
                    }
                    @Override public void allDone() {
                        try { if (holder[0] != null) holder[0].destroy(); } catch (Exception ignored) {}
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                failed.set(true);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(8, TimeUnit.MINUTES)) {
                new Handler(Looper.getMainLooper()).post(() -> { if (holder[0] != null) holder[0].destroy(); });
                return Result.retry();
            }
        } catch (InterruptedException e) {
            stopSession();
            Thread.currentThread().interrupt();
            return Result.retry();
        }
        return failed.get() ? Result.retry() : Result.success();
    }

    private static void notifyChange(Context context, Account a) {
        Notification n = new Notification.Builder(context, CH_CHANGE)
                .setContentTitle(I18n.text(context, R.string.app_name))
                .setContentText(I18n.text(context, R.string.private_change))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setAutoCancel(true)
                .build();
        context.getSystemService(NotificationManager.class)
                .notify(2000 + Math.abs(a.id.hashCode() % 100000), n);
    }

    @Override public void onStopped() {
        super.onStopped();
        // Interrupting Worker.doWork does not guarantee that its WebView stops.
        stopSession();
    }
    private void stopSession() {
        new Handler(Looper.getMainLooper()).post(() -> { if (holder[0] != null) holder[0].destroy(); });
    }
}
