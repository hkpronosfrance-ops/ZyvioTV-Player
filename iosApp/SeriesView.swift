import SwiftUI

struct SeriesView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var series: [ProviderSeriesDTO] = []
    @State private var progressBySeriesId: [String: Double] = [:]
    @State private var selectedSeries: ProviderSeriesDTO?
    @State private var isLoading = true
    @State private var errorMessage: String?

    private var columns: [GridItem] {
        let count = horizontalSizeClass == .regular ? 5 : 2
        return Array(repeating: GridItem(.flexible(), spacing: 14), count: count)
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView((Locale.current.language.languageCode?.identifier == "fr" ? "Chargement des séries…" : "Loading series…"))
                        .tint(.red)
                } else if let errorMessage {
                    ContentUnavailableView {
                        Label((Locale.current.language.languageCode?.identifier == "fr" ? "Catalogue indisponible" : "Catalog unavailable"), systemImage: "exclamationmark.triangle")
                    } description: {
                        Text(errorMessage)
                    } actions: {
                        Button((Locale.current.language.languageCode?.identifier == "fr" ? "Réessayer" : "Try again")) {
                            Task { await reload() }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                    }
                } else if series.isEmpty {
                    ContentUnavailableView(
                        "Aucune série disponible",
                        systemImage: "rectangle.stack",
                        description: Text((Locale.current.language.languageCode?.identifier == "fr" ? "Cette playlist ne contient pas de catalogue Séries." : "This playlist has no series catalog."))
                    )
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 18) {
                            ForEach(series) { item in
                                Button {
                                    selectedSeries = item
                                } label: {
                                    SeriesCard(
                                        item: item,
                                        progress: progressBySeriesId[item.id]
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(horizontalSizeClass == .regular ? 28 : 16)
                        .padding(.vertical, 18)
                    }
                    .refreshable {
                        await reload()
                    }
                }
            }
            .background(ZyvioDesign.Palette.base)
            .navigationTitle("Séries")
            .preferredColorScheme(.dark)
            .navigationDestination(item: $selectedSeries) { item in
                SeriesDetailView(series: item)
            }
        }
        .task {
            await reload()
        }
    }

    @MainActor
    private func reload() async {
        isLoading = true
        errorMessage = nil

        do {
            async let catalogTask = SupabaseProviderCatalogService.shared.loadCatalog()
            async let progressTask = SupabaseLibrarySyncService.shared.listWatchProgress(limit: 200)

            let catalog = try await catalogTask
            let progress = try await progressTask

            series = catalog.series

            var values: [String: Double] = [:]
            let episodes = progress.filter {
                $0.playlistId == catalog.playlistId &&
                $0.contentType == "episode" &&
                $0.seriesId != nil
            }

            for item in episodes {
                guard let seriesId = item.seriesId else { continue }

                if item.completed {
                    if values[seriesId] == nil {
                        values[seriesId] = 1
                    }
                    continue
                }

                if let duration = item.durationMs, duration > 0, item.positionMs > 0 {
                    values[seriesId] = min(
                        max(Double(item.positionMs) / Double(duration), 0),
                        1
                    )
                } else if item.positionMs > 0 {
                    values[seriesId] = 0.05
                }
            }

            progressBySeriesId = values
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}

private struct SeriesCard: View {
    let item: ProviderSeriesDTO
    let progress: Double?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.07))

                if let posterUrl = item.posterUrl,
                   let url = URL(string: posterUrl) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .scaledToFill()
                        default:
                            Image(systemName: "rectangle.stack.fill")
                                .foregroundStyle(.red)
                        }
                    }
                } else {
                    Image(systemName: "rectangle.stack.fill")
                        .foregroundStyle(.red)
                }
            }
            .aspectRatio(2 / 3, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 16))

            Text(item.title)
                .font(.headline)
                .foregroundStyle(.white)
                .lineLimit(2)

            if let progress, progress > 0 {
                ProgressView(value: progress)
                    .tint(.red)
            }
        }
    }
}

struct SeriesDetailView: View {
    let series: ProviderSeriesDTO

    @State private var detail: ProviderSeriesDetailDTO?
    @State private var progressByEpisodeId: [String: SyncedWatchProgressDTO] = [:]
    @State private var selectedSeason: Int?
    @State private var playingEpisode: ProviderSeriesEpisodeDTO?
    @State private var isLoading = true
    @State private var errorMessage: String?

    private var seasons: [Int] {
        Array(Set(detail?.episodes.map(\.season) ?? [])).sorted()
    }

    private var visibleEpisodes: [ProviderSeriesEpisodeDTO] {
        guard let selectedSeason else { return [] }
        return detail?.episodes.filter { $0.season == selectedSeason } ?? []
    }

