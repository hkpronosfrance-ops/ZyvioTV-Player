import SwiftUI

struct HomeView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var progress: [SyncedWatchProgressDTO] = []
    @State private var favorites: [SyncedFavoriteDTO] = []
    @State private var isLoading = true
    @State private var errorMessage: String?

    private var continueWatching: [SyncedWatchProgressDTO] {
        progress
            .filter { !$0.completed && $0.positionMs > 0 }
            .prefix(20)
            .map { $0 }
    }

    private var nextEpisodes: [SyncedWatchProgressDTO] {
        progress
            .filter {
                $0.contentType == "episode" &&
                !$0.completed &&
                $0.seriesId != nil
            }
            .prefix(20)
            .map { $0 }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                HomeHero(
                    item: continueWatching.first,
                    isWide: horizontalSizeClass == .regular
                )

                if isLoading {
                    HStack {
                        Spacer()
                        ProgressView("Chargement de votre bibliothèque…")
                            .tint(.red)
                        Spacer()
                    }
                    .padding(.vertical, 30)
                } else if let errorMessage {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Impossible de charger l’Accueil")
                            .font(.title3.bold())
                        Text(errorMessage)
                            .foregroundStyle(.secondary)
                        Button("Réessayer") {
                            Task { await reload() }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                    }
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white.opacity(0.05))
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                } else {
                    if !continueWatching.isEmpty {
                        ProgressShelf(
                            title: "Continuer à regarder",
                            items: continueWatching
                        )
                    }

                    if !nextEpisodes.isEmpty {
                        ProgressShelf(
                            title: "Prochains épisodes",
                            items: nextEpisodes
                        )
                    }

                    if !favorites.isEmpty {
                        FavoriteShelf(
                            title: "Favoris",
                            items: Array(favorites.prefix(20))
                        )
                    }

                    if continueWatching.isEmpty && favorites.isEmpty {
                        EmptyHomeState()
                    }
                }
            }
            .padding(.horizontal, horizontalSizeClass == .regular ? ZyvioDesign.Space.s8 : ZyvioDesign.Space.s5)
            .padding(.vertical, 20)
        }
        .background(ZyvioDesign.Palette.base)
        .preferredColorScheme(.dark)
        .refreshable {
            await reload()
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
            async let progressTask = SupabaseLibrarySyncService.shared.listWatchProgress(limit: 100)
            async let favoritesTask = SupabaseLibrarySyncService.shared.listFavorites()

            progress = try await progressTask
            favorites = try await favoritesTask
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}

private struct HomeHero: View {
    let item: SyncedWatchProgressDTO?
    let isWide: Bool

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            if let artwork = item?.artworkUrl, let url = URL(string: artwork) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    ZyvioDesign.Palette.surface2
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
                .accessibilityHidden(true)
            }

            LinearGradient(
                colors: [ZyvioDesign.Palette.brand.opacity(0.92), ZyvioDesign.Palette.base.opacity(0.95), ZyvioDesign.Palette.base.opacity(0.45)],
                startPoint: .leading,
                endPoint: .trailing
            )

            VStack(alignment: .leading, spacing: 10) {
                Text("ZYVIOTV")
                    .font(.system(size: isWide ? 34 : 28, weight: .black))
                Text("PLAYER")
                    .font(.caption.bold())
                    .tracking(6)
                    .foregroundStyle(.red)

                if let item {
                    Text(item.title)
                        .font(isWide ? .largeTitle.bold() : .title.bold())
                        .lineLimit(2)

                    if let fraction = progressFraction(item) {
                        ProgressView(value: fraction)
                            .tint(.red)
                            .frame(maxWidth: isWide ? 480 : .infinity)
                    }

                    Text("Reprenez votre lecture là où vous l’avez arrêtée.")
                        .foregroundStyle(.secondary)
                } else {
                    Text("Tout votre univers au même endroit.")
                        .font(isWide ? .largeTitle.bold() : .title2.bold())
                    Text("Vos favoris et votre progression se synchronisent avec votre compte ZYVIOTV.")
                        .foregroundStyle(.secondary)
                }
            }
            .padding(isWide ? 32 : 24)
        }
        .frame(height: isWide ? 330 : 252)
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }
}

private struct ProgressShelf: View {
    let title: String
    let items: [SyncedWatchProgressDTO]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.title3.bold())

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: ZyvioDesign.Space.s3) {
                    ForEach(items) { item in
                        VStack(alignment: .leading, spacing: 8) {
                            AsyncArtwork(
                                urlString: item.artworkUrl,
                                fallbackSystemImage: item.contentType == "episode" ? "rectangle.stack.fill" : "film.fill"
                            )
                            .frame(width: 180, height: 105)

                            Text(item.title)
                                .font(.headline)
                                .lineLimit(1)
                                .frame(width: 180, alignment: .leading)

                            if let fraction = progressFraction(item) {
                                ProgressView(value: fraction)
                                    .tint(.red)
                                    .frame(width: 180)
                            }

                            if let season = item.seasonNumber,
                               let episode = item.episodeNumber {
                                Text("S\(season) E\(episode)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private struct FavoriteShelf: View {
    let title: String
    let items: [SyncedFavoriteDTO]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.title3.bold())

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(items) { item in
                        VStack(alignment: .leading, spacing: 8) {
                            AsyncArtwork(
                                urlString: item.artworkUrl,
                                fallbackSystemImage: favoriteIcon(item.contentType)
                            )
                            .frame(width: 150, height: 220)

                            Text(item.title)
                                .font(.headline)
                                .lineLimit(2)
                                .frame(width: 150, alignment: .leading)
                        }
                    }
                }
            }
        }
    }

    private func favoriteIcon(_ type: String) -> String {
        switch type {
        case "series": return "rectangle.stack.fill"
        case "live": return "tv.fill"
        default: return "film.fill"
        }
    }
}

private struct AsyncArtwork: View {
    let urlString: String?
    let fallbackSystemImage: String

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.07))

            if let urlString,
               let url = URL(string: urlString) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    default:
                        Image(systemName: fallbackSystemImage)
                            .foregroundStyle(.red)
                    }
                }
            } else {
                Image(systemName: fallbackSystemImage)
                    .foregroundStyle(.red)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct EmptyHomeState: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Votre Accueil est prêt")
                .font(.title3.bold())
            Text("Commencez à regarder un contenu ou ajoutez-le aux favoris pour le retrouver ici.")
                .foregroundStyle(.secondary)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

private func progressFraction(_ item: SyncedWatchProgressDTO) -> Double? {
    guard let duration = item.durationMs, duration > 0 else {
        return nil
    }

    return min(
        max(Double(item.positionMs) / Double(duration), 0),
        1
    )
}

#Preview {
    HomeView()
}
