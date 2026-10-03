import Foundation
import NaturalLanguage
import MLKitTranslate

@MainActor
final class PortalTranslator {
    private var cache: [String: Snapshot] = [:]
    private var running = false
    static let languages: [String: TranslateLanguage] = ["en": .english, "ru": .russian, "pl": .polish,
        "uk": .ukrainian, "de": .german, "fr": .french, "es": .spanish, "pt": .portuguese,
        "zh": .chinese, "ja": .japanese, "ar": .arabic, "hi": .hindi]

    func translate(_ snapshot: Snapshot, target: String, phase: @escaping (String) -> Void) async throws -> Snapshot {
        guard let targetLanguage = Self.languages[target] else { throw MonitorError.message("translation_error") }
        let cacheKey = target + snapshot.fingerprint
        if let cached = cache[cacheKey] { return cached }
        // A translator can occupy substantial memory. Process cards sequentially.
        while running { try await Task.sleep(nanoseconds: 50_000_000) }
        try Task.checkCancellation()
        running = true
        defer { running = false }
        let deadline = Date().addingTimeInterval(120)
        var result = snapshot
        var clients: [String: Translator] = [:]
        // Only permitted text fields are passed to Google. Credentials never enter this method.
        for index in result.fields.indices where result.fields[index].canTranslate {
            try Task.checkCancellation()
            guard Date() < deadline else { throw MonitorError.message("translation_error") }
            let text = result.fields[index].value
            guard text.count <= 10000 else { throw MonitorError.message("translation_error") }
            let recognizer = NLLanguageRecognizer(); recognizer.processString(text)
            let detected = String((recognizer.dominantLanguage?.rawValue ?? "pl").split(separator: "-")[0])
            let source = Self.languages[detected] ?? .polish
            if source == targetLanguage { continue }
            let client: Translator
            if let existing = clients[detected] { client = existing }
            else {
                client = Translator.translator(options: TranslatorOptions(sourceLanguage: source, targetLanguage: targetLanguage))
                clients[detected] = client
            }
            phase("action_models")
            let _: Bool = try await callbackValue(timeout: UInt64(max(1, deadline.timeIntervalSinceNow))) { completion in
                let conditions = ModelDownloadConditions(allowsCellularAccess: false, allowsBackgroundDownloading: true)
                client.downloadModelIfNeeded(with: conditions) { error in
                    if let error { completion(.failure(error)) } else { completion(.success(true)) }
                }
            }
            try Task.checkCancellation(); phase("action_translate")
            guard Date() < deadline else { throw MonitorError.message("translation_error") }
            let translated: String = try await callbackValue(timeout: UInt64(max(1, deadline.timeIntervalSinceNow))) { completion in
                client.translate(text) { value, error in
                    if let error { completion(.failure(error)) }
                    else if let value { completion(.success(value)) }
                    else { completion(.failure(MonitorError.message("translation_error"))) }
                }
            }
            result.fields[index].value = translated
        }
        if cache.count >= 16 { cache.removeAll() }
        cache[cacheKey] = result
        return result
    }
}
