import SwiftUI

struct MoviesView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var movies: [ProviderMovieDTO] = []
    @State private var progressById: [String: SyncedWatchProgressDTO] = [:]
    @State private var selectedMovie: ProviderMovieDTO?
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
                    ProgressView((Locale.current.language.languageCode?.identifier == "fr" ? "Chargement des films…" : "Loading movies…"))
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
                } else if movies.isEmpty {
                    ContentUnavailableView(
                        "Aucun film disponible",
                        systemImage: "film",
                        description: Text((Locale.current.language.languageCode?.identifier == "fr" ? "Cette playlist ne contient pas de catalogue Films." : "This playlist has no movie catalog."))
                    )
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 18) {
                            ForEach(movies) { movie in
                                Button {
                                    selectedMovie = movie
                                } label: {
                                    MovieCard(
                                        movie: movie,
                                        progress: progressById[movie.id]
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
            .background(Color.black)
            .navigationTitle("Films")
            .preferredColorScheme(.dark)
        }
        .task {
            await reload()
        }
        .fullScreenCover(item: $selectedMovie) { movie in
            MoviePlayerScreen(
                movie: movie,
                existingProgress: progressById[movie.id],
                onProgressSaved: { updated in
                    progressById[movie.id] = updated
                }
            )
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

            movies = catalog.movies
            progressById = Dictionary(
                uniqueKeysWithValues: progress
                    .filter {
                        $0.playlistId == catalog.playlistId &&
                        $0.contentType == "movie"
                    }
                    .map { ($0.contentId, $0) }
            )
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}

private struct MovieCard: View {
    let movie: ProviderMovieDTO
    let progress: SyncedWatchProgressDTO?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.07))

                if let posterUrl = movie.posterUrl,
                   let url = URL(string: posterUrl) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .scaledToFill()
                        default:
                            Image(systemName: "film.fill")
                                .foregroundStyle(.red)
                        }
                    }
                } else {
                    Image(systemName: "film.fill")
                        .foregroundStyle(.red)
                }
            }
            .aspectRatio(2 / 3, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 16))

            Text(movie.title)
                .font(.headline)
                .foregroundStyle(.white)
                .lineLimit(2)

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
            }
        }
    }
}

struct MoviePlayerScreen: View {
    let movie: ProviderMovieDTO
    let existingProgress: SyncedWatchProgressDTO?
    let onProgressSaved: (SyncedWatchProgressDTO) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var errorMessage: String?

    private var resumeSeconds: Double {
        let milliseconds = existingProgress?.positionMs ?? 0
        guard milliseconds > 0 else { return 0 }
        return max(Double(milliseconds) / 1_000.0 - 5.0, 0)
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()

            ParentalProtectedPlayerView(
                title: movie.title,
                streamURL: movie.streamUrl,
                resumePositionSeconds: resumeSeconds,
                playbackKind: "movie",
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
                    Button((Locale.current.language.languageCode?.identifier == "fr" ? "Fermer" : "Close")) {
                        dismiss()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                }
                .padding(24)
                .background(Color.black.opacity(0.92))
                .clipShape(RoundedRectangle(cornerRadius: 20))
            }
        }
        .preferredColorScheme(.dark)
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
            playlistId: movie.playlistId,
            contentType: "movie",
            contentId: movie.id,
            title: movie.title,
            seriesId: nil,
            seasonNumber: nil,
            episodeNumber: nil,
            artworkUrl: movie.posterUrl,
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
    MoviesView()
}
