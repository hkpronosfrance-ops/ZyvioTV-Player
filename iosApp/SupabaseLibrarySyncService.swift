import Foundation

struct SyncedFavoriteDTO: Codable, Identifiable {
    var id: String { playlistId + ":" + contentType + ":" + contentId }

    let playlistId: String
    let contentType: String
    let contentId: String
    let title: String
    let artworkUrl: String?

    enum CodingKeys: String, CodingKey {
        case playlistId = "playlist_id"
        case contentType = "content_type"
        case contentId = "content_id"
        case title
        case artworkUrl = "artwork_url"
    }
}

struct SyncedWatchProgressDTO: Codable, Identifiable {
    var id: String { playlistId + ":" + contentType + ":" + contentId }

    let playlistId: String
    let contentType: String
    let contentId: String
    let title: String
    let seriesId: String?
    let seasonNumber: Int?
    let episodeNumber: Int?
    let artworkUrl: String?
    let positionMs: Int64
    let durationMs: Int64?
    let completed: Bool

    enum CodingKeys: String, CodingKey {
        case playlistId = "playlist_id"
        case contentType = "content_type"
        case contentId = "content_id"
        case title
        case seriesId = "series_id"
        case seasonNumber = "season_number"
        case episodeNumber = "episode_number"
        case artworkUrl = "artwork_url"
        case positionMs = "position_ms"
        case durationMs = "duration_ms"
        case completed
    }
}

actor SupabaseLibrarySyncService {
    static let shared = SupabaseLibrarySyncService()

    private let baseURL = URL(string: "https://nvpuftuluguawdxonmlc.supabase.co")!
    private let publishableKey = "sb_publishable_Qr5CcSZRUsi1oATqvnJb_A_5cBla4SC"
    private let sessionStore = AuthSessionStore()

    func listFavorites() async throws -> [SyncedFavoriteDTO] {
        try await get(
            path: "/rest/v1/player_favorites?select=playlist_id,content_type,content_id,title,artwork_url&order=updated_at.desc"
        )
    }

    func listWatchProgress(limit: Int = 50) async throws -> [SyncedWatchProgressDTO] {
        let safeLimit = min(max(limit, 1), 200)
        return try await get(
            path: "/rest/v1/player_watch_progress?select=playlist_id,content_type,content_id,title,series_id,season_number,episode_number,artwork_url,position_ms,duration_ms,completed&order=last_watched_at.desc&limit=\(safeLimit)"
        )
    }

    func upsertFavorite(_ favorite: SyncedFavoriteDTO) async throws {
        let userId = try await currentUserId()
        let payload: [String: Any] = [
            "user_id": userId,
            "playlist_id": favorite.playlistId,
            "content_type": favorite.contentType,
            "content_id": favorite.contentId,
            "title": favorite.title,
            "artwork_url": jsonValue(favorite.artworkUrl),
            "updated_at": ISO8601DateFormatter().string(from: Date())
        ]
        try await mutate(
            path: "/rest/v1/player_favorites?on_conflict=user_id,playlist_id,content_type,content_id",
            method: "POST",
            payload: [payload],
            preferUpsert: true
        )
    }

    func removeFavorite(_ favorite: SyncedFavoriteDTO) async throws {
        try await mutate(
            path: "/rest/v1/player_favorites?playlist_id=eq.\(encoded(favorite.playlistId))&content_type=eq.\(encoded(favorite.contentType))&content_id=eq.\(encoded(favorite.contentId))",
            method: "DELETE"
        )
    }

    func upsertWatchProgress(_ progress: SyncedWatchProgressDTO) async throws {
        let userId = try await currentUserId()
        let now = ISO8601DateFormatter().string(from: Date())
        let payload: [String: Any] = [
            "user_id": userId,
            "playlist_id": progress.playlistId,
            "content_type": progress.contentType,
            "content_id": progress.contentId,
            "title": progress.title,
            "series_id": jsonValue(progress.seriesId),
            "season_number": jsonValue(progress.seasonNumber),
            "episode_number": jsonValue(progress.episodeNumber),
            "artwork_url": jsonValue(progress.artworkUrl),
            "position_ms": progress.positionMs,
            "duration_ms": jsonValue(progress.durationMs),
            "completed": progress.completed,
            "last_watched_at": now,
            "updated_at": now
        ]
        try await mutate(
            path: "/rest/v1/player_watch_progress?on_conflict=user_id,playlist_id,content_type,content_id",
            method: "POST",
            payload: [payload],
            preferUpsert: true
        )
    }

    func removeWatchProgress(_ progress: SyncedWatchProgressDTO) async throws {
        try await mutate(
            path: "/rest/v1/player_watch_progress?playlist_id=eq.\(encoded(progress.playlistId))&content_type=eq.\(encoded(progress.contentType))&content_id=eq.\(encoded(progress.contentId))",
            method: "DELETE"
        )
    }

    private func currentUserId() async throws -> String {
        struct CurrentUser: Decodable { let id: String }
        let user: CurrentUser = try await get(path: "/auth/v1/user")
        return user.id
    }

    private func mutate(
        path: String,
        method: String,
        payload: Any? = nil,
        preferUpsert: Bool = false
    ) async throws {
        guard let session = sessionStore.load() else {
            throw LibrarySyncError.noSession
        }
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw LibrarySyncError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        if preferUpsert {
            request.setValue("resolution=merge-duplicates,return=minimal", forHTTPHeaderField: "Prefer")
        }
        if let payload {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        }

        let (_, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw LibrarySyncError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw LibrarySyncError.server
        }
    }

    private func jsonValue<T>(_ value: T?) -> Any {
        if let value { return value }
        return NSNull()
    }

    private func encoded(_ value: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&=+?")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private func get<T: Decodable>(path: String) async throws -> T {
        guard let session = sessionStore.load() else {
            throw LibrarySyncError.noSession
        }
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw LibrarySyncError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw LibrarySyncError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw LibrarySyncError.server
        }

        return try JSONDecoder().decode(T.self, from: data)
    }

    enum LibrarySyncError: LocalizedError {
        case noSession
        case invalidURL
        case invalidResponse
        case server

        var errorDescription: String? {
            switch self {
            case .noSession:
                return "Session absente."
            case .invalidURL:
                return "Configuration serveur invalide."
            case .invalidResponse:
                return "Réponse serveur invalide."
            case .server:
                return "Impossible de synchroniser votre bibliothèque."
            }
        }
    }
}
