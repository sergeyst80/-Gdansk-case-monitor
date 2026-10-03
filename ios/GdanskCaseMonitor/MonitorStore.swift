import Foundation
import Combine
import UserNotifications

@MainActor
final class MonitorStore: ObservableObject {
    static let shared = MonitorStore()
    @Published private(set) var vault = VaultState()
    @Published private(set) var settings = AppSettings.load()
    @Published private(set) var isChecking = false
    @Published private(set) var portalPhase = "action_idle"
    @Published private(set) var translationPhase = "action_translate"
    @Published private(set) var translations = 0
    @Published var message: String?
    private let storage = KeychainVault()
    private let session = PortalSession()
    private let translator = PortalTranslator()
    private var storageReady = false
    private var currentCheck: Task<Bool, Never>?
    private(set) var lastAutomaticCheck: Date?
    var action: String { isChecking ? portalPhase : translations > 0 ? translationPhase : "action_idle" }
    var busy: Bool { isChecking || translations > 0 }

    private init() { reloadStorage() }
    func text(_ key: String, _ args: CVarArg...) -> String {
        // Forward the formatted argument list without passing the array as one argument.
        let localized = settings.text(key)
        return args.isEmpty ? localized : String(format: localized, locale: Locale(identifier: settings.language), arguments: args)
    }
    func reloadStorage() {
        guard !isChecking else { return }
        do { vault = try storage.load(); storageReady = true }
        catch { storageReady = false; message = "storage_error" }
    }
    private func commit(_ newState: VaultState) throws {
        guard storageReady else { throw MonitorError.message("storage_error") }
        do { try storage.save(newState); vault = newState }
        catch { storageReady = false; message = "storage_error"; throw MonitorError.message("storage_error") }
    }
    func saveAccount(_ account: Account) throws {
        guard !isChecking else { throw MonitorError.message("checking") }
        guard !account.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !account.login.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !account.password.isEmpty else { throw MonitorError.message("required_fields") }
        var next = vault
        if let i = next.accounts.firstIndex(where: { $0.id == account.id }) { next.accounts[i] = account }
        else { next.accounts.append(account) }
        try commit(next)
    }
    func delete(_ account: Account) throws {
        guard !isChecking else { throw MonitorError.message("checking") }
        var next = vault; next.accounts.removeAll { $0.id == account.id }; next.statuses.removeValue(forKey: account.id.uuidString)
        try commit(next)
    }
    func updateSettings(_ next: AppSettings) {
        if isChecking && next.language != settings.language { message = "checking"; return }
        guard AppSettings.languages.contains(next.language), AppSettings.intervals.contains(next.interval) else { return }
        do {
            try next.save(); settings = next
            if next.autoRefresh { if !BackgroundScheduler.schedule(next) { message = "background_unavailable" } }
            else { BackgroundScheduler.cancel() }
        } catch { message = "storage_error" }
    }
    func check(_ account: Account? = nil, background: Bool = false) async -> Bool {
        guard !isChecking else { if !background { message = "checking" }; return false }
        guard storageReady else { message = "storage_error"; return false }
        let accounts = account.map { [$0] } ?? vault.accounts
        guard !accounts.isEmpty else { return true }
        isChecking = true; portalPhase = "action_start"
        if background { lastAutomaticCheck = Date() }
        session.progress = { [weak self] in self?.portalPhase = $0 }
        defer { isChecking = false; portalPhase = "action_idle"; session.progress = { _ in } }
        var success = true
        for account in accounts {
            if Task.isCancelled { return false }
            do {
                let snapshot = try await session.check(account)
                try Task.checkCancellation()
                portalPhase = "action_save"
                var next = vault
                let previous = next.statuses[account.id.uuidString]?.snapshot
                next.statuses[account.id.uuidString] = CaseStatus(snapshot: snapshot, lastSuccess: Date(), errorKey: nil)
                try commit(next)
                if background, let previous, previous.fingerprint != snapshot.fingerprint {
                    await notifyChange(account)
                }
            } catch is CancellationError { return false }
            catch {
                success = false
                var next = vault
                var status = next.statuses[account.id.uuidString] ?? CaseStatus()
                status.errorKey = (error as? MonitorError)?.key ?? "portal_network_error"
                next.statuses[account.id.uuidString] = status
                do { try commit(next) } catch { message = "storage_error"; return false }
            }
        }
        return success
    }
    func startCheck(_ account: Account? = nil) {
        guard !isChecking, currentCheck == nil else { message = "checking"; return }
        currentCheck = Task {
            let result = await check(account)
            currentCheck = nil
            return result
        }
    }
    func cancelCheck() { currentCheck?.cancel() }
    func translate(_ snapshot: Snapshot) async throws -> Snapshot {
        let language = settings.language
        translations += 1
        defer { translations = max(0, translations - 1) }
        return try await translator.translate(snapshot, target: language) { [weak self] in self?.translationPhase = $0 }
    }
    func requestNotifications() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }
    private func notifyChange(_ account: Account) async {
        let content = UNMutableNotificationContent()
        content.title = "Gdańsk Case Monitor"
        content.body = text("private_change")
        content.sound = .default
        let request = UNNotificationRequest(identifier: "case-" + account.id.uuidString, content: content, trigger: nil)
        try? await UNUserNotificationCenter.current().add(request)
    }
}
