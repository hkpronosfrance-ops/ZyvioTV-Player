import SwiftUI

struct LiveTvView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var channels: [ProviderLiveChannelDTO] = []
    @State private var categories: [String: String] = [:]
    @State private var selectedCategory: String?
    @State private var selectedId: String?
    @State private var guide: [String: [ProviderEpgProgrammeDTO]] = [:]
    @State private var playing: ProviderLiveChannelDTO?
    @State private var loading = true
    @State private var error: String?
    @State private var showGuide = false

    private var visible: [ProviderLiveChannelDTO] {
        guard let selectedCategory else { return channels }
        return channels.filter { $0.categoryId == selectedCategory }
    }

    private var selected: ProviderLiveChannelDTO? {
        visible.first { $0.id == selectedId } ?? visible.first
    }

    var body: some View {
        NavigationStack {
            Group {
                if loading {
                    ProgressView((Locale.current.language.languageCode?.identifier == "fr" ? "Chargement des chaînes…" : "Loading channels…")).tint(.red)
                } else if let error {
                    ContentUnavailableView {
                        Label("TV indisponible", systemImage: "exclamationmark.triangle")
                    } description: {
                        Text(error)
                    } actions: {
                        Button("Réessayer") { Task { await load() } }
                            .buttonStyle(.borderedProminent)
                            .tint(.red)
                    }
                } else if channels.isEmpty {
                    ContentUnavailableView("Aucune chaîne disponible", systemImage: "tv")
                } else if sizeClass == .regular {
                    HStack(spacing: 20) {
                        VStack(spacing: 12) {
                            categoryBar
                            ScrollView { channelList }
                        }
                        .frame(width: 380)

                        selectedPanel
                    }
                    .padding(20)
                } else {
                    ScrollView {
                        VStack(spacing: 16) {
                            categoryBar
                            selectedPanel
                            channelList
                        }
                        .padding(16)
                    }
                    .refreshable { await load() }
                }
            }
            .background(Color.black)
            .navigationTitle((Locale.current.language.languageCode?.identifier == "fr" ? "TV en direct" : "Live TV"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button((Locale.current.language.languageCode?.identifier == "fr" ? "Guide TV" : "TV guide")) { showGuide = true }
                }
            }
            .sheet(isPresented: $showGuide) {
                GuideList(
                    channels: channels,
                    guide: guide,
                    onTune: {
                        showGuide = false
                        playing = $0
                    }
                )
            }
        }
        .preferredColorScheme(.dark)
        .task { await load() }
        .fullScreenCover(item: $playing) { LivePlayer(channel: $0) }
    }

    private var categoryBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack {
                Button((Locale.current.language.languageCode?.identifier == "fr" ? "Toutes" : "All")) {
                    selectedCategory = nil
                    selectedId = channels.first?.id
                }
                .buttonStyle(.borderedProminent)
                .tint(selectedCategory == nil ? .red : .gray.opacity(0.35))

                ForEach(categories.sorted(by: { $0.value < $1.value }), id: \.key) { id, name in
                    Button(name) {
                        selectedCategory = id
                        selectedId = channels.first { $0.categoryId == id }?.id
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(selectedCategory == id ? .red : .gray.opacity(0.35))
                }
            }
        }
    }

    private var channelList: some View {
        LazyVStack(spacing: 10) {
            ForEach(visible) { channel in
                Button {
                    selectedId = channel.id
                } label: {
                    HStack(spacing: 12) {
                        ChannelLogo(channel: channel)
                            .frame(width: 52, height: 52)

                        VStack(alignment: .leading, spacing: 4) {
                            Text(channel.name)
                                .bold()
                                .foregroundStyle(.white)
                                .lineLimit(1)

                            Text(now(for: channel.id)?.title ?? "Guide indisponible")
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }

                        Spacer()
                    }
                    .padding(12)
                    .background(Color.white.opacity(selected?.id == channel.id ? 0.10 : 0.05))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private var selectedPanel: some View {
        if let channel = selected {
            VStack(alignment: .leading, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 22)
                        .fill(
                            LinearGradient(
                                colors: [Color.white.opacity(0.1), .black],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )

                    VStack(spacing: 12) {
                        ChannelLogo(channel: channel)
                            .frame(width: 120, height: 90)

                        Text(channel.name)
                            .font(.title2.bold())

                        Button {
                            playing = channel
                        } label: {
                            Label((Locale.current.language.languageCode?.identifier == "fr" ? "Regarder" : "Watch"), systemImage: "play.fill")
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                    }
                }
                .frame(minHeight: sizeClass == .regular ? 360 : 260)

                if let current = now(for: channel.id) {
                    Text((Locale.current.language.languageCode?.identifier == "fr" ? "Maintenant" : "Now"))
                        .foregroundStyle(.red)
                        .bold()

                    Text(current.title)
                        .font(.title3.bold())

                    if let description = current.description {
                        Text(description)
                            .foregroundStyle(.secondary)
                            .lineLimit(3)
                    }

                    ProgressView(value: progress(current))
                        .tint(.red)
                } else {
                    Text((Locale.current.language.languageCode?.identifier == "fr" ? "Programme en cours indisponible" : "Current program unavailable"))
                        .foregroundStyle(.secondary)
                }

                if let next = next(for: channel.id) {
                    Text("À suivre : \(next.title)")
                        .foregroundStyle(.secondary)
                }

                Spacer()
            }
        }
    }

    @MainActor
    private func load() async {
        loading = true
        error = nil

        do {
            let catalog = try await SupabaseProviderCatalogService.shared.loadCatalog()
            channels = catalog.liveChannels
            categories = catalog.liveCategories
            selectedId = selectedId ?? channels.first?.id

            let data = try await SupabaseProviderCatalogService.shared.loadGuide(
                playlistId: catalog.playlistId,
                channels: channels
            )

            guide = Dictionary(
                uniqueKeysWithValues: data.map { ($0.channel.id, $0.programmes) }
            )
        } catch {
            self.error = error.localizedDescription
        }

        loading = false
    }

    private func now(for channelId: String) -> ProviderEpgProgrammeDTO? {
        let time = Int64(Date().timeIntervalSince1970)
        return guide[channelId]?.first {
            time >= $0.startEpochSeconds && time < $0.endEpochSeconds
        }
    }

    private func next(for channelId: String) -> ProviderEpgProgrammeDTO? {
        let threshold = now(for: channelId)?.endEpochSeconds
            ?? Int64(Date().timeIntervalSince1970)

        return guide[channelId]?.first { $0.startEpochSeconds >= threshold }
    }

    private func progress(_ item: ProviderEpgProgrammeDTO) -> Double {
        let duration = item.endEpochSeconds - item.startEpochSeconds
        guard duration > 0 else { return 0 }

        let elapsed = Int64(Date().timeIntervalSince1970) - item.startEpochSeconds
        return min(max(Double(elapsed) / Double(duration), 0), 1)
    }
}

private struct ChannelLogo: View {
    let channel: ProviderLiveChannelDTO

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.06))

            if let raw = channel.logoUrl, let url = URL(string: raw) {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFit()
                    } else {
                        Image(systemName: "tv").foregroundStyle(.red)
                    }
                }
                .padding(6)
            } else {
                Image(systemName: "tv").foregroundStyle(.red)
            }
        }
    }
}

