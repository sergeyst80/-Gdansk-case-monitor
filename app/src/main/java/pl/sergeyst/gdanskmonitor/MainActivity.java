package pl.sergeyst.gdanskmonitor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int INK = 0xFF172B3A, MUTED = 0xFF516879, TEAL = 0xFF087F8C;
    private Repository repo;
    private MonitorEngine foregroundEngine;
    private PortalTranslator translator;
    private LinearLayout list;
    private TextView monitoringText, actionText;
    private ProgressBar progress;
    private Button refresh;
    private int renderGeneration;
    private boolean observing;
    private boolean storageBlocked;
    private final Runnable statusObserver = this::renderAction;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceObserver = (prefs, key) -> {
        if ("statuses".equals(key) || "accounts".equals(key) || "monitoring".equals(key) || "interval".equals(key))
            render();
    };

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(I18n.context(base)); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repo = new Repository(this); foregroundEngine = new MonitorEngine(this); translator = new PortalTranslator();
        applyScreenProtection();
        buildUi(); render(); renderAction(); requestNotifications();
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(INK);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); return view;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? 0xFFFFFFFF : TEAL); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        b.setPadding(dp(16), dp(8), dp(16), dp(8)); b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22087F8C),
                shape(primary ? TEAL : 0xFFEAF4F5, 14), null));
        return b;
    }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private void gap(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }

    private void buildUi() {
        LinearLayout root = column(); root.setBackgroundColor(0xFFF2F6F8);
        root.setPadding(dp(20), dp(12), dp(20), dp(12));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            root.setPadding(dp(20) + left, dp(12) + top, dp(20) + right, dp(12) + bottom); return insets;
        });
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column();
        TextView title = text(getString(R.string.app_name), 23); title.setTypeface(Typeface.DEFAULT_BOLD); titles.addView(title);
        TextView sub = text("klient.gdansk.uw.gov.pl", 12); sub.setTextColor(MUTED); titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        Button menu = button("☰", false); menu.setContentDescription(getString(R.string.menu));
        menu.setOnClickListener(v -> openMenu(menu)); header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header); gap(root, 12);
        monitoringText = text("", 12); monitoringText.setTextColor(MUTED); root.addView(monitoringText);
        gap(root, 12);
        refresh = button(getString(R.string.refresh_all), true); refresh.setOnClickListener(v -> refreshAll());
        root.addView(refresh, full()); gap(root, 8);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        list = column(); scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); gap(root, 8);

        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBackground(shape(0xFF172B3A, 16)); footer.setPadding(dp(14), dp(12), dp(14), dp(12));
        progress = new ProgressBar(this); progress.setIndeterminateTintList(ColorStateList.valueOf(0xFF62D5C7));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(22), dp(22)); pp.setMarginEnd(dp(12)); footer.addView(progress, pp);
        LinearLayout actions = column();
        TextView caption = text(getString(R.string.action_label), 11); caption.setTextColor(0xFFB7CAD6); actions.addView(caption);
        actionText = text("", 14); actionText.setTextColor(0xFFFFFFFF); actionText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        actions.addView(actionText); footer.addView(actions, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(footer, full());
        setContentView(root); root.requestApplyInsets();
    }

    private void renderAction() {
        if (actionText == null || isDestroyed()) return;
        actionText.setText(getString(storageBlocked ? R.string.storage_unavailable : AppStatus.phase()));
        progress.setVisibility(AppStatus.busy() ? View.VISIBLE : View.GONE);
        refresh.setEnabled(!storageBlocked && !PortalWebSession.isBusy() && !foregroundEngine.isChecking());
        refresh.setAlpha(refresh.isEnabled() ? 1f : .55f);
    }
    private void render() {
        if (list == null || isDestroyed()) return;
        final int generation = ++renderGeneration;
        translator.cancelPending(); list.removeAllViews();
        List<Account> accounts; JSONObject statuses;
        try { accounts = repo.accounts(); statuses = repo.statuses(); storageBlocked = false; }
        catch (StorageException e) {
            storageBlocked = true;
            list.addView(text(getString(R.string.storage_error), 15)); renderAction(); return;
        }
        monitoringText.setText(repo.settings().getBool("monitoring", false)
                ? getString(R.string.monitor_on, MonitorSchedule.interval(this)) : getString(R.string.monitor_off));
        if (accounts.isEmpty()) {
            LinearLayout empty = column(); empty.setPadding(dp(20), dp(28), dp(20), dp(28)); empty.setBackgroundResource(R.drawable.card_bg);
            TextView title = text(getString(R.string.empty_users), 20); title.setTypeface(Typeface.DEFAULT_BOLD); empty.addView(title);
            gap(empty, 12); empty.addView(text(getString(R.string.empty_accounts), 14));
            gap(empty, 20); Button add = button(getString(R.string.add_user), true); add.setOnClickListener(v -> editDialog(null)); empty.addView(add);
            list.addView(empty, full());
        } else for (Account account : accounts) list.addView(card(account, statuses.optJSONObject(account.id), generation));
        renderAction();
    }

    private View card(Account account, JSONObject status, int generation) {
        LinearLayout card = column(); card.setBackgroundResource(R.drawable.card_bg);
        LinearLayout.LayoutParams params = full(); params.setMargins(0, dp(6), 0, dp(10)); card.setLayoutParams(params);
        card.setElevation(dp(1));
        TextView name = text(account.name, 20); name.setTypeface(Typeface.DEFAULT_BOLD); card.addView(name);
        TextView login = text(getString(R.string.login, mask(account.login)), 12); login.setTextColor(MUTED); card.addView(login);
        gap(card, 18);
        JSONObject snapshot = status == null ? null : status.optJSONObject("snapshot");
        LinearLayout values = column(); card.addView(values, full()); showFields(values, snapshot);
        if (snapshot == null) {
            values.removeAllViews();
            String error = status == null ? "" : status.optString("lastError");
            values.addView(text(error.isEmpty() ? getString(R.string.not_checked) : I18n.error(this, error), 14));
        }
        if (status != null && snapshot != null && !status.optString("lastError").isEmpty()) {
            gap(card, 12); TextView error = text(getString(R.string.error, I18n.error(this, status.optString("lastError"))), 13);
            error.setTextColor(0xFFAF3030); card.addView(error);
        }
        if (status != null) {
            gap(card, 14);
            TextView last = text(getString(R.string.last_success, status.optString("lastSuccess", "—")), 11);
            last.setTextColor(MUTED); card.addView(last);
        }
        if (snapshot != null && repo.settings().getBool("translate_portal", false)) {
            gap(card, 12);
            TextView translationNote = text(getString(R.string.action_translate), 11); translationNote.setTextColor(MUTED); card.addView(translationNote);
            Button original = button(getString(R.string.show_original), false); original.setVisibility(View.GONE); card.addView(original, full());
            translator.translate(snapshot, I18n.language(this), (translated, failed) -> {
                if (generation != renderGeneration || isDestroyed()) return;
                translationNote.setText(getString(failed ? R.string.translation_error : R.string.machine_translation));
                if (failed) return;
                showFields(values, translated); original.setVisibility(View.VISIBLE);
                final boolean[] showingOriginal = {false};
                original.setOnClickListener(v -> {
                    showingOriginal[0] = !showingOriginal[0];
                    showFields(values, showingOriginal[0] ? snapshot : translated);
                    original.setText(showingOriginal[0] ? R.string.show_translation : R.string.show_original);
                });
            });
        }
        gap(card, 14);
        Button update = button(getString(R.string.refresh), false); update.setOnClickListener(v -> refreshOne(account)); card.addView(update, full());
        return card;
    }

    private void showFields(LinearLayout box, JSONObject snapshot) {
        box.removeAllViews(); if (snapshot == null) return;
        JSONArray fields = snapshot.optJSONArray("fields");
        if (fields != null && fields.length() > 0) {
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.optJSONObject(i); if (field == null) continue;
                if (i > 0) gap(box, 12);
                TextView label = text(MonitorEngine.displayLabel(this, field, fields, i), 11); label.setTextColor(MUTED); box.addView(label);
                TextView value = text(field.optString("value", "—"), 15); value.setTextIsSelectable(true);
                if ("stage".equals(MonitorEngine.fieldKey(fields, i))) { value.setTextColor(TEAL); value.setTypeface(Typeface.DEFAULT_BOLD); }
                box.addView(value);
            }
        } else {
            JSONObject status = new JSONObject(); try { status.put("snapshot", snapshot); } catch (Exception ignored) {}
            TextView fallback = text(MonitorEngine.summary(this, status), 14); fallback.setTextIsSelectable(true); box.addView(fallback);
        }
    }
    private String mask(String value) { return value.length() <= 4 ? "••••" : value.substring(0, 2) + "••••" + value.substring(value.length() - 2); }
    private boolean editingBlocked() {
        try { repo.verifyReadable(); storageBlocked = false; }
        catch (StorageException e) { storageBlocked = true; toast(getString(R.string.storage_error)); renderAction(); return true; }
        if (foregroundEngine.isChecking() || PortalWebSession.isBusy()) { toast(getString(R.string.checking)); return true; }
        return false;
    }
    private void openMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.users);
        menu.getMenu().add(0, 2, 1, R.string.settings);
        menu.setOnMenuItemClickListener(item -> { if (item.getItemId() == 1) usersDialog(); else settingsDialog(); return true; });
        menu.show();
    }
    private void usersDialog() {
        if (editingBlocked()) return;
        List<Account> users = repo.accounts();
        String[] names = new String[users.size()];
        for (int i = 0; i < users.size(); i++) names[i] = users.get(i).name;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(R.string.users)
                .setPositiveButton(R.string.add_user, (d, which) -> editDialog(null)).setNegativeButton(R.string.cancel, null);
        if (users.isEmpty()) dialog.setMessage(R.string.empty_users);
        else dialog.setItems(names, (d, which) -> {
            Account account = users.get(which);
            showSecure(new AlertDialog.Builder(this).setTitle(account.name)
                    .setItems(new String[]{getString(R.string.edit), getString(R.string.delete)},
                            (choice, action) -> { if (action == 0) editDialog(account); else delete(account); }).create());
        });
        showSecure(dialog.create());
    }

    private void editDialog(Account existing) {
        if (editingBlocked()) return;
        LinearLayout box = column(); box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText name = new EditText(this); name.setHint(R.string.name_hint);
        EditText login = new EditText(this); login.setHint(R.string.login_hint);
        login.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText pass = new EditText(this); pass.setHint(existing == null ? R.string.password_hint : R.string.new_password_hint);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (existing != null) { name.setText(existing.name); login.setText(existing.login); }
        box.addView(name); box.addView(login); box.addView(pass);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(existing == null ? R.string.add_title : R.string.edit_title)
                .setView(box).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (editingBlocked()) return;
            String n = name.getText().toString().trim(), l = login.getText().toString().trim(), p = pass.getText().toString();
            if (n.isEmpty() || l.isEmpty() || (existing == null && p.isEmpty())) { toast(getString(R.string.required_fields)); return; }
            try {
                List<Account> users = new ArrayList<>(repo.accounts());
                if (existing == null) users.add(Account.create(n, l, p));
                else for (Account a : users) if (a.id.equals(existing.id)) { a.name = n; a.login = l; if (!p.isEmpty()) a.password = p; }
                repo.saveAccounts(users); dialog.dismiss(); render();
            } catch (Exception e) { toast(getString(R.string.save_error, getString(R.string.unknown_error))); }
        })); showSecure(dialog);
    }
    private void delete(Account account) {
        if (editingBlocked()) return;
        showSecure(new AlertDialog.Builder(this).setTitle(getString(R.string.delete_title, account.name)).setMessage(R.string.delete_message)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (d, w) -> {
                    if (editingBlocked()) return;
                    try {
                        List<Account> users = new ArrayList<>(repo.accounts()); users.removeIf(a -> a.id.equals(account.id)); repo.saveAccounts(users);
                        JSONObject statuses = repo.statuses(); statuses.remove(account.id); repo.saveStatuses(statuses); render();
                    } catch (Exception e) { toast(getString(R.string.unknown_error)); }
                }).create());
    }

    private void settingsDialog() {
        LinearLayout box = column(); box.setPadding(dp(20), dp(8), dp(20), dp(16));
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        Button language = button(getString(R.string.language) + " · " + I18n.NAMES[java.util.Arrays.asList(I18n.CODES).indexOf(I18n.language(this))], false);
        box.addView(language, full()); gap(box, 18);
        Switch auto = new Switch(this); auto.setText(R.string.auto_refresh); auto.setTextColor(INK); auto.setMinHeight(dp(48));
        auto.setChecked(repo.settings().getBool("monitoring", false)); box.addView(auto, full()); gap(box, 8);
        Button interval = button(getString(R.string.interval) + " · " + getString(R.string.minutes, MonitorSchedule.interval(this)), false);
        box.addView(interval, full()); gap(box, 8);
        TextView intervalHint = text(getString(R.string.interval_hint), 12); intervalHint.setTextColor(MUTED); box.addView(intervalHint); gap(box, 18);
        Switch translate = new Switch(this); translate.setText(R.string.translate_portal); translate.setTextColor(INK); translate.setMinHeight(dp(48));
        translate.setChecked(repo.settings().getBool("translate_portal", false)); box.addView(translate, full()); gap(box, 8);
        TextView privacy = text(getString(R.string.translate_hint), 12); privacy.setTextColor(MUTED); box.addView(privacy);
        gap(box, 18);
        Switch protect = new Switch(this); protect.setText(R.string.protect_screen); protect.setTextColor(INK);
        protect.setChecked(repo.settings().getBool("protect_screen", false)); box.addView(protect, full());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.settings).setView(scroll).setNegativeButton(R.string.cancel, null).create();
        language.setOnClickListener(v -> { dialog.dismiss(); languageDialog(); });
        interval.setOnClickListener(v -> intervalDialog(interval));
        auto.setOnCheckedChangeListener((v, enabled) -> {
            MonitorSchedule.configure(this, enabled, MonitorSchedule.interval(this)); render();
            toast(getString(enabled ? R.string.monitor_started : R.string.monitor_stopped));
        });
        translate.setOnCheckedChangeListener((v, enabled) -> {
            repo.settings().putBool("translate_portal", enabled); translator.retryFailures(); render(); toast(getString(R.string.settings_saved));
        });
        showSecure(dialog);
        protect.setOnCheckedChangeListener((v, enabled) -> {
            repo.settings().putBool("protect_screen", enabled); applyScreenProtection();
            if (enabled) dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            else dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        });
    }

    private void applyScreenProtection() {
        if (repo.settings().getBool("protect_screen", false)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }
    private void showSecure(AlertDialog dialog) {
        if (repo.settings().getBool("protect_screen", false)) dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }

    private void intervalDialog(Button label) {
        String[] options = new String[MonitorSchedule.INTERVALS.length]; int selected = -1;
        for (int i = 0; i < options.length; i++) {
            options[i] = getString(R.string.minutes, MonitorSchedule.INTERVALS[i]);
            if (MonitorSchedule.INTERVALS[i] == MonitorSchedule.interval(this)) selected = i;
        }
        showSecure(new AlertDialog.Builder(this).setTitle(R.string.interval).setSingleChoiceItems(options, selected, (dialog, which) -> {
            MonitorSchedule.configure(this, repo.settings().getBool("monitoring", false), MonitorSchedule.INTERVALS[which]);
            label.setText(getString(R.string.interval) + " · " + options[which]); dialog.dismiss(); render(); toast(getString(R.string.settings_saved));
        }).setNegativeButton(R.string.cancel, null).create());
    }
    private void languageDialog() {
        int selected = java.util.Arrays.asList(I18n.CODES).indexOf(I18n.language(this));
        showSecure(new AlertDialog.Builder(this).setTitle(getString(R.string.language) + " / Language")
                .setSingleChoiceItems(I18n.NAMES, selected, (dialog, which) -> {
                    String code = I18n.CODES[which];
                    if (!code.equals(I18n.language(this))) {
                        if (editingBlocked()) { dialog.dismiss(); return; }
                        I18n.setLanguage(this, code); dialog.dismiss(); recreate();
                    } else dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).create());
    }

    private void refreshAll() {
        if (editingBlocked()) return;
        translator.retryFailures(); foregroundEngine.checkAll(listener()); renderAction();
    }
    private void refreshOne(Account account) {
        if (editingBlocked()) return;
        translator.retryFailures(); foregroundEngine.checkOne(account, listener()); renderAction();
    }
    private MonitorEngine.Listener listener() {
        return new MonitorEngine.Listener() {
            public void accountDone(Account account, JSONObject status, boolean changed, String error) {}
            public void allDone() { runOnUiThread(() -> { render(); toast(getString(R.string.check_done)); }); }
        };
    }
    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    @Override protected void onStart() {
        super.onStart();
        if (!observing) {
            AppStatus.observe(statusObserver); getSharedPreferences("secure_store", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferenceObserver);
            observing = true;
        }
        render(); renderAction();
    }
    @Override protected void onStop() {
        if (observing) {
            AppStatus.remove(statusObserver); getSharedPreferences("secure_store", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceObserver); observing = false;
        }
        super.onStop();
    }
    @Override protected void onDestroy() {
        renderGeneration++; if (translator != null) translator.close();
        if (foregroundEngine != null) foregroundEngine.destroy(); super.onDestroy();
    }
}
