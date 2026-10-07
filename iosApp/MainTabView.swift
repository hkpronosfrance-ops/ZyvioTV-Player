import SwiftUI

private enum AppSection: String, CaseIterable, Identifiable {
    case home
    case live
    case movies
    case series
    case account

    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: return "Accueil"
        case .live: return "TV"
        case .movies: return "Films"
        case .series: return "Séries"
        case .account: return "Plus"
        }
    }

    var systemImage: String {
        switch self {
        case .home: return "house.fill"
        case .live: return "tv.fill"
        case .movies: return "film.fill"
        case .series: return "rectangle.stack.fill"
        case .account: return "ellipsis.circle.fill"
        }
    }
}

struct MainTabView: View {
    let profile: PlayerProfileDTO
    let onSignedOut: () -> Void
    let onSwitchProfile: () -> Void

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var selectedSection: AppSection = .home

    var body: some View {
        Group {
            if horizontalSizeClass == .regular {
                NavigationSplitView {
                    List {
                        ForEach(AppSection.allCases) { section in
                            Button {
                                selectedSection = section
                            } label: {
                                Label(section.title, systemImage: section.systemImage)
                                    .foregroundStyle(
                                        selectedSection == section ? Color.white : Color.secondary
                                    )
                            }
                            .listRowBackground(
                                selectedSection == section
                                    ? Color.red.opacity(0.22)
                                    : Color.clear
                            )
                        }
                    }
                    .navigationTitle("ZYVIOTV")
                    .scrollContentBackground(.hidden)
                    .background(Color.black)
                } detail: {
                    sectionView(selectedSection)
                }
                .navigationSplitViewStyle(.balanced)
            } else {
                TabView(selection: $selectedSection) {
                    sectionView(.home)
                        .tabItem {
                            Label("Accueil", systemImage: "house.fill")
                        }
                        .tag(AppSection.home)

                    sectionView(.live)
                        .tabItem {
                            Label("TV", systemImage: "tv.fill")
                        }
                        .tag(AppSection.live)

                    sectionView(.movies)
                        .tabItem {
                            Label("Films", systemImage: "film.fill")
                        }
                        .tag(AppSection.movies)

                    sectionView(.series)
                        .tabItem {
                            Label("Séries", systemImage: "rectangle.stack.fill")
                        }
                        .tag(AppSection.series)

                    sectionView(.account)
                        .tabItem {
                            Label("Plus", systemImage: "ellipsis.circle.fill")
                        }
                        .tag(AppSection.account)
                }
            }
        }
        .tint(.red)
        .preferredColorScheme(.dark)
    }

    @ViewBuilder
    private func sectionView(_ section: AppSection) -> some View {
        switch section {
        case .home:
            HomeView()
        case .live:
            LiveTvView()
        case .movies:
            MoviesView()
        case .series:
            SeriesView()
        case .account:
            AccountView(
                profile: profile,
                onSignedOut: onSignedOut,
                onSwitchProfile: onSwitchProfile
            )
        }
    }
}

private struct AccountView: View {
    let profile: PlayerProfileDTO
    let onSignedOut: () -> Void
    let onSwitchProfile: () -> Void
    @State private var isSigningOut = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()

                VStack(alignment: .leading, spacing: 20) {
                    Text("Compte")
                        .font(.largeTitle.bold())

                    Text("Votre compte ZYVIOTV Player synchronise vos appareils, favoris et progressions.")
                        .foregroundStyle(.secondary)

                    HStack(spacing: 14) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.white.opacity(0.08))
                            Image(systemName: profile.isChild ? "figure.and.child.holdinghands" : "person.crop.circle.fill")
                                .font(.title)
                                .foregroundStyle(.white)
                        }
                        .frame(width: 58, height: 58)

                        VStack(alignment: .leading, spacing: 4) {
                            Text(profile.name)
                                .font(.headline)
                            Text(profile.isChild ? "Profil enfant" : (profile.isPrimary ? "Profil principal" : "Profil standard"))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()
                    }
                    .padding(14)
                    .background(Color.white.opacity(0.05))
                    .clipShape(RoundedRectangle(cornerRadius: 18))

                    Button {
                        onSwitchProfile()
                    } label: {
                        Label("Changer de profil", systemImage: "person.2.fill")
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)

                    NavigationLink {
                        ParentalSettingsView()
                    } label: {
                        Label("Contrôle parental", systemImage: "lock.shield.fill")
                    }
                    .buttonStyle(.bordered)

                    NavigationLink {
                        PlaylistSettingsView(onSignedOut: onSignedOut)
                    } label: {
                        Label("Playlists", systemImage: "list.bullet.rectangle")
                    }
                    .buttonStyle(.bordered)

                    NavigationLink {
                        DevicesSettingsView()
                    } label: {
                        Label("Appareils", systemImage: "laptopcomputer.and.iphone")
                    }
                    .buttonStyle(.bordered)

                    NavigationLink {
                        GlobalSearchView()
                    } label: {
                        Label("Recherche", systemImage: "magnifyingglass")
                    }
                    .buttonStyle(.bordered)

                    NavigationLink {
                        LibraryView()
                    } label: {
                        Label("Bibliothèque", systemImage: "books.vertical.fill")
                    }
                    .buttonStyle(.bordered)

                    Button(role: .destructive) {
                        Task { await signOut() }
                    } label: {
                        if isSigningOut {
                            ProgressView()
                        } else {
                            Label("Se déconnecter", systemImage: "rectangle.portrait.and.arrow.right")
                        }
                    }
                    .buttonStyle(.bordered)
                    .disabled(isSigningOut)

                    Spacer()
                }
                .padding(24)
            }
            .navigationTitle("Plus")
        }
    }

    @MainActor
    private func signOut() async {
        isSigningOut = true
        await SupabaseAuthService.shared.signOut()
        isSigningOut = false
        onSignedOut()
    }
}