private struct LivePlayer: View {
    let channel: ProviderLiveChannelDTO

    @Environment(\.dismiss) private var dismiss
    @State private var error: String?

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()

            ParentalProtectedPlayerView(
                title: channel.name,
                streamURL: channel.streamUrl,
                resumePositionSeconds: 0,
                playbackKind: "live",
                onError: { error = $0 }
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

            if let error {
                Text(error)
                    .padding(20)
                    .background(Color.black.opacity(0.9))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
        }
    }
}

private struct GuideList: View {
    let channels: [ProviderLiveChannelDTO]
    let guide: [String: [ProviderEpgProgrammeDTO]]
    let onTune: (ProviderLiveChannelDTO) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(channels) { channel in
                        let programmes = guide[channel.id] ?? []
                        let current = current(programmes)
                        let next = upcoming(programmes, after: current)

                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(channel.name).bold()
                                Spacer()
                                Button((Locale.current.language.languageCode?.identifier == "fr" ? "Regarder" : "Watch")) { onTune(channel) }
                                    .buttonStyle(.borderedProminent)
                                    .tint(.red)
                            }

                            Text(
                                current.map { "Maintenant · \($0.title)" }
                                    ?? (Locale.current.language.languageCode?.identifier == "fr" ? "Programme en cours indisponible" : "Current program unavailable")
                            )

                            if let next {
                                Text("À suivre · \(next.title)")
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(14)
                        .background(Color.white.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                }
                .padding(16)
            }
            .background(Color.black)
            .navigationTitle((Locale.current.language.languageCode?.identifier == "fr" ? "Guide TV" : "TV guide"))
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button((Locale.current.language.languageCode?.identifier == "fr" ? "Fermer" : "Close")) { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func current(_ programmes: [ProviderEpgProgrammeDTO]) -> ProviderEpgProgrammeDTO? {
        let time = Int64(Date().timeIntervalSince1970)
        return programmes.first {
            time >= $0.startEpochSeconds && time < $0.endEpochSeconds
        }
    }

    private func upcoming(
        _ programmes: [ProviderEpgProgrammeDTO],
        after current: ProviderEpgProgrammeDTO?
    ) -> ProviderEpgProgrammeDTO? {
        let threshold = current?.endEpochSeconds
            ?? Int64(Date().timeIntervalSince1970)

        return programmes.first { $0.startEpochSeconds >= threshold }
    }
}

#Preview { LiveTvView() }