    var body: some View {
        Group {
            if isLoading {
                ProgressView((Locale.current.language.languageCode?.identifier == "fr" ? "Chargement des épisodes…" : "Loading episodes…"))
                    .tint(.red)
            } else if let errorMessage {
                ContentUnavailableView {
                    Label((Locale.current.language.languageCode?.identifier == "fr" ? "Épisodes indisponibles" : "Episodes unavailable"), systemImage: "exclamationmark.triangle")
                } description: {
                    Text(errorMessage)
                } actions: {
                    Button((Locale.current.language.languageCode?.identifier == "fr" ? "Réessayer" : "Try again")) {
                        Task { await reload() }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                }
            } else if let detail {
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        HStack(alignment: .top, spacing: 18) {
                            SeriesPoster(urlString: series.posterUrl)
                                .frame(width: 150, height: 225)

                            VStack(alignment: .leading, spacing: 10) {
                                Text(detail.title ?? series.title)
                                    .font(.largeTitle.bold())

                                if let year = detail.year {
                                    Text(year)
                                        .foregroundStyle(.secondary)
                                }

                                if !detail.genres.isEmpty {
                                    Text(detail.genres.joined(separator: " • "))
                                        .foregroundStyle(.secondary)
                                }

                                if let synopsis = detail.synopsis, !synopsis.isEmpty {
                                    Text(synopsis)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(6)
                                }
                            }
                        }

                        if !seasons.isEmpty {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 10) {
                                    ForEach(seasons, id: \.self) { season in
                                        Button("Saison \(season)") {
                                            selectedSeason = season
                                        }
                                        .buttonStyle(.borderedProminent)
                                        .tint(selectedSeason == season ? .red : .gray.opacity(0.35))
                                    }
                                }
                            }
                        }

                        VStack(spacing: 12) {
                            ForEach(visibleEpisodes) { episode in
                                Button {
                                    playingEpisode = episode
                                } label: {
                                    EpisodeRow(
                                        episode: episode,
                                        progress: progressByEpisodeId[episode.id]
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(20)
                }
            } else {
                ContentUnavailableView(
                    "Aucun épisode disponible",
                    systemImage: "rectangle.stack.badge.play"
                )
            }
        }
        .background(ZyvioDesign.Palette.base)
        .navigationTitle(series.title)
        .navigationBarTitleDisplayMode(.inline)
        .preferredColorScheme(.dark)
        .task {
            await reload()
        }
        .fullScreenCover(item: $playingEpisode) { episode in
            EpisodePlayerScreen(
                series: series,
                episode: episode,
                allEpisodes: detail?.episodes ?? [],
                existingProgress: progressByEpisodeId[episode.id],
                onProgressSaved: { updated in
                    progressByEpisodeId[episode.id] = updated
                },
                onPlayNext: { next in
                    playingEpisode = next
                }
            )
        }
    }

    @MainActor
    private func reload() async {
        isLoading = true
        errorMessage = nil

        do {
            async let detailTask = SupabaseProviderCatalogService.shared.loadSeriesDetail(
                playlistId: series.playlistId,
                seriesId: series.id
            )
            async let progressTask = SupabaseLibrarySyncService.shared.listWatchProgress(limit: 200)

            let loadedDetail = try await detailTask
            let progress = try await progressTask

            detail = loadedDetail
            selectedSeason = selectedSeason ?? loadedDetail.episodes.first?.season
            progressByEpisodeId = Dictionary(
                uniqueKeysWithValues: progress
                    .filter {
                        $0.playlistId == series.playlistId &&
                        $0.contentType == "episode" &&
                        $0.seriesId == series.id
                    }
                    .map { ($0.contentId, $0) }
            )
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}

private struct SeriesPoster: View {
    let urlString: String?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.07))

            if let urlString, let url = URL(string: urlString) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        Image(systemName: "rectangle.stack.fill")
                            .foregroundStyle(.red)
                    }
                }
            } else {
                Image(systemName: "rectangle.stack.fill")
                    .foregroundStyle(.red)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct EpisodeRow: View {
    let episode: ProviderSeriesEpisodeDTO
    let progress: SyncedWatchProgressDTO?

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.06))
                Image(systemName: "play.fill")
                    .foregroundStyle(.red)
            }
            .frame(width: 112, height: 68)

            VStack(alignment: .leading, spacing: 6) {
                Text("S\(episode.season) E\(episode.number) — \(episode.title)")
                    .font(.headline)
                    .foregroundStyle(.white)
                    .lineLimit(2)

                if let synopsis = episode.synopsis, !synopsis.isEmpty {
                    Text(synopsis)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }

                if let progress,
                   let duration = progress.durationMs,
                   duration > 0,
                   !progress.completed {
                    ProgressView(
                        value: min(
                            max(Double(progress.positionMs) / Double(duration), 0),
                            1
                        )
                    )
                    .tint(.red)
                } else if progress?.completed == true {
                    Label((Locale.current.language.languageCode?.identifier == "fr" ? "Vu" : "Watched"), systemImage: "checkmark.circle.fill")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()
        }
        .padding(12)
        .background(Color.white.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct EpisodePlayerScreen: View {
    let series: ProviderSeriesDTO
    let episode: ProviderSeriesEpisodeDTO
    let allEpisodes: [ProviderSeriesEpisodeDTO]
    let existingProgress: SyncedWatchProgressDTO?
    let onProgressSaved: (SyncedWatchProgressDTO) -> Void
    let onPlayNext: (ProviderSeriesEpisodeDTO) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var errorMessage: String?
    @State private var hasAdvanced = false

    private var resumeSeconds: Double {
        guard let progress = existingProgress, !progress.completed else { return 0 }
        let milliseconds = progress.positionMs
        guard milliseconds > 0 else { return 0 }
        if let duration = progress.durationMs, duration > 0,
           Double(milliseconds) / Double(duration) >= 0.95 { return 0 }
        return max(Double(milliseconds) / 1_000.0 - 5.0, 0)
    }

    private var nextEpisode: ProviderSeriesEpisodeDTO? {
        // Provider order can differ from chronological season/episode order.
        let ordered = allEpisodes.sorted {
            if $0.season != $1.season { return $0.season < $1.season }
            return $0.number < $1.number
        }
        guard let index = ordered.firstIndex(where: { $0.id == episode.id }) else {
            return nil
        }
        let nextIndex = ordered.index(after: index)
        return nextIndex < ordered.endIndex ? ordered[nextIndex] : nil
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            ZyvioDesign.Palette.base.ignoresSafeArea()

            ParentalProtectedPlayerView(
                title: series.title + " — S\(episode.season) E\(episode.number)",
                streamURL: episode.streamUrl,
                resumePositionSeconds: resumeSeconds,
                playbackKind: "episode",
                onPositionChanged: { position, duration in
                    saveProgress(
                        positionSeconds: position,
                        durationSeconds: duration,
                        completed: false
                    )
                },
                onEnded: { position, duration in
                    saveProgress(
                        positionSeconds: position,
                        durationSeconds: duration,
                        completed: true
                    )
                    advanceIfPossible()
                },
                onError: { message in
                    errorMessage = message
                }
            )
            .ignoresSafeArea()

            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.headline)
                    .padding(12)
                    .background(.black.opacity(0.65))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(.white)
            .padding(18)

            if let errorMessage {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(.red)
                    Text((Locale.current.language.languageCode?.identifier == "fr" ? "Lecture impossible" : "Playback unavailable"))
                        .font(.title2.bold())
                    Text(errorMessage)
                        .foregroundStyle(.secondary)

                    if let nextEpisode {
                        Button((Locale.current.language.languageCode?.identifier == "fr" ? "Épisode suivant" : "Next episode")) {
                            onPlayNext(nextEpisode)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                    }

                    Button((Locale.current.language.languageCode?.identifier == "fr" ? "Fermer" : "Close")) {
                        dismiss()
                    }
                    .buttonStyle(.bordered)
                }
                .padding(ZyvioDesign.Space.s6)
                .background(ZyvioDesign.Palette.surface1.opacity(0.96))
                .clipShape(RoundedRectangle(cornerRadius: 20))
            }
        }
        .preferredColorScheme(.dark)
    }

    private func advanceIfPossible() {
        guard !hasAdvanced, let nextEpisode else { return }
        hasAdvanced = true
        onPlayNext(nextEpisode)
    }

    private func saveProgress(
        positionSeconds: Double,
        durationSeconds: Double?,
        completed: Bool
    ) {
        let positionMs = Int64(max(positionSeconds, 0) * 1_000)
        let durationMs = durationSeconds.map { Int64(max($0, 0) * 1_000) }
        let effectiveCompleted =
            completed ||
            (
                durationMs != nil &&
                durationMs! > 0 &&
                Double(positionMs) / Double(durationMs!) >= 0.95
            )

        let value = SyncedWatchProgressDTO(
            playlistId: series.playlistId,
            contentType: "episode",
            contentId: episode.id,
            title: episode.title,
            seriesId: series.id,
            seasonNumber: episode.season,
            episodeNumber: episode.number,
            artworkUrl: series.posterUrl,
            positionMs: positionMs,
            durationMs: durationMs,
            completed: effectiveCompleted
        )

        onProgressSaved(value)

        Task {
            try? await SupabaseLibrarySyncService.shared.upsertWatchProgress(value)
        }
    }
}

#Preview {
    SeriesView()
}
