import SwiftUI

struct SeriesView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var series: [ProviderSeriesDTO] = []
    @State private var progressBySeriesId: [String: Double] = [:]
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
                    ProgressView("Chargement des séries…")
                        .tint(.red)
                } else if let errorMessage {
                    ContentUnavailableView {
                        Label("Catalogue indisponible", systemImage: "exclamationmark.triangle")
                    } description: {
                        Text(errorMessage)
                    } actions: {
                        Button("Réessayer") {
                            Task { await reload() }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                    }
                } else if series.isEmpty {
                    ContentUnavailableView(
                        "Aucune série disponible",
                        systemImage: "rectangle.stack",
                        description: Text("Cette playlist ne contient pas de catalogue Séries.")
                    )
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 18) {
                            ForEach(series) { item in
                                SeriesCard(
                                    item: item,
                                    progress: progressBySeriesId[item.id]
                                )
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
            .navigationTitle("Séries")
            .preferredColorScheme(.dark)
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

#Preview {
    SeriesView()
}
