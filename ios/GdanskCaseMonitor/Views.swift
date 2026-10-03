import SwiftUI

private let brand = Color(red: 0.03, green: 0.50, blue: 0.55)

@MainActor struct DashboardView: View {
    @EnvironmentObject private var store: MonitorStore
    @Environment(\.scenePhase) private var scenePhase
    private let timer = Timer.publish(every: 30, on: .main, in: .common).autoconnect()
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("klient.gdansk.uw.gov.pl").font(.caption).foregroundStyle(.secondary)
                    Text(store.settings.autoRefresh ? store.text("monitor_on", Int64(store.settings.interval)) : store.text("monitor_off"))
                        .font(.caption).foregroundStyle(.secondary)
                    Button { store.startCheck() } label: {
                        Label(store.text("refresh_all"), systemImage: "arrow.clockwise").frame(maxWidth: .infinity)
                    }.buttonStyle(.borderedProminent).controlSize(.large).disabled(store.isChecking)
                    if store.vault.accounts.isEmpty {
                        ContentUnavailableView(store.text("empty_users"), systemImage: "person.crop.circle.badge.plus",
                            description: Text(store.text("ios_empty_accounts")))
                    }
                    ForEach(store.vault.accounts) { account in
                        CaseCard(account: account, status: store.vault.statuses[account.id.uuidString])
                    }
                }.padding(20)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("Gdańsk Case Monitor").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        NavigationLink { UsersView() } label: { Label(store.text("users"), systemImage: "person.2") }
                        NavigationLink { SettingsView() } label: { Label(store.text("settings"), systemImage: "slider.horizontal.3") }
                    } label: { Image(systemName: "line.3.horizontal").accessibilityLabel(store.text("menu")) }
                }
            }
            .safeAreaInset(edge: .bottom) {
                HStack(spacing: 12) {
                    if store.busy { ProgressView().tint(.white) }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(store.text("action_label")).font(.caption).foregroundStyle(.white.opacity(0.7))
                        Text(store.text(store.action)).font(.subheadline).foregroundStyle(.white)
                    }
                    Spacer()
                }.padding(16).background(Color(red: 0.09, green: 0.17, blue: 0.23), in: RoundedRectangle(cornerRadius: 18))
                    .padding(.horizontal, 16).padding(.bottom, 8)
            }
            .alert(store.text("error_title"), isPresented: Binding(get: { store.message != nil }, set: { if !$0 { store.message = nil } })) {
                Button(store.text("close"), role: .cancel) { store.message = nil }
            } message: { Text(store.text(store.message ?? "unknown_error")) }
            .task { await store.requestNotifications() }
            .onReceive(timer) { _ in
                guard scenePhase == .active, store.settings.autoRefresh, !store.isChecking else { return }
                let previous = store.lastAutomaticCheck ?? .distantPast
                if Date().timeIntervalSince(previous) >= Double(store.settings.interval * 60) {
                    Task { await store.check(background: true) }
                }
            }
        }
    }
}

private struct TranslationIdentity: Hashable {
    let fingerprint: String
    let language: String
    let enabled: Bool
    let refreshed: Date?
}

@MainActor struct CaseCard: View {
    @EnvironmentObject private var store: MonitorStore
    let account: Account
    let status: CaseStatus?
    @State private var translated: Snapshot?
    @State private var original = false
    @State private var translationFailed = false
    private var identity: TranslationIdentity {
        TranslationIdentity(fingerprint: status?.snapshot?.fingerprint ?? "", language: store.settings.language,
            enabled: store.settings.translate, refreshed: status?.lastSuccess)
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(account.name).font(.title3.bold())
            Text(store.text("login", account.maskedLogin)).font(.caption).foregroundStyle(.secondary)
            if let snapshot = (original ? status?.snapshot : translated ?? status?.snapshot) {
                ForEach(Array(snapshot.fields.enumerated()), id: \.offset) { _, field in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(field.labelKey.map { store.text($0) } ?? (field.label.isEmpty ? store.text("portal_data") : field.label))
                            .font(.caption).foregroundStyle(.secondary)
                        Text(field.value).font(.body).foregroundStyle(field.key == "stage" ? brand : Color.primary).textSelection(.enabled)
                    }
                }
            } else { Text(store.text("not_checked")).foregroundStyle(.secondary) }
            if let error = status?.errorKey { Text(store.text(error)).font(.callout).foregroundStyle(.red) }
            Text(store.text("last_success", status?.lastSuccess?.formatted(date: .numeric, time: .standard) ?? "—"))
                .font(.caption2).foregroundStyle(.secondary)
            if store.settings.translate, status?.snapshot != nil {
                if translationFailed { Text(store.text("translation_error")).font(.caption).foregroundStyle(.secondary) }
                else if translated != nil {
                    Text(store.text("machine_translation")).font(.caption).foregroundStyle(.secondary)
                    Button(store.text(original ? "show_translation" : "show_original")) { original.toggle() }.buttonStyle(.bordered)
                }
            }
            Button { store.startCheck(account) } label: {
                Label(store.text("refresh"), systemImage: "arrow.clockwise").frame(maxWidth: .infinity)
            }.buttonStyle(.bordered).disabled(store.isChecking)
        }.frame(maxWidth: .infinity, alignment: .leading).padding(20)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 22))
            .task(id: identity) {
                translated = nil; original = false; translationFailed = false
                guard identity.enabled, let snapshot = status?.snapshot else { return }
                do {
                    let result = try await store.translate(snapshot)
                    try Task.checkCancellation(); translated = result
                } catch is CancellationError { }
                catch { if !Task.isCancelled { translationFailed = true } }
            }
    }
}

