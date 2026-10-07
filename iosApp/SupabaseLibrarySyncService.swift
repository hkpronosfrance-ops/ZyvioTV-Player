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

struct PlayerProfileDTO: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let avatarKey: String
    let profileType: String
    let maxAge: Int?
    let isPrimary: Bool

    var isChild: Bool { profileType == "child" }

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case avatarKey = "avatar_key"
        case profileType = "profile_type"
        case maxAge = "max_age"
        case isPrimary = "is_primary"
    }
}

final class PlayerProfileSelectionStore {
    static let shared = PlayerProfileSelectionStore()

    private let defaults = UserDefaults.standard
    private let key = "zyviotv.active_profile_id"

    var activeProfileId: String? {
        defaults.string(forKey: key)
    }

    func select(profileId: String) {
        defaults.set(profileId, forKey: key)
    }

    func clear() {
        defaults.removeObject(forKey: key)
    }
}

actor SupabaseProfileService {
    static let shared = SupabaseProfileService()

    private let baseURL = URL(string: "https://nvpuftuluguawdxonmlc.supabase.co")!
    private let publishableKey = "sb_publishable_Qr5CcSZRUsi1oATqvnJb_A_5cBla4SC"
    private let sessionStore = AuthSessionStore()

    func ensurePrimaryProfile() async throws -> String {
        let data = try await request(
            path: "/rest/v1/rpc/player_ensure_primary_profile",
            method: "POST",
            payload: [:]
        )
        if let value = try? JSONDecoder().decode(String.self, from: data) {
            return value
        }
        guard let value = String(data: data, encoding: .utf8)?
            .trimmingCharacters(in: CharacterSet(charactersIn: "\"\n\r "))
            .nilIfBlank
        else {
            throw ProfileError.invalidResponse
        }
        return value
    }

    func listProfiles() async throws -> [PlayerProfileDTO] {
        try await get(
            path: "/rest/v1/player_profiles?select=id,name,avatar_key,profile_type,max_age,is_primary&order=is_primary.desc,created_at.asc"
        )
    }

    func resolveActiveProfile() async throws -> PlayerProfileDTO {
        _ = try await ensurePrimaryProfile()
        let profiles = try await listProfiles()
        guard !profiles.isEmpty else {
            throw ProfileError.noProfile
        }

        if let storedId = PlayerProfileSelectionStore.shared.activeProfileId,
           let stored = profiles.first(where: { $0.id == storedId }) {
            return stored
        }

        let fallback = profiles.first(where: { $0.isPrimary }) ?? profiles[0]
        PlayerProfileSelectionStore.shared.select(profileId: fallback.id)
        return fallback
    }

    private func request(
        path: String,
        method: String,
        payload: Any? = nil
    ) async throws -> Data {
        guard let session = sessionStore.load() else {
            throw ProfileError.noSession
        }
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw ProfileError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        if let payload {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ProfileError.invalidResponse
        }
        guard (200...299).contains(http.statusCode) else {
            throw ProfileError.server
        }
        return data
    }

    private func get<T: Decodable>(path: String) async throws -> T {
        let data = try await request(path: path, method: "GET")
        return try JSONDecoder().decode(T.self, from: data)
    }

    enum ProfileError: LocalizedError {
        case noSession
        case noProfile
        case invalidURL
        case invalidResponse
        case server

        var errorDescription: String? {
            switch self {
            case .noSession:
                return "Session absente."
            case .noProfile:
                return "Aucun profil disponible."
            case .invalidURL:
                return "Configuration serveur invalide."
            case .invalidResponse:
                return "Réponse serveur invalide."
            case .server:
                return "Impossible de charger les profils."
            }
        }
    }
}

