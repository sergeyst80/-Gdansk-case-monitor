import Foundation
import CryptoKit

struct Account: Codable, Identifiable, Equatable {
    var id = UUID()
    var name: String
    var login: String
    var password: String
    var maskedLogin: String {
        login.count > 4 ? String(login.prefix(2)) + "••••" + String(login.suffix(2)) : "••••"
    }
}

struct PortalField: Codable, Identifiable, Equatable {
    var key: String
    var label: String
    var value: String
    var id: String { key + "|" + label }
    var canTranslate: Bool { ["stage", "stageDescription", "notes", "documents"].contains(key) }
    var labelKey: String? {
        ["name": "name", "caseNumber": "case_number", "filedDate": "filed_date", "stage": "stage",
         "stageDescription": "stage_description", "notes": "notes", "documents": "documents"][key]
    }
}

struct Snapshot: Codable, Equatable {
    var fields: [PortalField]
    var lines: [String]
    var fingerprint: String {
        // Display labels and translations never participate in case-change detection.
        let values = fields.isEmpty ? lines : fields.map(\.value)
        let data = (try? JSONEncoder().encode(values)) ?? Data()
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}

struct CaseStatus: Codable {
    var snapshot: Snapshot?
    var lastSuccess: Date?
    var errorKey: String?
}

struct VaultState: Codable {
    var accounts: [Account] = []
    var statuses: [String: CaseStatus] = [:]
}

struct AppSettings: Codable, Equatable {
    var language = "en"
    var autoRefresh = false
    var interval = 30
    var translate = false
    static let intervals = [15, 30, 60, 180, 360, 720, 1440]
    static let languages = ["en", "ru", "pl", "uk", "de", "fr", "es", "pt", "zh", "ja", "ar", "hi"]
    static let languageNames = ["English", "Русский", "Polski", "Українська", "Deutsch", "Français", "Español", "Português", "中文", "日本語", "العربية", "हिन्दी"]
    static func load() -> AppSettings {
        guard let data = UserDefaults.standard.data(forKey: "appSettings"),
              var result = try? JSONDecoder().decode(AppSettings.self, from: data) else { return AppSettings() }
        if !languages.contains(result.language) { result.language = "en" }
        if !intervals.contains(result.interval) { result.interval = 30 }
        return result
    }
    func save() throws { UserDefaults.standard.set(try JSONEncoder().encode(self), forKey: "appSettings") }
    func text(_ key: String, _ arguments: CVarArg...) -> String {
        let path = Bundle.main.path(forResource: language, ofType: "lproj")
        let bundle = path.flatMap(Bundle.init(path:)) ?? Bundle.main
        let english = Bundle.main.path(forResource: "en", ofType: "lproj").flatMap(Bundle.init(path:))
        let fallback = english?.localizedString(forKey: key, value: key, table: nil) ?? key
        let format = bundle.localizedString(forKey: key, value: fallback, table: nil)
        return arguments.isEmpty ? format : String(format: format, locale: Locale(identifier: language), arguments: arguments)
    }
}

enum MonitorError: Error {
    case message(String)
    var key: String { if case let .message(key) = self { return key }; return "unknown_error" }
}