@MainActor struct UsersView: View {
    @EnvironmentObject private var store: MonitorStore
    @State private var editing: Account?
    @State private var deleting: Account?
    var body: some View {
        List {
            if store.vault.accounts.isEmpty { Text(store.text("empty_users")).foregroundStyle(.secondary) }
            ForEach(store.vault.accounts) { account in
                Button { editing = account } label: {
                    VStack(alignment: .leading) {
                        Text(account.name).foregroundStyle(.primary)
                        Text(store.text("login", account.maskedLogin)).font(.caption).foregroundStyle(.secondary)
                    }
                }.disabled(store.isChecking)
                    .swipeActions { Button(store.text("delete"), role: .destructive) { deleting = account }.disabled(store.isChecking) }
                    .contextMenu {
                        Button(store.text("edit")) { editing = account }.disabled(store.isChecking)
                        Button(store.text("delete"), role: .destructive) { deleting = account }.disabled(store.isChecking)
                    }
            }
        }.navigationTitle(store.text("users"))
            .toolbar { Button { editing = Account(name: "", login: "", password: "") } label: {
                Image(systemName: "plus").accessibilityLabel(store.text("add_user"))
            }.disabled(store.isChecking) }
            .sheet(item: $editing) { account in AccountEditor(account: account) }
            .confirmationDialog(store.text("delete_title", deleting?.name ?? ""),
                isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
                Button(store.text("delete"), role: .destructive) {
                    if let account = deleting {
                        do { try store.delete(account) } catch { store.message = (error as? MonitorError)?.key ?? "storage_error" }
                    }
                    deleting = nil
                }
            } message: { Text(store.text("delete_message")) }
    }
}

@MainActor struct AccountEditor: View {
    @EnvironmentObject private var store: MonitorStore
    @Environment(\.dismiss) private var dismiss
    @State var account: Account
    @State private var password = ""
    @State private var error: String?
    private var existing: Bool { store.vault.accounts.contains { $0.id == account.id } }
    var body: some View {
        NavigationStack {
            Form {
                TextField(store.text("name_hint"), text: $account.name).textContentType(.nickname)
                TextField(store.text("login_hint"), text: $account.login).textContentType(.username)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField(store.text(existing ? "new_password_hint" : "password_hint"), text: $password)
                    .textContentType(existing ? .newPassword : .password)
                if let error { Text(store.text(error)).foregroundStyle(.red) }
            }.navigationTitle(store.text(existing ? "edit_title" : "add_title"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(store.text("cancel")) { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(store.text("save")) {
                            var value = account
                            value.name = value.name.trimmingCharacters(in: .whitespacesAndNewlines)
                            value.login = value.login.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !password.isEmpty { value.password = password }
                            do { try store.saveAccount(value); dismiss() }
                            catch { self.error = (error as? MonitorError)?.key ?? "storage_error" }
                        }.disabled(store.isChecking)
                    }
                }
        }
    }
}

@MainActor struct SettingsView: View {
    @EnvironmentObject private var store: MonitorStore
    private func binding<Value>(_ key: WritableKeyPath<AppSettings, Value>) -> Binding<Value> {
        Binding(get: { store.settings[keyPath: key] }, set: { value in
            var next = store.settings; next[keyPath: key] = value; store.updateSettings(next)
        })
    }
    var body: some View {
        Form {
            Section {
                Picker(store.text("language"), selection: binding(\.language)) {
                    ForEach(Array(AppSettings.languages.enumerated()), id: \.element) { i, code in
                        Text(AppSettings.languageNames[i]).tag(code)
                    }
                }.disabled(store.isChecking)
            }
            Section {
                Toggle(store.text("auto_refresh"), isOn: binding(\.autoRefresh))
                Picker(store.text("interval"), selection: binding(\.interval)) {
                    ForEach(AppSettings.intervals, id: \.self) { Text(store.text("minutes", Int64($0))).tag($0) }
                }
            } footer: { Text(store.text("ios_background_hint")) }
            Section {
                Toggle(store.text("translate_portal"), isOn: binding(\.translate))
            } footer: { Text(store.text("translate_hint")) }
        }.navigationTitle(store.text("settings"))
    }
}
