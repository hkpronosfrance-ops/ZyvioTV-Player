import Foundation

actor SupabaseAuthService {
    static let shared = SupabaseAuthService()

    private let baseURL = URL(string: "https://nvpuftuluguawdxonmlc.supabase.co")!
    private let publishableKey = "sb_publishable_Qr5CcSZRUsi1oATqvnJb_A_5cBla4SC"
    private let sessionStore = AuthSessionStore()

    var hasStoredSession: Bool {
        sessionStore.load() != nil
    }

    func signIn(email: String, password: String) async throws {
        let body = [
            "email": email.trimmingCharacters(in: .whitespacesAndNewlines),
            "password": password,
        ]

        let response = try await request(
            path: "/auth/v1/token?grant_type=password",
            method: "POST",
            body: body
        )

        try persistSession(from: response)
    }

    func signUp(email: String, password: String) async throws {
        let body = [
            "email": email.trimmingCharacters(in: .whitespacesAndNewlines),
            "password": password,
        ]

        let response = try await request(
            path: "/auth/v1/signup",
            method: "POST",
            body: body
        )

        if response["access_token"] != nil {
            try persistSession(from: response)
        }
    }

    func requestPasswordReset(email: String) async throws {
        _ = try await request(
            path: "/auth/v1/recover",
            method: "POST",
            body: ["email": email.trimmingCharacters(in: .whitespacesAndNewlines)]
        )
    }

    func signOut() async {
        guard let session = sessionStore.load() else {
            sessionStore.clear()
            return
        }

        _ = try? await request(
            path: "/auth/v1/logout",
            method: "POST",
            body: [:],
            bearerToken: session.accessToken
        )

        sessionStore.clear()
    }

    private func request(
        path: String,
        method: String,
        body: [String: String],
        bearerToken: String? = nil
    ) async throws -> [String: Any] {
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw AuthServiceError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue(
            "Bearer \(bearerToken ?? publishableKey)",
            forHTTPHeaderField: "Authorization"
        )
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthServiceError.invalidResponse
        }

        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]

        guard (200...299).contains(httpResponse.statusCode) else {
            let message =
                json["msg"] as? String
                ?? json["message"] as? String
                ?? json["error_description"] as? String
                ?? "Une erreur est survenue. Réessayez."
            throw AuthServiceError.server(message)
        }

        return json
    }

    private func persistSession(from json: [String: Any]) throws {
        guard
            let accessToken = json["access_token"] as? String,
            let refreshToken = json["refresh_token"] as? String
        else {
            return
        }

        let expiresIn = (json["expires_in"] as? NSNumber)?.int64Value ?? 3600
        let now = Int64(Date().timeIntervalSince1970)

        try sessionStore.save(
            AuthSession(
                accessToken: accessToken,
                refreshToken: refreshToken,
                expiresAtEpochSeconds: now + expiresIn
            )
        )
    }

    enum AuthServiceError: LocalizedError {
        case invalidURL
        case invalidResponse
        case server(String)

        var errorDescription: String? {
            switch self {
            case .invalidURL:
                return "Configuration serveur invalide."
            case .invalidResponse:
                return "Réponse serveur invalide."
            case .server(let message):
                return message
            }
        }
    }
}
