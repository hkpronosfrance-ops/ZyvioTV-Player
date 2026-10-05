import Foundation
import Security

struct AuthSession: Codable {
    let accessToken: String
    let refreshToken: String
    let expiresAtEpochSeconds: Int64
}

final class AuthSessionStore {
    private let service = "fr.zyviotv.player"
    private let account = "supabase_session"

    func save(_ session: AuthSession) throws {
        let data = try JSONEncoder().encode(session)

        SecItemDelete(query() as CFDictionary)

        var attributes = query()
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly

        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError(status: status)
        }
    }

    func load() -> AuthSession? {
        var attributes = query()
        attributes[kSecReturnData as String] = true
        attributes[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(attributes as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else {
            return nil
        }

        return try? JSONDecoder().decode(AuthSession.self, from: data)
    }

    func clear() {
        SecItemDelete(query() as CFDictionary)
    }

    private func query() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    private struct KeychainError: Error {
        let status: OSStatus
    }
}
