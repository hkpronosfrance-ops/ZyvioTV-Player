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