actor SupabaseLibrarySyncService {
    static let shared = SupabaseLibrarySyncService()

    private let baseURL = URL(string: "https://nvpuftuluguawdxonmlc.supabase.co")!
    private let publishableKey = "sb_publishable_Qr5CcSZRUsi1oATqvnJb_A_5cBla4SC"
    private let sessionStore = AuthSessionStore()

    func listFavorites() async throws -> [SyncedFavoriteDTO] {
        let profileId = try activeProfileId()
        return try await get(
            path: "/rest/v1/player_favorites?profile_id=eq.\(encoded(profileId))&select=playlist_id,content_type,content_id,title,artwork_url&order=updated_at.desc"
        )
    }

    func listWatchProgress(limit: Int = 50) async throws -> [SyncedWatchProgressDTO] {
        let profileId = try activeProfileId()
        let safeLimit = min(max(limit, 1), 200)
        return try await get(
            path: "/rest/v1/player_watch_progress?profile_id=eq.\(encoded(profileId))&select=playlist_id,content_type,content_id,title,series_id,season_number,episode_number,artwork_url,position_ms,duration_ms,completed&order=last_watched_at.desc&limit=\(safeLimit)"
        )
    }

    func upsertFavorite(_ favorite: SyncedFavoriteDTO) async throws {
        let userId = try await currentUserId()
        let profileId = try activeProfileId()
        let payload: [String: Any] = [
            "user_id": userId,
            "profile_id": profileId,
            "playlist_id": favorite.playlistId,
            "content_type": favorite.contentType,
            "content_id": favorite.contentId,
            "title": favorite.title,
            "artwork_url": jsonValue(favorite.artworkUrl),
            "updated_at": ISO8601DateFormatter().string(from: Date())
        ]
        try await mutate(
            path: "/rest/v1/player_favorites?on_conflict=user_id,profile_id,playlist_id,content_type,content_id",
            method: "POST",
            payload: [payload],
            preferUpsert: true
        )
    }

    func removeFavorite(_ favorite: SyncedFavoriteDTO) async throws {
        let profileId = try activeProfileId()
        try await mutate(
            path: "/rest/v1/player_favorites?profile_id=eq.\(encoded(profileId))&playlist_id=eq.\(encoded(favorite.playlistId))&content_type=eq.\(encoded(favorite.contentType))&content_id=eq.\(encoded(favorite.contentId))",
            method: "DELETE"
        )
    }

    func upsertWatchProgress(_ progress: SyncedWatchProgressDTO) async throws {
        let userId = try await currentUserId()
        let profileId = try activeProfileId()
        let now = ISO8601DateFormatter().string(from: Date())
        let payload: [String: Any] = [
            "user_id": userId,
            "profile_id": profileId,
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
            path: "/rest/v1/player_watch_progress?on_conflict=user_id,profile_id,playlist_id,content_type,content_id",
            method: "POST",
            payload: [payload],
            preferUpsert: true
        )
    }

    func removeWatchProgress(_ progress: SyncedWatchProgressDTO) async throws {
        let profileId = try activeProfileId()
        try await mutate(
            path: "/rest/v1/player_watch_progress?profile_id=eq.\(encoded(profileId))&playlist_id=eq.\(encoded(progress.playlistId))&content_type=eq.\(encoded(progress.contentType))&content_id=eq.\(encoded(progress.contentId))",
            method: "DELETE"
        )
    }

    private func currentUserId() async throws -> String {
        struct CurrentUser: Decodable { let id: String }
        let user: CurrentUser = try await get(path: "/auth/v1/user")
        return user.id
    }

    private func activeProfileId() throws -> String {
        guard let profileId = PlayerProfileSelectionStore.shared.activeProfileId else {
            throw LibrarySyncError.noProfile
        }
        return profileId
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
        case noProfile
        case invalidURL
        case invalidResponse
        case server

        var errorDescription: String? {
            switch self {
            case .noSession:
                return "Session absente."
            case .noProfile:
                return "Aucun profil actif."
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


struct ProviderPlaylistDTO: Decodable, Identifiable {
    let id: String
    let name: String
    let providerType: String
    let serverHost: String?
    let playlistUrlHint: String?
    let secretStatus: String
    let isEnabled: Bool
    let priority: Int

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case providerType = "provider_type"
        case serverHost = "server_host"
        case playlistUrlHint = "playlist_url_hint"
        case secretStatus = "secret_status"
        case isEnabled = "is_enabled"
        case priority
    }
}


enum ApplePlaylistSecret {
    case xtream(serverURL: String, username: String, password: String)
    case m3u(url: String, xmlTvURL: String?)
}

actor SupabasePlaylistService {
    static let shared = SupabasePlaylistService()

    private let baseURL = URL(string: "https://nvpuftuluguawdxonmlc.supabase.co")!
    private let publishableKey = "sb_publishable_Qr5CcSZRUsi1oATqvnJb_A_5cBla4SC"
    private let sessionStore = AuthSessionStore()

    func listPlaylists() async throws -> [ProviderPlaylistDTO] {
        try await get(
            path: "/rest/v1/player_playlists?select=id,name,provider_type,server_host,playlist_url_hint,secret_status,is_enabled,priority&order=priority.asc,updated_at.desc"
        )
    }

    func testXtream(serverURL: String, username: String, password: String) async throws {
        guard
            var components = URLComponents(string: serverURL.trimmingCharacters(in: .whitespacesAndNewlines))
        else { throw PlaylistError.invalidURL }

        var path = components.path
        if !path.hasSuffix("/") { path += "/" }
        path += "player_api.php"
        components.path = path
        components.queryItems = [
            URLQueryItem(name: "username", value: username.trimmingCharacters(in: .whitespacesAndNewlines)),
            URLQueryItem(name: "password", value: password),
        ]
        guard let url = components.url else { throw PlaylistError.invalidURL }

        var request = URLRequest(url: url)
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("ZYVIOTV-Player/0.1", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard
            let http = response as? HTTPURLResponse,
            (200...299).contains(http.statusCode),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let userInfo = object["user_info"] as? [String: Any]
        else { throw PlaylistError.providerUnavailable }

        let authValue = userInfo["auth"]
        let authenticated =
            (authValue as? Int) == 1 ||
            (authValue as? NSNumber)?.intValue == 1 ||
            (authValue as? String) == "1"

        guard authenticated else { throw PlaylistError.invalidCredentials }
    }

    func testM3u(urlString: String) async throws {
        guard
            let url = URL(string: urlString.trimmingCharacters(in: .whitespacesAndNewlines)),
            ["http", "https"].contains(url.scheme?.lowercased() ?? "")
        else { throw PlaylistError.invalidURL }

        var request = URLRequest(url: url)
        request.timeoutInterval = 15
        request.setValue("bytes=0-262143", forHTTPHeaderField: "Range")
        request.setValue("ZYVIOTV-Player/0.1", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard
            let http = response as? HTTPURLResponse,
            (200...299).contains(http.statusCode) || http.statusCode == 206,
            let prefix = String(data: data.prefix(262_144), encoding: .utf8),
            prefix.uppercased().contains("#EXTM3U")
        else { throw PlaylistError.invalidM3u }
    }

    func createPlaylist(
        name: String,
        providerType: String,
        serverHost: String?,
        playlistUrlHint: String?
    ) async throws -> String {
        let playlists = try await listPlaylists()
        guard playlists.count < 10 else { throw PlaylistError.limitReached }
        let userId = try await currentUserId()
        let priority = min((playlists.map(\.priority).max() ?? 0) + 1, 10)
        let now = ISO8601DateFormatter().string(from: Date())

        let body: [[String: Any]] = [[
            "user_id": userId,
            "name": name.trimmingCharacters(in: .whitespacesAndNewlines),
            "provider_type": providerType,
            "server_host": serverHost ?? NSNull(),
            "playlist_url_hint": playlistUrlHint ?? NSNull(),
            "secret_status": "not_configured",
            "is_enabled": true,
            "priority": priority,
            "updated_at": now,
        ]]

        let data = try await request(
            path: "/rest/v1/player_playlists",
            method: "POST",
            payload: body,
            prefer: "return=representation"
        )
        struct Created: Decodable { let id: String }
        guard let first = try JSONDecoder().decode([Created].self, from: data).first else {
            throw PlaylistError.invalidResponse
        }
        return first.id
    }

    func setSecret(playlistId: String, secret: ApplePlaylistSecret) async throws {
        let secretBody: [String: Any]
        switch secret {
        case let .xtream(serverURL, username, password):
            secretBody = [
                "provider_type": "xtream",
                "server_url": serverURL,
                "username": username,
                "password": password,
            ]
        case let .m3u(url, xmlTvURL):
            secretBody = [
                "provider_type": "m3u",
                "url": url,
                "xmltv_url": xmlTvURL ?? NSNull(),
            ]
        }

        _ = try await request(
            path: "/rest/v1/rpc/player_set_playlist_secret",
            method: "POST",
            payload: [
                "p_playlist_id": playlistId,
                "p_secret": secretBody,
            ]
        )
    }

    func setEnabled(id: String, enabled: Bool) async throws {
        try await patch(id: id, fields: ["is_enabled": enabled])
    }

    func setPriority(id: String, priority: Int) async throws {
        try await patch(id: id, fields: ["priority": min(max(priority, 1), 10)])
    }

    func rename(id: String, name: String) async throws {
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { throw PlaylistError.emptyName }
        try await patch(id: id, fields: ["name": clean])
    }

    func delete(id: String) async throws {
        _ = try await request(
            path: "/rest/v1/player_playlists?id=eq.\(encoded(id))",
            method: "DELETE"
        )
    }

    private func patch(id: String, fields: [String: Any]) async throws {
        var payload = fields
        payload["updated_at"] = ISO8601DateFormatter().string(from: Date())
        _ = try await request(
            path: "/rest/v1/player_playlists?id=eq.\(encoded(id))",
            method: "PATCH",
            payload: payload,
            prefer: "return=minimal"
        )
    }

    private func currentUserId() async throws -> String {
        struct CurrentUser: Decodable { let id: String }
        let user: CurrentUser = try await get(path: "/auth/v1/user")
        return user.id
    }

    private func get<T: Decodable>(path: String) async throws -> T {
        let data = try await request(path: path, method: "GET")
        return try JSONDecoder().decode(T.self, from: data)
    }

    private func request(
        path: String,
        method: String,
        payload: Any? = nil,
        prefer: String? = nil
    ) async throws -> Data {
        guard let session = sessionStore.load() else { throw PlaylistError.noSession }
        guard let url = URL(string: path, relativeTo: baseURL) else { throw PlaylistError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        if let prefer { request.setValue(prefer, forHTTPHeaderField: "Prefer") }
        if let payload { request.httpBody = try JSONSerialization.data(withJSONObject: payload) }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw PlaylistError.invalidResponse }
        guard (200...299).contains(http.statusCode) else { throw PlaylistError.server }
        return data
    }

    private func encoded(_ value: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&=+?")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    enum PlaylistError: LocalizedError {
        case noSession
        case invalidURL
        case invalidResponse
        case providerUnavailable
        case invalidCredentials
        case invalidM3u
        case limitReached
        case emptyName
        case server

        var errorDescription: String? {
            switch self {
            case .noSession: return "Session absente."
            case .invalidURL: return "Adresse invalide."
            case .invalidResponse: return "Réponse serveur invalide."
            case .providerUnavailable: return "Le fournisseur IPTV ne répond pas."
            case .invalidCredentials: return "Identifiants Xtream invalides."
            case .invalidM3u: return "Cette adresse ne contient pas une playlist M3U valide."
            case .limitReached: return "La limite de 10 playlists est atteinte."
            case .emptyName: return "Le nom de la playlist est vide."
            case .server: return "Impossible de synchroniser la playlist."
            }
        }
    }
}

struct ProviderMovieDTO: Identifiable, Hashable {
    let id: String
    let playlistId: String
    let title: String
    let categoryId: String?
    let posterUrl: String?
    let streamUrl: URL
    let addedAtEpochSeconds: Int64?
}

struct ProviderLiveChannelDTO: Identifiable, Hashable {
    let id: String
    let playlistId: String
    let name: String
    let categoryId: String?
    let logoUrl: String?
    let streamUrl: URL
    let epgId: String?
}

struct ProviderSeriesDTO: Identifiable, Hashable {
    let id: String
    let playlistId: String
    let title: String
    let categoryId: String?
    let posterUrl: String?
    let addedAtEpochSeconds: Int64?
}


struct ProviderSeriesEpisodeDTO: Identifiable, Hashable {
    let id: String
    let season: Int
    let number: Int
    let title: String
    let synopsis: String?
    let streamUrl: URL
}

struct ProviderSeriesDetailDTO {
    let title: String?
    let year: String?
    let synopsis: String?
    let genres: [String]
    let episodes: [ProviderSeriesEpisodeDTO]
}

struct ProviderEpgProgrammeDTO: Identifiable, Hashable {
    var id: String { "\(channelId):\(startEpochSeconds):\(endEpochSeconds):\(title)" }

    let channelId: String
    let title: String
    let description: String?
    let startEpochSeconds: Int64
    let endEpochSeconds: Int64
}

struct ProviderGuideChannelDTO: Identifiable {
    var id: String { channel.id }
    let channel: ProviderLiveChannelDTO
    let programmes: [ProviderEpgProgrammeDTO]
}

struct ProviderCatalogDTO {
    let playlistId: String
    let playlistName: String
    let liveChannels: [ProviderLiveChannelDTO]
    let liveCategories: [String: String]
    let movieCategories: [String: String]
    let seriesCategories: [String: String]
    let movies: [ProviderMovieDTO]
    let series: [ProviderSeriesDTO]
}


struct ParentalRuntimeStateDTO: Decodable {
    let serverNowEpochMs: Int64?
    let parentalEnabled: Bool
    let isChild: Bool
    let consumedSeconds: Int
    let limitMinutes: Int?
    let dailyLimitMinutes: Int?
    let weekendLimitMinutes: Int?
    let warningMinutes: Int
    let scheduleEnabled: Bool
    let scheduleWindows: [[String: AnyCodableValue]]
    let exceptionUntilEpochMs: Int64?
    let blockedByTime: Bool

    enum CodingKeys: String, CodingKey {
        case serverNowEpochMs = "server_now_epoch_ms"
        case parentalEnabled = "parental_enabled"
        case isChild = "is_child"
        case consumedSeconds = "consumed_seconds"
        case limitMinutes = "limit_minutes"
        case dailyLimitMinutes = "daily_limit_minutes"
        case weekendLimitMinutes = "weekend_limit_minutes"
        case warningMinutes = "warning_minutes"
        case scheduleEnabled = "schedule_enabled"
        case scheduleWindows = "schedule_windows"
        case exceptionUntilEpochMs = "exception_until_epoch_ms"
        case blockedByTime = "blocked_by_time"
    }
}

struct ScreenTimeHeartbeatDTO: Decodable {
    let consumedSeconds: Int
    let limitMinutes: Int?
    let warningMinutes: Int
    let blockedByTime: Bool

    enum CodingKeys: String, CodingKey {
        case consumedSeconds = "consumed_seconds"
        case limitMinutes = "limit_minutes"
        case warningMinutes = "warning_minutes"
        case blockedByTime = "blocked_by_time"
    }
}

struct ParentalExceptionDTO: Decodable {
    let success: Bool
    let reason: String?
    let expiresAt: String?

    enum CodingKeys: String, CodingKey {
        case success
        case reason
        case expiresAt = "expires_at"
    }
}

enum AnyCodableValue: Decodable {
    case string(String)
    case int(Int)
    case array([AnyCodableValue])
    case bool(Bool)
    case null

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .null }
        else if let value = try? container.decode(String.self) { self = .string(value) }
        else if let value = try? container.decode(Int.self) { self = .int(value) }
        else if let value = try? container.decode(Bool.self) { self = .bool(value) }
        else if let value = try? container.decode([AnyCodableValue].self) { self = .array(value) }
        else { self = .null }
    }
}


struct CachedAppleParentalRuntime: Codable {
    let parentalEnabled: Bool
    let isChild: Bool
    let trustedEpochMs: Int64
    let trustedUptime: TimeInterval
    var consumedSeconds: Int
    let dailyLimitMinutes: Int?
    let weekendLimitMinutes: Int?
    let scheduleEnabled: Bool
    let scheduleWindows: [ParentalScheduleWindowDTO]
}

final class AppleParentalRuntimeCache {
    static let shared = AppleParentalRuntimeCache()

    private let defaults = UserDefaults.standard
    private let prefix = "zyviotv.apple.parental.runtime."

    func store(profileId: String, state: ParentalRuntimeStateDTO) {
        guard let serverNow = state.serverNowEpochMs else { return }

        let windows = state.scheduleWindows.compactMap { raw -> ParentalScheduleWindowDTO? in
            guard
                case let .array(dayValues)? = raw["days"],
                case let .string(start)? = raw["start"],
                case let .string(end)? = raw["end"]
            else { return nil }

            let days = dayValues.compactMap { value -> Int? in
                if case let .int(day) = value { return day }
                return nil
            }

            return ParentalScheduleWindowDTO(days: days, start: start, end: end)
        }

        let existing = load(profileId: profileId)
        let day = utcDayKey(epochMs: serverNow)
        let consumed: Int
        if let existing, utcDayKey(epochMs: trustedNowEpochMs(existing)) == day {
            consumed = max(existing.consumedSeconds, state.consumedSeconds)
        } else {
            consumed = max(0, state.consumedSeconds)
        }

        let cached = CachedAppleParentalRuntime(
            parentalEnabled: state.parentalEnabled,
            isChild: state.isChild,
            trustedEpochMs: serverNow,
            trustedUptime: ProcessInfo.processInfo.systemUptime,
            consumedSeconds: consumed,
            dailyLimitMinutes: state.dailyLimitMinutes,
            weekendLimitMinutes: state.weekendLimitMinutes,
            scheduleEnabled: state.scheduleEnabled,
            scheduleWindows: windows
        )

        if let data = try? JSONEncoder().encode(cached) {
            defaults.set(data, forKey: key(profileId))
        }
    }

    func load(profileId: String) -> CachedAppleParentalRuntime? {
        guard let data = defaults.data(forKey: key(profileId)) else { return nil }
        return try? JSONDecoder().decode(CachedAppleParentalRuntime.self, from: data)
    }

    func updateConsumed(profileId: String, seconds: Int) {
        guard var cached = load(profileId: profileId) else { return }
        let nowDay = utcDayKey(epochMs: trustedNowEpochMs(cached))
        let storedDay = utcDayKey(epochMs: cached.trustedEpochMs)
        cached.consumedSeconds = nowDay == storedDay
            ? max(cached.consumedSeconds, max(0, seconds))
            : max(0, seconds)
        save(profileId: profileId, cached: cached)
    }

    func addOfflineSeconds(profileId: String, seconds: Int) -> Int {
        guard var cached = load(profileId: profileId) else { return 0 }
        let nowDay = utcDayKey(epochMs: trustedNowEpochMs(cached))
        let storedDay = utcDayKey(epochMs: cached.trustedEpochMs)
        let base = nowDay == storedDay ? cached.consumedSeconds : 0
        cached.consumedSeconds = min(Int.max, base + max(0, seconds))
        save(profileId: profileId, cached: cached)
        return cached.consumedSeconds
    }

    func effectiveLimitMinutes(profileId: String) -> Int? {
        guard let cached = load(profileId: profileId) else { return nil }
        let now = Date(timeIntervalSince1970: Double(trustedNowEpochMs(cached)) / 1000)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        let weekday = calendar.component(.weekday, from: now)
        let weekend = weekday == 1 || weekday == 7
        return weekend ? (cached.weekendLimitMinutes ?? cached.dailyLimitMinutes) : cached.dailyLimitMinutes
    }

    func trustedNowEpochMs(_ cached: CachedAppleParentalRuntime) -> Int64 {
        let delta = max(0, ProcessInfo.processInfo.systemUptime - cached.trustedUptime)
        return cached.trustedEpochMs + Int64(delta * 1000)
    }

    private func save(profileId: String, cached: CachedAppleParentalRuntime) {
        if let data = try? JSONEncoder().encode(cached) {
            defaults.set(data, forKey: key(profileId))
        }
    }

    private func key(_ profileId: String) -> String {
        prefix + profileId
    }

    private func utcDayKey(epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000)
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
}

final class AppleDeviceIdentityStore {
    static let shared = AppleDeviceIdentityStore()
    private let key = "zyviotv.apple.device_uid"

    var deviceUid: String {
        if let existing = UserDefaults.standard.string(forKey: key), !existing.isEmpty {
            return existing
        }
        let value = UUID().uuidString.lowercased()
        UserDefaults.standard.set(value, forKey: key)
        return value
    }
}

struct ParentalAccountSettingsDTO: Decodable {
    let hasPin: Bool
    let enabled: Bool
    let blockedUntil: String?

    enum CodingKeys: String, CodingKey {
        case hasPin = "has_pin"
        case enabled
        case blockedUntil = "blocked_until"
    }
}

struct ParentalWriteDTO: Decodable {
    let success: Bool
    let reason: String?
}

struct ProfileContentLocksDTO: Decodable {
    let parentalEnabled: Bool
    let isChild: Bool
    let hideLocked: Bool
    let lockedCategoryKeys: [String]
    let lockedContentKeys: [String]

    enum CodingKeys: String, CodingKey {
        case parentalEnabled = "parental_enabled"
        case isChild = "is_child"
        case hideLocked = "hide_locked"
        case lockedCategoryKeys = "locked_category_keys"
        case lockedContentKeys = "locked_content_keys"
    }
}

struct ParentalScheduleWindowDTO: Codable, Hashable {
    let days: [Int]
    let start: String
    let end: String
}

struct ProfileParentalSettingsDTO: Decodable {
    let profileId: String
    let profileName: String
    let profileType: String
    let isPrimary: Bool
    let maxAge: Int?
    let hideLocked: Bool
    let dailyLimitMinutes: Int?
    let weekendLimitMinutes: Int?
    let warningMinutes: Int
    let scheduleEnabled: Bool
    let scheduleWindows: [ParentalScheduleWindowDTO]

    enum CodingKeys: String, CodingKey {
        case profileId = "profile_id"
        case profileName = "profile_name"
        case profileType = "profile_type"
        case isPrimary = "is_primary"
        case maxAge = "max_age"
        case hideLocked = "hide_locked"
        case dailyLimitMinutes = "daily_limit_minutes"
        case weekendLimitMinutes = "weekend_limit_minutes"
        case warningMinutes = "warning_minutes"
        case scheduleEnabled = "schedule_enabled"
        case scheduleWindows = "schedule_windows"
    }
}

actor SupabaseParentalService {
    static let shared = SupabaseParentalService()

    private let baseURL = URL(string: "https://nvpuftuluguawdxonmlc.supabase.co")!
    private let publishableKey = "sb_publishable_Qr5CcSZRUsi1oATqvnJb_A_5cBla4SC"
    private let sessionStore = AuthSessionStore()

    func accountSettings() async throws -> ParentalAccountSettingsDTO {
        try await rpc(name: "player_get_parental_settings", body: [:])
    }

    func setPin(newPin: String, currentPin: String?) async throws -> ParentalWriteDTO {
        var body: [String: Any] = ["p_new_pin": newPin]
        body["p_current_pin"] = currentPin ?? NSNull()
        return try await rpc(name: "player_set_parental_pin", body: body)
    }

    func setEnabled(pin: String, enabled: Bool) async throws -> ParentalWriteDTO {
        try await rpc(
            name: "player_set_parental_enabled",
            body: [
                "p_pin": pin,
                "p_enabled": enabled,
            ]
        )
    }

    func contentLocks(profileId: String) async throws -> ProfileContentLocksDTO {
        try await rpc(
            name: "player_get_profile_content_locks",
            body: ["p_profile_id": profileId]
        )
    }

    func updateContentLocks(
        profileId: String,
        pin: String,
        lockedCategoryKeys: [String],
        lockedContentKeys: [String]
    ) async throws -> ParentalWriteDTO {
        try await rpc(
            name: "player_update_profile_content_locks",
            body: [
                "p_profile_id": profileId,
                "p_pin": pin,
                "p_locked_category_keys": lockedCategoryKeys,
                "p_locked_content_keys": lockedContentKeys,
            ]
        )
    }

    func profileSettings(profileId: String) async throws -> ProfileParentalSettingsDTO {
        try await rpc(
            name: "player_get_profile_parental_settings",
            body: ["p_profile_id": profileId]
        )
    }

    func updateProfileSettings(
        profileId: String,
        pin: String,
        maxAge: Int?,
        hideLocked: Bool,
        dailyLimitMinutes: Int?,
        weekendLimitMinutes: Int?,
        warningMinutes: Int,
        scheduleEnabled: Bool,
        scheduleWindows: [ParentalScheduleWindowDTO]
    ) async throws -> ParentalWriteDTO {
        try await rpc(
            name: "player_update_profile_parental_settings",
            body: [
                "p_profile_id": profileId,
                "p_pin": pin,
                "p_max_age": maxAge ?? NSNull(),
                "p_hide_locked": hideLocked,
                "p_daily_limit_minutes": dailyLimitMinutes ?? NSNull(),
                "p_weekend_limit_minutes": weekendLimitMinutes ?? NSNull(),
                "p_warning_minutes": warningMinutes,
                "p_schedule_enabled": scheduleEnabled,
                "p_schedule_windows": scheduleWindows.map {
                    [
                        "days": $0.days,
                        "start": $0.start,
                        "end": $0.end,
                    ] as [String: Any]
                },
            ]
        )
    }

    func runtimeState(profileId: String, contentKey: String) async throws -> ParentalRuntimeStateDTO {
        try await rpc(
            name: "player_parental_runtime_state",
            body: [
                "p_profile_id": profileId,
                "p_content_key": contentKey,
            ]
        )
    }

    func heartbeat(
        profileId: String,
        deviceUid: String,
        playing: Bool,
        contentKey: String,
        localConsumedSeconds: Int
    ) async throws -> ScreenTimeHeartbeatDTO {
        try await rpc(
            name: "player_parental_screen_time_heartbeat_v2",
            body: [
                "p_profile_id": profileId,
                "p_device_uid": deviceUid,
                "p_playing": playing,
                "p_content_key": contentKey,
                "p_local_consumed_seconds": max(localConsumedSeconds, 0),
            ]
        )
    }

    func grantException(
        profileId: String,
        pin: String,
        contentKey: String
    ) async throws -> ParentalExceptionDTO {
        try await rpc(
            name: "player_parental_grant_exception",
            body: [
                "p_profile_id": profileId,
                "p_pin": pin,
                "p_content_key": contentKey,
            ]
        )
    }

    func endException(profileId: String, contentKey: String) async {
        let _: EmptyResponse? = try? await rpc(
            name: "player_parental_end_exception",
            body: [
                "p_profile_id": profileId,
                "p_content_key": contentKey,
            ]
        )
    }

    private func rpc<T: Decodable>(name: String, body: [String: Any]) async throws -> T {
        guard let session = sessionStore.load() else {
            throw ParentalError.noSession
        }
        guard let url = URL(string: "/rest/v1/rpc/\(name)", relativeTo: baseURL) else {
            throw ParentalError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ParentalError.invalidResponse
        }
        guard (200...299).contains(http.statusCode) else {
            throw ParentalError.server
        }

        if T.self == EmptyResponse.self, data.isEmpty {
            return EmptyResponse() as! T
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    struct EmptyResponse: Decodable {}

    enum ParentalError: LocalizedError {
        case noSession
        case invalidURL
        case invalidResponse
        case server

        var errorDescription: String? {
            switch self {
            case .noSession: return "Session absente."
            case .invalidURL: return "Configuration serveur invalide."
            case .invalidResponse: return "Réponse serveur invalide."
            case .server: return "Impossible de vérifier le contrôle parental."
            }
        }
    }
}

actor SupabaseProviderCatalogService {
    static let shared = SupabaseProviderCatalogService()

    private let baseURL = URL(string: "https://nvpuftuluguawdxonmlc.supabase.co")!
    private let publishableKey = "sb_publishable_Qr5CcSZRUsi1oATqvnJb_A_5cBla4SC"
    private let sessionStore = AuthSessionStore()

    func loadGuide(
        playlistId: String,
        channels: [ProviderLiveChannelDTO],
        maxChannels: Int = 50
    ) async throws -> [ProviderGuideChannelDTO] {
        let secret = try await loadPlaylistSecret(playlistId: playlistId)
        guard
            secret.providerType == "xtream",
            let serverURL = secret.serverURL,
            let username = secret.username,
            let password = secret.password
        else {
            throw ProviderCatalogError.invalidSecret
        }

        var result: [ProviderGuideChannelDTO] = []
        for channel in channels.prefix(max(1, min(maxChannels, 50))) {
            let programmes = (try? await loadShortEpg(
                serverURL: serverURL,
                username: username,
                password: password,
                channelId: channel.id
            )) ?? []
            result.append(
                ProviderGuideChannelDTO(
                    channel: channel,
                    programmes: programmes
                )
            )
        }
        return result
    }

    private func loadShortEpg(
        serverURL: URL,
        username: String,
        password: String,
        channelId: String
    ) async throws -> [ProviderEpgProgrammeDTO] {
        guard var components = URLComponents(
            url: serverURL.appendingPathComponent("player_api.php"),
            resolvingAgainstBaseURL: false
        ) else {
            throw ProviderCatalogError.invalidURL
        }

        components.queryItems = [
            URLQueryItem(name: "username", value: username),
            URLQueryItem(name: "password", value: password),
            URLQueryItem(name: "action", value: "get_short_epg"),
            URLQueryItem(name: "stream_id", value: channelId),
            URLQueryItem(name: "limit", value: "100"),
        ]

        guard let url = components.url else {
            throw ProviderCatalogError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("ZYVIOTV-Player/0.1", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard
            let httpResponse = response as? HTTPURLResponse,
            (200...299).contains(httpResponse.statusCode),
            let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let items = root["epg_listings"] as? [[String: Any]]
        else {
            throw ProviderCatalogError.providerUnavailable
        }

        let now = Int64(Date().timeIntervalSince1970)
        let from = now - 3 * 60 * 60
        let to = now + 6 * 60 * 60

        let mapped: [ProviderEpgProgrammeDTO] = items.compactMap { item -> ProviderEpgProgrammeDTO? in
            guard
                let start = epochSeconds(item["start_timestamp"]),
                let end = epochSeconds(item["stop_timestamp"]),
                end > start,
                end > from,
                start < to
            else {
                return nil
            }

            return ProviderEpgProgrammeDTO(
                channelId: channelId,
                title: decodeMaybeBase64(cleanString(item["title"]) ?? "").ifBlank("Programme TV"),
                description: decodeMaybeBase64(cleanString(item["description"]) ?? "").nilIfBlank,
                startEpochSeconds: start,
                endEpochSeconds: end
            )
        }

        var unique: [ProviderEpgProgrammeDTO] = []
        for item in mapped {
            let exists = unique.contains {
                $0.startEpochSeconds == item.startEpochSeconds &&
                $0.endEpochSeconds == item.endEpochSeconds &&
                $0.title == item.title
            }
            if !exists {
                unique.append(item)
            }
        }

        return unique.sorted { $0.startEpochSeconds < $1.startEpochSeconds }
    }

    private func decodeMaybeBase64(_ value: String) -> String {
        guard !value.isEmpty,
              let data = Data(base64Encoded: value),
              let decoded = String(data: data, encoding: .utf8),
              decoded.unicodeScalars.allSatisfy({
                  $0.value == 9 || $0.value == 10 || $0.value == 13 || $0.value >= 32
              })
        else {
            return value
        }
        return decoded
    }

    func loadSeriesDetail(
        playlistId: String,
        seriesId: String
    ) async throws -> ProviderSeriesDetailDTO {
        let secret = try await loadPlaylistSecret(playlistId: playlistId)
        guard
            secret.providerType == "xtream",
            let serverURL = secret.serverURL,
            let username = secret.username,
            let password = secret.password
        else {
            throw ProviderCatalogError.invalidSecret
        }

        guard var components = URLComponents(
            url: serverURL.appendingPathComponent("player_api.php"),
            resolvingAgainstBaseURL: false
        ) else {
            throw ProviderCatalogError.invalidURL
        }

        components.queryItems = [
            URLQueryItem(name: "username", value: username),
            URLQueryItem(name: "password", value: password),
            URLQueryItem(name: "action", value: "get_series_info"),
            URLQueryItem(name: "series_id", value: seriesId),
        ]

        guard let url = components.url else {
            throw ProviderCatalogError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 30
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("ZYVIOTV-Player/0.1", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard
            let httpResponse = response as? HTTPURLResponse,
            (200...299).contains(httpResponse.statusCode),
            let root = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            throw ProviderCatalogError.providerUnavailable
        }

        let info = root["info"] as? [String: Any]
        let episodesObject = root["episodes"] as? [String: Any] ?? [:]
        var episodes: [ProviderSeriesEpisodeDTO] = []

        for (seasonKey, rawValue) in episodesObject {
            guard
                let season = Int(seasonKey),
                let array = rawValue as? [[String: Any]]
            else {
                continue
            }

            for (index, item) in array.enumerated() {
                guard
                    let episodeId = stringValue(item["id"]) ?? stringValue(item["stream_id"])
                else {
                    continue
                }

                let number =
                    Int(stringValue(item["episode_num"]) ?? "") ??
                    (item["episode_num"] as? NSNumber)?.intValue ??
                    index + 1
                let title = cleanString(item["title"]) ?? "Épisode \(number)"
                let extensionValue = cleanString(item["container_extension"]) ?? "mp4"
                let episodeInfo = item["info"] as? [String: Any]

                guard let streamURL = mediaURL(
                    serverURL: serverURL,
                    kind: "series",
                    username: username,
                    password: password,
                    id: episodeId,
                    extensionValue: extensionValue
                ) else {
                    continue
                }

                episodes.append(
                    ProviderSeriesEpisodeDTO(
                        id: episodeId,
                        season: season,
                        number: number,
                        title: title,
                        synopsis: cleanString(episodeInfo?["plot"]),
                        streamUrl: streamURL
                    )
                )
            }
        }

        episodes.sort {
            if $0.season == $1.season {
                return $0.number < $1.number
            }
            return $0.season < $1.season
        }

        let releaseDate = cleanString(info?["releaseDate"])
        let year = releaseDate.flatMap {
            $0.count >= 4 ? String($0.prefix(4)) : nil
        }
        let genres = (cleanString(info?["genre"]) ?? "")
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }

        return ProviderSeriesDetailDTO(
            title: cleanString(info?["name"]),
            year: year,
            synopsis: cleanString(info?["plot"]),
            genres: genres,
            episodes: episodes
        )
    }

    func loadCatalog(applyParentalFilters: Bool = true) async throws -> ProviderCatalogDTO {
        let playlists: [ProviderPlaylistDTO] = try await supabaseGet(
            path: "/rest/v1/player_playlists?select=id,name,provider_type,secret_status,is_enabled,priority&order=priority.asc,updated_at.desc"
        )

        guard let playlist = playlists.first(where: {
            $0.isEnabled && $0.secretStatus == "configured"
        }) else {
            throw ProviderCatalogError.noConfiguredPlaylist
        }

        let secret = try await loadPlaylistSecret(playlistId: playlist.id)
        guard secret.providerType == "xtream" else {
            // M3U playlists currently provide Live content only in the shared product model.
            return ProviderCatalogDTO(
                playlistId: playlist.id,
                playlistName: playlist.name,
                liveChannels: [],
                liveCategories: [:],
                movieCategories: [:],
                seriesCategories: [:],
                movies: [],
                series: []
            )
        }

        guard
            let serverURL = secret.serverURL,
            let username = secret.username,
            let password = secret.password
        else {
            throw ProviderCatalogError.invalidSecret
        }

        async let liveCategoryPayload = providerArray(
            serverURL: serverURL,
            username: username,
            password: password,
            action: "get_live_categories"
        )
        async let livePayload = providerArray(
            serverURL: serverURL,
            username: username,
            password: password,
            action: "get_live_streams"
        )
        async let movieCategoryPayload = providerArray(
            serverURL: serverURL,
            username: username,
            password: password,
            action: "get_vod_categories"
        )
        async let seriesCategoryPayload = providerArray(
            serverURL: serverURL,
            username: username,
            password: password,
            action: "get_series_categories"
        )
        async let moviePayload = providerArray(
            serverURL: serverURL,
            username: username,
            password: password,
            action: "get_vod_streams"
        )
        async let seriesPayload = providerArray(
            serverURL: serverURL,
            username: username,
            password: password,
            action: "get_series"
        )

        let liveCategories = Dictionary(
            uniqueKeysWithValues: try await liveCategoryPayload.compactMap { item -> (String, String)? in
                guard
                    let id = stringValue(item["category_id"]),
                    let name = cleanString(item["category_name"])
                else {
                    return nil
                }
                return (id, name)
            }
        )

        let movieCategories = Dictionary(
            uniqueKeysWithValues: try await movieCategoryPayload.compactMap { item -> (String, String)? in
                guard
                    let id = stringValue(item["category_id"]),
                    let name = cleanString(item["category_name"])
                else { return nil }
                return (id, name)
            }
        )

        let seriesCategories = Dictionary(
            uniqueKeysWithValues: try await seriesCategoryPayload.compactMap { item -> (String, String)? in
                guard
                    let id = stringValue(item["category_id"]),
                    let name = cleanString(item["category_name"])
                else { return nil }
                return (id, name)
            }
        )

        let liveChannels = try await livePayload.compactMap { item -> ProviderLiveChannelDTO? in
            guard
                let id = stringValue(item["stream_id"]),
                let name = cleanString(item["name"]),
                let streamURL = mediaURL(
                    serverURL: serverURL,
                    kind: "live",
                    username: username,
                    password: password,
                    id: id,
                    extensionValue: "ts"
                )
            else {
                return nil
            }

            return ProviderLiveChannelDTO(
                id: id,
                playlistId: playlist.id,
                name: name,
                categoryId: cleanString(item["category_id"]),
                logoUrl: cleanString(item["stream_icon"]),
                streamUrl: streamURL,
                epgId: id
            )
        }

        let movies = try await moviePayload.compactMap { item -> ProviderMovieDTO? in
            guard
                let id = stringValue(item["stream_id"]),
                let title = cleanString(item["name"])
            else {
                return nil
            }

            let extensionValue = cleanString(item["container_extension"]) ?? "mp4"
            guard let streamURL = mediaURL(
                serverURL: serverURL,
                kind: "movie",
                username: username,
                password: password,
                id: id,
                extensionValue: extensionValue
            ) else {
                return nil
            }

            return ProviderMovieDTO(
                id: id,
                playlistId: playlist.id,
                title: title,
                categoryId: cleanString(item["category_id"]),
                posterUrl: cleanString(item["stream_icon"]),
                streamUrl: streamURL,
                addedAtEpochSeconds: epochSeconds(item["added"])
            )
        }

        let series = try await seriesPayload.compactMap { item -> ProviderSeriesDTO? in
            guard
                let id = stringValue(item["series_id"]),
                let title = cleanString(item["name"])
            else {
                return nil
            }

            return ProviderSeriesDTO(
                id: id,
                playlistId: playlist.id,
                title: title,
                categoryId: cleanString(item["category_id"]),
                posterUrl: cleanString(item["cover"]),
                addedAtEpochSeconds: epochSeconds(item["added"])
            )
        }

        let locks: ProfileContentLocksDTO?
        if let profileId = PlayerProfileSelectionStore.shared.activeProfileId {
            locks = try? await SupabaseParentalService.shared.contentLocks(profileId: profileId)
        } else {
            locks = nil
        }

        let blockedCategories = Set(locks?.lockedCategoryKeys ?? [])
        let blockedContent = Set(locks?.lockedContentKeys ?? [])
        let shouldFilter = applyParentalFilters && locks?.parentalEnabled == true && locks?.isChild == true

        let visibleLive = shouldFilter ? liveChannels.filter { channel in
            let contentKey = "live:" + channel.id
            let categoryKey = channel.categoryId.map { "live:" + $0 }
            let adultCategory = channel.categoryId
                .flatMap { liveCategories[$0] }
                .map(isAdultCategoryName) ?? false
            return !blockedContent.contains(contentKey)
                && !(categoryKey.map(blockedCategories.contains) ?? false)
                && !adultCategory
        } : liveChannels

        let visibleMovies = shouldFilter ? movies.filter { movie in
            let contentKey = "movie:" + movie.id
            let categoryKey = movie.categoryId.map { "movie:" + $0 }
            let adultCategory = movie.categoryId
                .flatMap { movieCategories[$0] }
                .map(isAdultCategoryName) ?? false
            return !blockedContent.contains(contentKey)
                && !(categoryKey.map(blockedCategories.contains) ?? false)
                && !adultCategory
        } : movies

        let visibleSeries = shouldFilter ? series.filter { item in
            let contentKey = "series:" + item.id
            let categoryKey = item.categoryId.map { "series:" + $0 }
            let adultCategory = item.categoryId
                .flatMap { seriesCategories[$0] }
                .map(isAdultCategoryName) ?? false
            return !blockedContent.contains(contentKey)
                && !(categoryKey.map(blockedCategories.contains) ?? false)
                && !adultCategory
        } : series

        return ProviderCatalogDTO(
            playlistId: playlist.id,
            playlistName: playlist.name,
            liveChannels: visibleLive,
            liveCategories: liveCategories,
            movieCategories: movieCategories,
            seriesCategories: seriesCategories,
            movies: visibleMovies.sorted {
                ($0.addedAtEpochSeconds ?? 0) > ($1.addedAtEpochSeconds ?? 0)
            },
            series: visibleSeries.sorted {
                ($0.addedAtEpochSeconds ?? 0) > ($1.addedAtEpochSeconds ?? 0)
            }
        )
    }

    private func isAdultCategoryName(_ value: String) -> Bool {
        let normalized = value
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .lowercased()
        return ["adult", "adulte", "xxx", "porn", "erotic", "erotique", "18+", "+18"]
            .contains { normalized.contains($0) }
    }

    private struct PlaylistSecretPayload {
        let providerType: String
        let serverURL: URL?
        let username: String?
        let password: String?
        let m3uURL: URL?
        let xmlTvURL: URL?
    }

    private func loadPlaylistSecret(playlistId: String) async throws -> PlaylistSecretPayload {
        guard let session = sessionStore.load() else {
            throw ProviderCatalogError.noSession
        }
        guard let url = URL(string: "/rest/v1/rpc/player_get_playlist_secret", relativeTo: baseURL) else {
            throw ProviderCatalogError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["p_playlist_id": playlistId]
        )

        let (data, response) = try await URLSession.shared.data(for: request)
        guard
            let httpResponse = response as? HTTPURLResponse,
            (200...299).contains(httpResponse.statusCode),
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            throw ProviderCatalogError.secretUnavailable
        }

        let providerType = cleanString(json["provider_type"]) ?? ""
        if providerType == "xtream" {
            guard
                let rawServerURL = cleanString(json["server_url"]),
                let serverURL = URL(string: rawServerURL),
                let username = cleanString(json["username"]),
                let password = cleanString(json["password"])
            else {
                throw ProviderCatalogError.invalidSecret
            }
            return PlaylistSecretPayload(
                providerType: providerType,
                serverURL: serverURL,
                username: username,
                password: password,
                m3uURL: nil,
                xmlTvURL: nil
            )
        }

        if providerType == "m3u" {
            guard
                let rawURL = cleanString(json["url"]),
                let m3uURL = URL(string: rawURL)
            else {
                throw ProviderCatalogError.invalidSecret
            }
            return PlaylistSecretPayload(
                providerType: providerType,
                serverURL: nil,
                username: nil,
                password: nil,
                m3uURL: m3uURL,
                xmlTvURL: cleanString(json["xmltv_url"]).flatMap(URL.init(string:))
            )
        }

        return PlaylistSecretPayload(
            providerType: providerType,
            serverURL: nil,
            username: nil,
            password: nil,
            m3uURL: nil,
            xmlTvURL: nil
        )
    }

    private func providerArray(
        serverURL: URL,
        username: String,
        password: String,
        action: String
    ) async throws -> [[String: Any]] {
        guard var components = URLComponents(
            url: serverURL.appendingPathComponent("player_api.php"),
            resolvingAgainstBaseURL: false
        ) else {
            throw ProviderCatalogError.invalidURL
        }

        components.queryItems = [
            URLQueryItem(name: "username", value: username),
            URLQueryItem(name: "password", value: password),
            URLQueryItem(name: "action", value: action),
        ]

        guard let url = components.url else {
            throw ProviderCatalogError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 30
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("ZYVIOTV-Player/0.1", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard
            let httpResponse = response as? HTTPURLResponse,
            (200...299).contains(httpResponse.statusCode),
            let payload = try JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else {
            throw ProviderCatalogError.providerUnavailable
        }

        return payload
    }

    private func mediaURL(
        serverURL: URL,
        kind: String,
        username: String,
        password: String,
        id: String,
        extensionValue: String
    ) -> URL? {
        var url = serverURL
        for component in [kind, username, password, "\(id).\(extensionValue)"] {
            url.appendPathComponent(component)
        }
        return url
    }

    private func supabaseGet<T: Decodable>(path: String) async throws -> T {
        guard let session = sessionStore.load() else {
            throw ProviderCatalogError.noSession
        }
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw ProviderCatalogError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard
            let httpResponse = response as? HTTPURLResponse,
            (200...299).contains(httpResponse.statusCode)
        else {
            throw ProviderCatalogError.server
        }

        return try JSONDecoder().decode(T.self, from: data)
    }

    private func cleanString(_ value: Any?) -> String? {
        guard let value, !(value is NSNull) else {
            return nil
        }
        let clean = String(describing: value)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty || clean == "null" ? nil : clean
    }

    private func stringValue(_ value: Any?) -> String? {
        cleanString(value)
    }

    private func epochSeconds(_ value: Any?) -> Int64? {
        guard let raw = cleanString(value), let number = Int64(raw), number > 0 else {
            return nil
        }
        return number > 9_999_999_999 ? number / 1_000 : number
    }

    enum ProviderCatalogError: LocalizedError {
        case noSession
        case noConfiguredPlaylist
        case invalidURL
        case secretUnavailable
        case invalidSecret
        case providerUnavailable
        case server

        var errorDescription: String? {
            switch self {
            case .noSession:
                return "Session absente."
            case .noConfiguredPlaylist:
                return "Aucune playlist active et configurée n’est disponible."
            case .invalidURL:
                return "Configuration fournisseur invalide."
            case .secretUnavailable:
                return "Impossible de restaurer la configuration sécurisée de la playlist."
            case .invalidSecret:
                return "La configuration sécurisée de la playlist est invalide."
            case .providerUnavailable:
                return "Impossible de charger le catalogue IPTV."
            case .server:
                return "Impossible de récupérer vos playlists."
            }
        }
    }
}


private extension String {
    func ifBlank(_ fallback: String) -> String {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? fallback : self
    }

    var nilIfBlank: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}
