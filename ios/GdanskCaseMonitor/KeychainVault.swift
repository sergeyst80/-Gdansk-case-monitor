import Foundation
import Security

struct KeychainVault {
    private let service = "pl.sergeyst.gdanskmonitor.ios.vault"
    private let account = "accounts-and-results"
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
         kSecAttrAccount as String: account, kSecAttrSynchronizable as String: false]
    }
    func load() throws -> VaultState {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var value: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &value)
        if status == errSecItemNotFound { return VaultState() }
        guard status == errSecSuccess, let data = value as? Data else { throw MonitorError.message("storage_error") }
        do { return try JSONDecoder().decode(VaultState.self, from: data) }
        catch { throw MonitorError.message("storage_error") }
    }
    func save(_ state: VaultState) throws {
        // Authenticate and decode the current item before replacing it.
        // A locked, inaccessible or malformed vault must never be treated as empty.
        _ = try load()
        let data = try JSONEncoder().encode(state)
        let attributes: [String: Any] = [kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        var status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw MonitorError.message("storage_error") }
    }
}