private enum SearchPlaybackTarget: Identifiable {
    case live(ProviderLiveChannelDTO)
    case movie(ProviderMovieDTO, SyncedWatchProgressDTO?)

    var id: String {
        switch self {
        case .live(let item):
            return "live:" + item.id
        case .movie(let item, _):
            return "movie:" + item.id
        }
    }
}

private struct GlobalSearchView: View {
    @State private var query = ""
    @State private var catalog: ProviderCatalogDTO?
    @State private var favorites: [SyncedFavoriteDTO] = []
    @State private var progress: [SyncedWatchProgressDTO] = []
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var playbackTarget: SearchPlaybackTarget?

    private var normalizedQuery: String {
        normalized(query)
    }

    private var liveResults: [ProviderLiveChannelDTO] {
        guard let catalog, !normalizedQuery.isEmpty else { return [] }
        return catalog.liveChannels
            .filter { normalized($0.name).contains(normalizedQuery) }
            .prefix(30)
            .map { $0 }
    }

    private var movieResults: [ProviderMovieDTO] {
        guard let catalog, !normalizedQuery.isEmpty else { return [] }
        return catalog.movies
            .filter { normalized($0.title).contains(normalizedQuery) }
            .prefix(30)
            .map { $0 }
    }

    private var seriesResults: [ProviderSeriesDTO] {
        guard let catalog, !normalizedQuery.isEmpty else { return [] }
        return catalog.series
            .filter { normalized($0.title).contains(normalizedQuery) }
            .prefix(30)
            .map { $0 }
    }

    var body: some View {
        Group {
            if loading {
                ProgressView("Chargement du catalogue…")
                    .tint(.red)
            } else if let errorMessage {
                ContentUnavailableView {
                    Label("Recherche indisponible", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(errorMessage)
                } actions: {
                    Button("Réessayer") { Task { await reload() } }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                }
            } else {
                List {
                    if normalizedQuery.isEmpty {
                        ContentUnavailableView(
                            "Rechercher dans ZYVIOTV",
                            systemImage: "magnifyingglass",
                            description: Text("Chaînes, films et séries.")
                        )
                        .listRowBackground(Color.clear)
                    } else if liveResults.isEmpty && movieResults.isEmpty && seriesResults.isEmpty {
                        ContentUnavailableView.search(text: query)
                            .listRowBackground(Color.clear)
                    } else {
                        if !liveResults.isEmpty {
                            Section("Chaînes") {
                                ForEach(liveResults) { channel in
                                    searchRow(
                                        title: channel.name,
                                        subtitle: "TV en direct",
                                        artwork: channel.logoUrl,
                                        systemImage: "tv.fill",
                                        favorite: isFavorite(
                                            playlistId: channel.playlistId,
                                            type: "live",
                                            contentId: channel.id
                                        ),
                                        onFavorite: {
                                            Task {
                                                await toggleFavorite(
                                                    playlistId: channel.playlistId,
                                                    type: "live",
                                                    contentId: channel.id,
                                                    title: channel.name,
                                                    artwork: channel.logoUrl
                                                )
                                            }
                                        },
                                        onOpen: {
                                            playbackTarget = .live(channel)
                                        }
                                    )
                                }
                            }
                        }

                        if !movieResults.isEmpty {
                            Section("Films") {
                                ForEach(movieResults) { movie in
                                    searchRow(
                                        title: movie.title,
                                        subtitle: "Film",
                                        artwork: movie.posterUrl,
                                        systemImage: "film.fill",
                                        favorite: isFavorite(
                                            playlistId: movie.playlistId,
                                            type: "movie",
                                            contentId: movie.id
                                        ),
                                        onFavorite: {
                                            Task {
                                                await toggleFavorite(
                                                    playlistId: movie.playlistId,
                                                    type: "movie",
                                                    contentId: movie.id,
                                                    title: movie.title,
                                                    artwork: movie.posterUrl
                                                )
                                            }
                                        },
                                        onOpen: {
                                            playbackTarget = .movie(
                                                movie,
                                                progress.first {
                                                    $0.playlistId == movie.playlistId &&
                                                    $0.contentType == "movie" &&
                                                    $0.contentId == movie.id
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        if !seriesResults.isEmpty {
                            Section("Séries") {
                                ForEach(seriesResults) { series in
                                    HStack(spacing: 12) {
                                        SearchArtwork(
                                            urlString: series.posterUrl,
                                            systemImage: "rectangle.stack.fill"
                                        )
                                        .frame(width: 46, height: 64)

                                        NavigationLink {
                                            SeriesDetailView(series: series)
                                        } label: {
                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(series.title)
                                                    .foregroundStyle(.primary)
                                                Text("Série")
                                                    .font(.caption)
                                                    .foregroundStyle(.secondary)
                                            }
                                        }

                                        Spacer()

                                        Button {
                                            Task {
                                                await toggleFavorite(
                                                    playlistId: series.playlistId,
                                                    type: "series",
                                                    contentId: series.id,
                                                    title: series.title,
                                                    artwork: series.posterUrl
                                                )
                                            }
                                        } label: {
                                            Image(
                                                systemName: isFavorite(
                                                    playlistId: series.playlistId,
                                                    type: "series",
                                                    contentId: series.id
                                                ) ? "heart.fill" : "heart"
                                            )
                                        }
                                        .buttonStyle(.plain)
                                        .foregroundStyle(.red)
                                    }
                                }
                            }
                        }
                    }
                }
                .scrollContentBackground(.hidden)
                .background(Color.black)
            }
        }
        .navigationTitle("Recherche")
        .searchable(text: $query, prompt: "Chaîne, film ou série")
        .task { await reload() }
        .fullScreenCover(item: $playbackTarget) { target in
            switch target {
            case .live(let channel):
                SearchLivePlayer(channel: channel)
            case .movie(let movie, let existingProgress):
                MoviePlayerScreen(
                    movie: movie,
                    existingProgress: existingProgress,
                    onProgressSaved: { updated in
                        if let index = progress.firstIndex(where: { $0.id == updated.id }) {
                            progress[index] = updated
                        } else {
                            progress.insert(updated, at: 0)
                        }
                    }
                )
            }
        }
    }

    @ViewBuilder
    private func searchRow(
        title: String,
        subtitle: String,
        artwork: String?,
        systemImage: String,
        favorite: Bool,
        onFavorite: @escaping () -> Void,
        onOpen: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 12) {
            SearchArtwork(urlString: artwork, systemImage: systemImage)
                .frame(width: 52, height: 52)

            Button(action: onOpen) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)

            Button(action: onFavorite) {
                Image(systemName: favorite ? "heart.fill" : "heart")
                    .foregroundStyle(.red)
            }
            .buttonStyle(.plain)
        }
    }

    @MainActor
    private func reload() async {
        loading = true
        errorMessage = nil
        do {
            async let catalogTask = SupabaseProviderCatalogService.shared.loadCatalog()
            async let favoritesTask = SupabaseLibrarySyncService.shared.listFavorites()
            async let progressTask = SupabaseLibrarySyncService.shared.listWatchProgress(limit: 200)

            catalog = try await catalogTask
            favorites = try await favoritesTask
            progress = try await progressTask
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }

    @MainActor
    private func toggleFavorite(
        playlistId: String,
        type: String,
        contentId: String,
        title: String,
        artwork: String?
    ) async {
        let value = SyncedFavoriteDTO(
            playlistId: playlistId,
            contentType: type,
            contentId: contentId,
            title: title,
            artworkUrl: artwork
        )

        do {
            if let index = favorites.firstIndex(where: { $0.id == value.id }) {
                try await SupabaseLibrarySyncService.shared.removeFavorite(value)
                favorites.remove(at: index)
            } else {
                try await SupabaseLibrarySyncService.shared.upsertFavorite(value)
                favorites.insert(value, at: 0)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func isFavorite(playlistId: String, type: String, contentId: String) -> Bool {
        favorites.contains {
            $0.playlistId == playlistId &&
            $0.contentType == type &&
            $0.contentId == contentId
        }
    }

    private func normalized(_ value: String) -> String {
        value
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .lowercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

private struct SearchArtwork: View {
    let urlString: String?
    let systemImage: String

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.white.opacity(0.07))

            if let urlString, let url = URL(string: urlString) {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFit()
                    } else {
                        Image(systemName: systemImage)
                            .foregroundStyle(.red)
                    }
                }
                .padding(4)
            } else {
                Image(systemName: systemImage)
                    .foregroundStyle(.red)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

private struct SearchLivePlayer: View {
    let channel: ProviderLiveChannelDTO

    @Environment(\.dismiss) private var dismiss
    @State private var errorMessage: String?

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()

            ParentalProtectedPlayerView(
                title: channel.name,
                streamURL: channel.streamUrl,
                resumePositionSeconds: 0,
                playbackKind: "live",
                onError: { errorMessage = $0 }
            )
            .ignoresSafeArea()

            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .padding(12)
                    .background(.black.opacity(0.65))
                    .clipShape(Circle())
            }
            .foregroundStyle(.white)
            .padding(18)

            if let errorMessage {
                Text(errorMessage)
                    .padding(16)
                    .background(Color.black.opacity(0.9))
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .padding(18)
            }
        }
    }
}

private struct LibraryView: View {
    @State private var favorites: [SyncedFavoriteDTO] = []
    @State private var progress: [SyncedWatchProgressDTO] = []
    @State private var loading = true
    @State private var errorMessage: String?

    private var continueWatching: [SyncedWatchProgressDTO] {
        progress.filter { !$0.completed && $0.positionMs > 0 }
    }

    var body: some View {
        List {
            if loading {
                HStack {
                    Spacer()
                    ProgressView("Chargement…")
                    Spacer()
                }
            } else if let errorMessage {
                VStack(alignment: .leading, spacing: 8) {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                    Button("Réessayer") { Task { await reload() } }
                }
            } else {
                Section("Favoris") {
                    if favorites.isEmpty {
                        Text("Aucun favori.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(favorites) { item in
                            LibraryRow(
                                title: item.title,
                                subtitle: libraryTypeLabel(item.contentType),
                                artworkUrl: item.artworkUrl,
                                progress: nil
                            )
                        }
                    }
                }

                Section("Continuer") {
                    if continueWatching.isEmpty {
                        Text("Aucune lecture à reprendre.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(continueWatching) { item in
                            LibraryRow(
                                title: item.title,
                                subtitle: progressSubtitle(item),
                                artworkUrl: item.artworkUrl,
                                progress: progressFraction(item)
                            )
                        }
                    }
                }

                Section("Historique") {
                    if progress.isEmpty {
                        Text("Aucun historique.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(progress) { item in
                            LibraryRow(
                                title: item.title,
                                subtitle: progressSubtitle(item),
                                artworkUrl: item.artworkUrl,
                                progress: progressFraction(item)
                            )
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.black)
        .navigationTitle("Bibliothèque")
        .refreshable { await reload() }
        .task { await reload() }
    }

    @MainActor
    private func reload() async {
        loading = true
        errorMessage = nil
        do {
            async let favoritesTask = SupabaseLibrarySyncService.shared.listFavorites()
            async let progressTask = SupabaseLibrarySyncService.shared.listWatchProgress(limit: 200)
            favorites = try await favoritesTask
            progress = try await progressTask
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }

    private func progressFraction(_ item: SyncedWatchProgressDTO) -> Double? {
        guard let duration = item.durationMs, duration > 0 else { return nil }
        return min(max(Double(item.positionMs) / Double(duration), 0), 1)
    }

    private func progressSubtitle(_ item: SyncedWatchProgressDTO) -> String {
        if let season = item.seasonNumber, let episode = item.episodeNumber {
            return "S\(season) E\(episode)" + (item.completed ? " · Vu" : "")
        }
        return item.completed ? "Vu" : libraryTypeLabel(item.contentType)
    }

    private func libraryTypeLabel(_ type: String) -> String {
        switch type {
        case "live": return "Chaîne TV"
        case "movie": return "Film"
        case "series": return "Série"
        case "episode": return "Épisode"
        default: return type.capitalized
        }
    }
}

private struct LibraryRow: View {
    let title: String
    let subtitle: String
    let artworkUrl: String?
    let progress: Double?

    var body: some View {
        HStack(spacing: 12) {
            SearchArtwork(
                urlString: artworkUrl,
                systemImage: "play.rectangle.fill"
            )
            .frame(width: 50, height: 58)

            VStack(alignment: .leading, spacing: 5) {
                Text(title)
                    .lineLimit(2)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)

                if let progress {
                    ProgressView(value: progress)
                        .tint(.red)
                }
            }
        }
    }
}


private struct DevicesSettingsView: View {
    @State private var devices: [AppleSyncedDeviceDTO] = []
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var editingId: String?
    @State private var editingName = ""
    @State private var busyId: String?

    private var currentDeviceUid: String {
        AppleDeviceIdentity.shared.deviceUid
    }

    var body: some View {
        List {
            if loading {
                HStack {
                    Spacer()
                    ProgressView("Chargement…")
                    Spacer()
                }
            } else if let errorMessage {
                VStack(alignment: .leading, spacing: 8) {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                    Button("Réessayer") {
                        Task { await reload() }
                    }
                }
            } else if devices.isEmpty {
                Text("Aucun appareil enregistré.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(devices) { device in
                    VStack(alignment: .leading, spacing: 10) {
                        if editingId == device.id {
                            TextField("Nom de l’appareil", text: $editingName)
                                .textFieldStyle(.roundedBorder)

                            HStack {
                                Button("Annuler") {
                                    editingId = nil
                                    editingName = ""
                                }
                                Button("Enregistrer") {
                                    Task { await saveRename(device) }
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(.red)
                                .disabled(editingName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                            }
                        } else {
                            HStack(alignment: .center, spacing: 12) {
                                Image(systemName: deviceIcon(device.platform))
                                    .font(.title2)
                                    .foregroundStyle(.red)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(device.displayName)
                                        .font(.headline)

                                    Text(deviceSubtitle(device))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }

                                Spacer()

                                Button {
                                    editingId = device.id
                                    editingName = device.displayName
                                } label: {
                                    Image(systemName: "pencil")
                                }
                                .buttonStyle(.plain)

                                Button(role: .destructive) {
                                    Task { await delete(device) }
                                } label: {
                                    Image(systemName: "trash")
                                }
                                .buttonStyle(.plain)
                                .disabled(
                                    device.deviceUid == currentDeviceUid ||
                                    busyId != nil
                                )
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.black)
        .navigationTitle("Appareils")
        .refreshable { await reload() }
        .task { await reload() }
    }

    @MainActor
    private func reload() async {
        loading = true
        errorMessage = nil
        do {
            try await SupabaseDeviceService.shared.registerCurrentDevice()
            devices = try await SupabaseDeviceService.shared.listDevices()
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }

    @MainActor
    private func saveRename(_ device: AppleSyncedDeviceDTO) async {
        busyId = device.id
        defer { busyId = nil }
        do {
            try await SupabaseDeviceService.shared.renameDevice(
                id: device.id,
                displayName: editingName
            )
            editingId = nil
            editingName = ""
            await reload()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func delete(_ device: AppleSyncedDeviceDTO) async {
        guard device.deviceUid != currentDeviceUid else { return }
        busyId = device.id
        defer { busyId = nil }
        do {
            try await SupabaseDeviceService.shared.deleteDevice(id: device.id)
            await reload()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func deviceIcon(_ platform: String) -> String {
        platform.contains("tablet") ? "ipad" : "iphone"
    }

    private func deviceSubtitle(_ device: AppleSyncedDeviceDTO) -> String {
        var parts = [device.platform.replacingOccurrences(of: "_", with: " ")]
        if let appVersion = device.appVersion, !appVersion.isEmpty {
            parts.append("v\(appVersion)")
        }
        if device.deviceUid == currentDeviceUid {
            parts.append("Cet appareil")
        }
        return parts.joined(separator: " · ")
    }
}

private struct PlaylistSettingsView: View {
    let onSignedOut: () -> Void

    @State private var playlists: [ProviderPlaylistDTO] = []
    @State private var loading = true
    @State private var busyId: String?
    @State private var errorMessage: String?
    @State private var showingAdd = false

    var body: some View {
        List {
            Section {
                HStack {
                    Text("\(playlists.count) / 10")
                        .foregroundStyle(.secondary)
                    Spacer()
                    Button("Ajouter") {
                        showingAdd = true
                    }
                    .disabled(playlists.count >= 10)
                }
            }

            if loading {
                HStack {
                    Spacer()
                    ProgressView("Chargement…")
                    Spacer()
                }
            } else if let errorMessage {
                VStack(alignment: .leading, spacing: 8) {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                    Button("Réessayer") {
                        Task { await reload() }
                    }
                }
            } else if playlists.isEmpty {
                Text("Aucune playlist configurée.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(playlists.sorted(by: { $0.priority < $1.priority })) { playlist in
                    playlistRow(playlist)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.black)
        .navigationTitle("Playlists")
        .task { await reload() }
        .sheet(isPresented: $showingAdd) {
            PlaylistOnboardingView(
                errorMessage: nil,
                onSaved: {
                    showingAdd = false
                    Task { await reload() }
                },
                onSignedOut: onSignedOut
            )
        }
    }

    @ViewBuilder
    private func playlistRow(_ playlist: ProviderPlaylistDTO) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(playlist.name)
                        .font(.headline)
                    Text(statusText(playlist))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Toggle(
                    "",
                    isOn: Binding(
                        get: { playlist.isEnabled },
                        set: { enabled in
                            Task { await setEnabled(playlist, enabled: enabled) }
                        }
                    )
                )
                .labelsHidden()
                .disabled(busyId == playlist.id)
            }

            HStack(spacing: 8) {
                Button {
                    Task { await move(playlist, offset: -1) }
                } label: {
                    Image(systemName: "arrow.up")
                }
                .disabled(
                    busyId != nil ||
                    playlist.priority <= (playlists.map(\.priority).min() ?? playlist.priority)
                )

                Button {
                    Task { await move(playlist, offset: 1) }
                } label: {
                    Image(systemName: "arrow.down")
                }
                .disabled(
                    busyId != nil ||
                    playlist.priority >= (playlists.map(\.priority).max() ?? playlist.priority)
                )

                Spacer()

                Button(role: .destructive) {
                    Task { await delete(playlist) }
                } label: {
                    Label("Supprimer", systemImage: "trash")
                }
                .disabled(busyId != nil)
            }
            .buttonStyle(.bordered)
        }
        .padding(.vertical, 4)
    }

    @MainActor
    private func reload() async {
        loading = true
        errorMessage = nil
        do {
            playlists = try await SupabasePlaylistService.shared.listPlaylists()
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }

    @MainActor
    private func setEnabled(_ playlist: ProviderPlaylistDTO, enabled: Bool) async {
        busyId = playlist.id
        defer { busyId = nil }
        do {
            try await SupabasePlaylistService.shared.setEnabled(id: playlist.id, enabled: enabled)
            await reload()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func move(_ playlist: ProviderPlaylistDTO, offset: Int) async {
        let ordered = playlists.sorted { $0.priority < $1.priority }
        guard let index = ordered.firstIndex(where: { $0.id == playlist.id }) else { return }
        let target = index + offset
        guard ordered.indices.contains(target) else { return }

        busyId = playlist.id
        defer { busyId = nil }

        let other = ordered[target]
        do {
            try await SupabasePlaylistService.shared.setPriority(
                id: playlist.id,
                priority: other.priority
            )
            try await SupabasePlaylistService.shared.setPriority(
                id: other.id,
                priority: playlist.priority
            )
            await reload()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func delete(_ playlist: ProviderPlaylistDTO) async {
        busyId = playlist.id
        defer { busyId = nil }
        do {
            try await SupabasePlaylistService.shared.delete(id: playlist.id)
            await reload()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func statusText(_ playlist: ProviderPlaylistDTO) -> String {
        let provider = playlist.providerType == "xtream" ? "Xtream Codes" : "M3U"
        let status: String
        if playlist.secretStatus != "configured" {
            status = "Identifiants invalides"
        } else if playlist.isEnabled {
            status = "À jour"
        } else {
            status = "Désactivée"
        }
        return "\(provider) · P\(playlist.priority) · \(status)"
    }
}

private struct ParentalSettingsView: View {
    @State private var loading = true
    @State private var busy = false
    @State private var account: ParentalAccountSettingsDTO?
    @State private var profiles: [PlayerProfileDTO] = []
    @State private var selectedProfileId: String?
    @State private var profileSettings: ProfileParentalSettingsDTO?
    @State private var catalog: ProviderCatalogDTO?
    @State private var lockedCategoryKeys: Set<String> = []
    @State private var lockedContentKeys: Set<String> = []
    @State private var contentLockQuery = ""

    @State private var currentPin = ""
    @State private var newPin = ""
    @State private var actionPin = ""
    @State private var maxAge: Int?
    @State private var hideLocked = false
    @State private var dailyLimit = ""
    @State private var weekendLimit = ""
    @State private var warningMinutes = "10"
    @State private var scheduleEnabled = false
    @State private var scheduleWindows: [ParentalScheduleWindowDTO] = []
    @State private var message: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if loading {
                    ProgressView("Chargement…").tint(.red)
                } else {
                    pinSection
                    if account?.hasPin == true {
                        accountSection
                        profileSection
                    }
                }

                if let message {
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(message.hasPrefix("Erreur") ? .red : .secondary)
                }
            }
            .padding(20)
        }
        .background(Color.black)
        .navigationTitle("Contrôle parental")
        .task { await loadAll() }
        .onChange(of: selectedProfileId) { _, _ in
            Task { await loadProfile() }
        }
    }

    private var pinSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(account?.hasPin == true ? "Modifier le PIN" : "Créer le PIN")
                .font(.title3.bold())

            if account?.hasPin == true {
                SecureField("PIN actuel", text: $currentPin)
                    .keyboardType(.numberPad)
                    .onChange(of: currentPin) { _, value in
                        currentPin = String(value.filter(\.isNumber).prefix(4))
                    }
                    .textFieldStyle(.roundedBorder)
            }

            SecureField("Nouveau PIN", text: $newPin)
                .keyboardType(.numberPad)
                .onChange(of: newPin) { _, value in
                    newPin = String(value.filter(\.isNumber).prefix(4))
                }
                .textFieldStyle(.roundedBorder)

            Button("Enregistrer le PIN") {
                Task { await savePin() }
            }
            .buttonStyle(.borderedProminent)
            .tint(.red)
            .disabled(
                busy ||
                newPin.count != 4 ||
                (account?.hasPin == true && currentPin.count != 4)
            )
        }
    }

    private var accountSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Compte").font(.title3.bold())

            SecureField("PIN pour confirmer", text: $actionPin)
                .keyboardType(.numberPad)
                .onChange(of: actionPin) { _, value in
                    actionPin = String(value.filter(\.isNumber).prefix(4))
                }
                .textFieldStyle(.roundedBorder)

            Toggle(
                "Contrôle parental activé",
                isOn: Binding(
                    get: { account?.enabled == true },
                    set: { requested in
                        Task { await setEnabled(requested) }
                    }
                )
            )
            .disabled(busy || actionPin.count != 4)
        }
    }

    private var profileSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Restrictions par profil")
                .font(.title3.bold())

            Picker("Profil", selection: Binding(
                get: { selectedProfileId ?? profiles.first?.id ?? "" },
                set: { selectedProfileId = $0 }
            )) {
                ForEach(profiles) { profile in
                    Text(profile.name).tag(profile.id)
                }
            }
            .pickerStyle(.menu)

            if let settings = profileSettings {
                if settings.isPrimary {
                    Text("Le profil principal reste sans restriction d’âge.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    Picker("Âge maximum", selection: Binding(
                        get: { maxAge ?? 0 },
                        set: { maxAge = $0 == 0 ? nil : $0 }
                    )) {
                        Text("Tous").tag(0)
                        ForEach([7, 10, 12, 16, 18], id: \.self) { age in
                            Text("\(age)+").tag(age)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Toggle("Masquer les contenus verrouillés", isOn: $hideLocked)

                TextField("Temps quotidien (minutes)", text: $dailyLimit)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)

                TextField("Limite week-end (minutes)", text: $weekendLimit)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)

                TextField("Avertir avant la fin (minutes)", text: $warningMinutes)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)

                Toggle(
                    "Plages horaires activées",
                    isOn: Binding(
                        get: { scheduleEnabled },
                        set: { requested in
                            scheduleEnabled = requested
                            if requested && scheduleWindows.isEmpty {
                                scheduleWindows = [
                                    ParentalScheduleWindowDTO(
                                        days: [1, 2, 3, 4, 5],
                                        start: "16:30",
                                        end: "19:30"
                                    )
                                ]
                            }
                        }
                    )
                )

                Button("Enregistrer les restrictions") {
                    Task { await saveProfile() }
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .disabled(busy || actionPin.count != 4)

                if settings.profileType == "child", let catalog {
                    Divider().padding(.vertical, 4)

                    Text("Verrouillages")
                        .font(.headline)

                    Text("\(lockedCategoryKeys.count) catégorie(s) · \(lockedContentKeys.count) contenu(s)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                    DisclosureGroup("Catégories") {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(lockCategories(catalog), id: \.key) { item in
                                Toggle(
                                    isOn: Binding(
                                        get: { lockedCategoryKeys.contains(item.key) },
                                        set: { checked in
                                            if checked {
                                                lockedCategoryKeys.insert(item.key)
                                            } else {
                                                lockedCategoryKeys.remove(item.key)
                                            }
                                        }
                                    )
                                ) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.name)
                                        Text(item.kind)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                        .padding(.top, 8)
                    }

                    TextField("Rechercher une chaîne, un film ou une série", text: $contentLockQuery)
                        .textFieldStyle(.roundedBorder)

                    if contentLockQuery.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 {
                        ForEach(lockContentResults(catalog), id: \.key) { item in
                            Toggle(
                                isOn: Binding(
                                    get: { lockedContentKeys.contains(item.key) },
                                    set: { checked in
                                        if checked {
                                            lockedContentKeys.insert(item.key)
                                        } else {
                                            lockedContentKeys.remove(item.key)
                                        }
                                    }
                                )
                            ) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.name)
                                    Text(item.kind)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }

                    Button("Enregistrer les verrouillages") {
                        Task { await saveLocks() }
                    }
                    .buttonStyle(.bordered)
                    .disabled(busy || actionPin.count != 4)
                }

            }
        }
    }

    @MainActor
    private func loadAll() async {
        loading = true
        message = nil
        do {
            async let settings = SupabaseParentalService.shared.accountSettings()
            async let loadedProfiles = SupabaseProfileService.shared.listProfiles()
            async let loadedCatalog = SupabaseProviderCatalogService.shared.loadCatalog(
                applyParentalFilters: false
            )
            account = try await settings
            profiles = try await loadedProfiles
            catalog = try await loadedCatalog
            if selectedProfileId == nil {
                selectedProfileId = profiles.first(where: { !$0.isPrimary })?.id ?? profiles.first?.id
            }
            await loadProfile()
        } catch {
            message = "Erreur : \(error.localizedDescription)"
        }
        loading = false
    }

    @MainActor
    private func loadProfile() async {
        guard let selectedProfileId else { return }
        do {
            async let loadedSettings = SupabaseParentalService.shared.profileSettings(
                profileId: selectedProfileId
            )
            async let loadedLocks = SupabaseParentalService.shared.contentLocks(
                profileId: selectedProfileId
            )
            let settings = try await loadedSettings
            let locks = try await loadedLocks
            profileSettings = settings
            lockedCategoryKeys = Set(locks.lockedCategoryKeys)
            lockedContentKeys = Set(locks.lockedContentKeys)
            maxAge = settings.maxAge
            hideLocked = settings.hideLocked
            dailyLimit = settings.dailyLimitMinutes?.description ?? ""
            weekendLimit = settings.weekendLimitMinutes?.description ?? ""
            warningMinutes = settings.warningMinutes.description
            scheduleEnabled = settings.scheduleEnabled
            scheduleWindows = settings.scheduleWindows
        } catch {
            message = "Erreur : \(error.localizedDescription)"
        }
    }

    @MainActor
    private func savePin() async {
        busy = true
        defer { busy = false }
        do {
            let result = try await SupabaseParentalService.shared.setPin(
                newPin: newPin,
                currentPin: account?.hasPin == true ? currentPin : nil
            )
            if result.success {
                currentPin = ""
                newPin = ""
                message = "PIN enregistré."
                account = try await SupabaseParentalService.shared.accountSettings()
            } else {
                message = "Erreur : \(reasonMessage(result.reason))"
            }
        } catch {
            message = "Erreur : \(error.localizedDescription)"
        }
    }

    @MainActor
    private func setEnabled(_ enabled: Bool) async {
        guard actionPin.count == 4 else {
            message = "Erreur : saisissez le PIN."
            return
        }
        busy = true
        defer { busy = false }
        do {
            let result = try await SupabaseParentalService.shared.setEnabled(
                pin: actionPin,
                enabled: enabled
            )
            if result.success {
                account = try await SupabaseParentalService.shared.accountSettings()
                actionPin = ""
                message = enabled ? "Contrôle parental activé." : "Contrôle parental désactivé."
            } else {
                message = "Erreur : \(reasonMessage(result.reason))"
            }
        } catch {
            message = "Erreur : \(error.localizedDescription)"
        }
    }

    @MainActor
    private func saveProfile() async {
        guard let selectedProfileId else { return }
        busy = true
        defer { busy = false }
        do {
            let result = try await SupabaseParentalService.shared.updateProfileSettings(
                profileId: selectedProfileId,
                pin: actionPin,
                maxAge: profileSettings?.isPrimary == true ? nil : maxAge,
                hideLocked: hideLocked,
                dailyLimitMinutes: Int(dailyLimit),
                weekendLimitMinutes: Int(weekendLimit),
                warningMinutes: Int(warningMinutes) ?? 10,
                scheduleEnabled: scheduleEnabled,
                scheduleWindows: scheduleEnabled ? scheduleWindows : []
            )
            if result.success {
                actionPin = ""
                message = "Restrictions mises à jour."
                await loadProfile()
            } else {
                message = "Erreur : \(reasonMessage(result.reason))"
            }
        } catch {
            message = "Erreur : \(error.localizedDescription)"
        }
    }


    @MainActor
    private func saveLocks() async {
        guard let selectedProfileId else { return }
        busy = true
        defer { busy = false }

        do {
            let result = try await SupabaseParentalService.shared.updateContentLocks(
                profileId: selectedProfileId,
                pin: actionPin,
                lockedCategoryKeys: lockedCategoryKeys.sorted(),
                lockedContentKeys: lockedContentKeys.sorted()
            )
            if result.success {
                actionPin = ""
                message = "Verrouillages mis à jour."
                await loadProfile()
            } else {
                message = "Erreur : \(reasonMessage(result.reason))"
            }
        } catch {
            message = "Erreur : \(error.localizedDescription)"
        }
    }

    private struct LockEditorItem {
        let key: String
        let name: String
        let kind: String
    }

    private func lockCategories(_ catalog: ProviderCatalogDTO) -> [LockEditorItem] {
        let live = catalog.liveCategories.map {
            LockEditorItem(key: "live:" + $0.key, name: $0.value, kind: "TV")
        }
        let movies = catalog.movieCategories.map {
            LockEditorItem(key: "movie:" + $0.key, name: $0.value, kind: "Films")
        }
        let series = catalog.seriesCategories.map {
            LockEditorItem(key: "series:" + $0.key, name: $0.value, kind: "Séries")
        }
        return (live + movies + series).sorted {
            $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
        }
    }

    private func lockContentResults(_ catalog: ProviderCatalogDTO) -> [LockEditorItem] {
        let query = contentLockQuery
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .lowercased()
        guard query.count >= 2 else { return [] }

        let live = Array(
            catalog.liveChannels.lazy
                .filter { normalized($0.name).contains(query) }
                .prefix(40)
                .map { LockEditorItem(key: "live:" + $0.id, name: $0.name, kind: "Chaîne TV") }
        )
        let movies = Array(
            catalog.movies.lazy
                .filter { normalized($0.title).contains(query) }
                .prefix(40)
                .map { LockEditorItem(key: "movie:" + $0.id, name: $0.title, kind: "Film") }
        )
        let series = Array(
            catalog.series.lazy
                .filter { normalized($0.title).contains(query) }
                .prefix(40)
                .map { LockEditorItem(key: "series:" + $0.id, name: $0.title, kind: "Série") }
        )

        return Array((live + movies + series).prefix(80))
    }

    private func normalized(_ value: String) -> String {
        value
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .lowercased()
    }

    private func reasonMessage(_ reason: String?) -> String {
        switch reason {
        case "invalid_format": return "Le PIN doit contenir 4 chiffres."
        case "current_pin_invalid": return "PIN actuel incorrect."
        case "pin_invalid": return "PIN incorrect."
        case "pin_not_configured": return "Configurez d’abord un PIN."
        case "blocked": return "Trop de tentatives. Réessayez plus tard."
        case "invalid_age": return "Restriction d’âge invalide."
        case "invalid_limit": return "Limite de temps invalide."
        case "invalid_warning": return "Avertissement invalide."
        case "primary_unrestricted": return "Le profil principal reste sans restriction d’âge."
        default: return "Modification impossible."
        }
    }
}

#Preview {
    MainTabView(
        profile: PlayerProfileDTO(
            id: "preview",
            name: "Profil principal",
            avatarKey: "avatar_01",
            profileType: "standard",
            maxAge: nil,
            isPrimary: true
        ),
        onSignedOut: {},
        onSwitchProfile: {}
    )
}
