import SwiftUI

struct RootView: View {
    @State private var isAuthenticated = false
    @State private var isCheckingSession = true
    @State private var isLoadingProfile = false
    @State private var activeProfile: PlayerProfileDTO?
    @State private var profileError: String?

    var body: some View {
        Group {
            if isCheckingSession || isLoadingProfile {
                ZStack {
                    Color.black.ignoresSafeArea()
                    ProgressView()
                        .tint(.red)
                }
            } else if isAuthenticated {
                if let activeProfile {
                    AppleSystemGateContainer(
                        onSignedOut: {
                            isAuthenticated = false
                            self.activeProfile = nil
                        }
                    ) {
                        PlaylistBootstrapView(
                            profile: activeProfile,
                            onSignedOut: {
                                isAuthenticated = false
                                self.activeProfile = nil
                            },
                            onSwitchProfile: {
                                self.activeProfile = nil
                            }
                        )
                    }
                    .id(activeProfile.id)
                } else {
                    ProfilePickerView(
                        errorMessage: profileError,
                        onSelected: { profile in
                            PlayerProfileSelectionStore.shared.select(profileId: profile.id)
                            activeProfile = profile
                            profileError = nil
                        },
                        onRetry: {
                            Task { await loadActiveProfile(useStoredSelection: false) }
                        },
                        onSignedOut: {
                            Task {
                                await SupabaseAuthService.shared.signOut()
                                isAuthenticated = false
                                activeProfile = nil
                            }
                        }
                    )
                }
            } else {
                AuthView {
                    isAuthenticated = true
                    Task { await loadActiveProfile(useStoredSelection: true) }
                }
            }
        }
        .task {
            isAuthenticated = await SupabaseAuthService.shared.restoreSession()
            isCheckingSession = false

            if isAuthenticated {
                await loadActiveProfile(useStoredSelection: true)
            }
        }
    }

    @MainActor
    private func loadActiveProfile(useStoredSelection: Bool) async {
        isLoadingProfile = true
        profileError = nil

        do {
            _ = try await SupabaseProfileService.shared.ensurePrimaryProfile()
            let profiles = try await SupabaseProfileService.shared.listProfiles()

            guard !profiles.isEmpty else {
                throw SupabaseProfileService.ProfileError.noProfile
            }

            if useStoredSelection,
               let storedId = PlayerProfileSelectionStore.shared.activeProfileId,
               let stored = profiles.first(where: { $0.id == storedId }) {
                activeProfile = stored
            } else if profiles.count == 1, let only = profiles.first {
                PlayerProfileSelectionStore.shared.select(profileId: only.id)
                activeProfile = only
            } else {
                activeProfile = nil
            }
        } catch {
            activeProfile = nil
            profileError = error.localizedDescription
        }

        isLoadingProfile = false
    }
}



private struct AppleSystemGateContainer<Content: View>: View {
    let onSignedOut: () -> Void
    private let content: () -> Content

    init(
        onSignedOut: @escaping () -> Void,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.onSignedOut = onSignedOut
        self.content = content
    }

    @State private var loading = true
    @State private var state: AppleSystemGateState = .normal
    @State private var ignoredPlannedMaintenance = false

    var body: some View {
        Group {
            if loading {
                ZStack {
                    Color.black.ignoresSafeArea()
                    ProgressView("Vérification du service…")
                        .tint(.red)
                }
            } else {
                switch state {
                case .normal:
                    content()
                case .plannedMaintenance(let message):
                    if ignoredPlannedMaintenance {
                        content()
                    } else {
                        systemStateView(
                            title: "Maintenance programmée",
                            message: message ?? "Une maintenance est prévue prochainement. Vous pouvez continuer à utiliser ZYVIOTV.",
                            blocking: false
                        )
                    }
                case .blockingMaintenance(let message):
                    systemStateView(
                        title: "Maintenance en cours",
                        message: message ?? "Le service est momentanément indisponible pendant la maintenance.",
                        blocking: true
                    )
                case .accountSuspended(let message):
                    systemStateView(
                        title: "Compte suspendu",
                        message: message ?? "L’accès au service est actuellement suspendu pour ce compte.",
                        blocking: true
                    )
                }
            }
        }
        .task { await reload() }
    }

    @ViewBuilder
    private func systemStateView(
        title: String,
        message: String,
        blocking: Bool
    ) -> some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 18) {
                Image(systemName: blocking ? "exclamationmark.octagon.fill" : "wrench.and.screwdriver.fill")
                    .font(.system(size: 46))
                    .foregroundStyle(.red)

                Text(title)
                    .font(.largeTitle.bold())

                Text(message)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                if !blocking {
                    Button("Continuer") {
                        ignoredPlannedMaintenance = true
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                } else {
                    Button("Réessayer") {
                        Task { await reload() }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)

                    Button("Se déconnecter", role: .destructive) {
                        Task {
                            await SupabaseAuthService.shared.signOut()
                            onSignedOut()
                        }
                    }
                    .buttonStyle(.bordered)
                }
            }
            .padding(28)
            .frame(maxWidth: 560)
        }
    }

    @MainActor
    private func reload() async {
        loading = true
        async let stateTask = SupabaseSystemStateService.shared.loadState()
        async let registrationTask: Void = registerDeviceSafely()
        state = await stateTask
        _ = await registrationTask
        loading = false
    }

    private func registerDeviceSafely() async {
        try? await SupabaseDeviceService.shared.registerCurrentDevice()
    }
}

private struct PlaylistBootstrapView: View {
    let profile: PlayerProfileDTO
    let onSignedOut: () -> Void
    let onSwitchProfile: () -> Void

    @State private var loading = true
    @State private var ready = false
    @State private var errorMessage: String?

    var body: some View {
        Group {
            if loading {
                ZStack {
                    Color.black.ignoresSafeArea()
                    ProgressView("Vérification de vos playlists…")
                        .tint(.red)
                }
            } else if ready {
                MainTabView(
                    profile: profile,
                    onSignedOut: onSignedOut,
                    onSwitchProfile: onSwitchProfile
                )
            } else {
                PlaylistOnboardingView(
                    errorMessage: errorMessage,
                    onSaved: {
                        Task { await refresh() }
                    },
                    onSignedOut: onSignedOut
                )
            }
        }
        .task { await refresh() }
    }

    @MainActor
    private func refresh() async {
        loading = true
        errorMessage = nil

        do {
            let playlists = try await SupabasePlaylistService.shared.listPlaylists()
            ready = playlists.contains {
                $0.isEnabled && $0.secretStatus == "configured"
            }
        } catch {
            ready = false
            errorMessage = error.localizedDescription
        }

        loading = false
    }
}

struct PlaylistOnboardingView: View {
    let errorMessage: String?
    let onSaved: () -> Void
    let onSignedOut: () -> Void

    @State private var type = "xtream"
    @State private var name = ""
    @State private var serverURL = ""
    @State private var username = ""
    @State private var password = ""
    @State private var m3uURL = ""
    @State private var xmlTvURL = ""
    @State private var busy = false
    @State private var message: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Ajoutez votre première playlist")
                            .font(.largeTitle.bold())
                        Text("ZYVIOTV doit disposer d’une source active avant d’ouvrir l’Accueil.")
                            .foregroundStyle(.secondary)
                    }

                    Picker("Type", selection: $type) {
                        Text("Xtream Codes").tag("xtream")
                        Text("M3U").tag("m3u")
                    }
                    .pickerStyle(.segmented)

                    TextField("Nom de la playlist", text: $name)
                        .textFieldStyle(.roundedBorder)

                    if type == "xtream" {
                        TextField("Adresse du serveur", text: $serverURL)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                            .textFieldStyle(.roundedBorder)
                        TextField("Nom d’utilisateur", text: $username)
                            .textInputAutocapitalization(.never)
                            .textFieldStyle(.roundedBorder)
                        SecureField("Mot de passe", text: $password)
                            .textFieldStyle(.roundedBorder)
                    } else {
                        TextField("URL M3U", text: $m3uURL)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                            .textFieldStyle(.roundedBorder)
                        TextField("URL XMLTV (optionnelle)", text: $xmlTvURL)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                            .textFieldStyle(.roundedBorder)
                    }

                    if let text = message ?? errorMessage {
                        Text(text)
                            .font(.footnote)
                            .foregroundStyle((message ?? "").hasPrefix("Erreur") ? .red : .secondary)
                    }

                    Button {
                        Task { await save() }
                    } label: {
                        if busy {
                            ProgressView()
                        } else {
                            Text("Tester et enregistrer")
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                    .disabled(busy || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                    Text("Les identifiants sont testés puis enregistrés de façon sécurisée. Ils ne sont jamais affichés en clair.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding(22)
                .frame(maxWidth: 680)
                .frame(maxWidth: .infinity)
            }
            .background(Color.black)
            .navigationTitle("Configurer ZYVIOTV")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Déconnexion", action: onSignedOut)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    @MainActor
    private func save() async {
        busy = true
        message = nil
        defer { busy = false }

        do {
            let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
            let playlistId: String
            let secret: ApplePlaylistSecret

            if type == "xtream" {
                let server = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
                let user = username.trimmingCharacters(in: .whitespacesAndNewlines)
                try await SupabasePlaylistService.shared.testXtream(
                    serverURL: server,
                    username: user,
                    password: password
                )
                playlistId = try await SupabasePlaylistService.shared.createPlaylist(
                    name: cleanName,
                    providerType: "xtream",
                    serverHost: safeOrigin(server),
                    playlistUrlHint: nil
                )
                secret = .xtream(serverURL: server, username: user, password: password)
            } else {
                let url = m3uURL.trimmingCharacters(in: .whitespacesAndNewlines)
                try await SupabasePlaylistService.shared.testM3u(urlString: url)

                let cleanXml = xmlTvURL
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                let xml = cleanXml.isEmpty ? nil : cleanXml
                if let xml,
                   let parsed = URL(string: xml),
                   !["http", "https"].contains(parsed.scheme?.lowercased() ?? "") {
                    throw SupabasePlaylistService.PlaylistError.invalidURL
                }

                playlistId = try await SupabasePlaylistService.shared.createPlaylist(
                    name: cleanName,
                    providerType: "m3u",
                    serverHost: nil,
                    playlistUrlHint: URL(string: url)?.host
                )
                secret = .m3u(url: url, xmlTvURL: xml)
            }

            do {
                try await SupabasePlaylistService.shared.setSecret(
                    playlistId: playlistId,
                    secret: secret
                )
            } catch {
                try? await SupabasePlaylistService.shared.delete(id: playlistId)
                throw error
            }

            message = "Playlist enregistrée."
            onSaved()
        } catch {
            message = "Erreur : \(error.localizedDescription)"
        }
    }

    private func safeOrigin(_ value: String) -> String? {
        guard let url = URL(string: value),
              let scheme = url.scheme,
              let host = url.host
        else { return nil }

        if let port = url.port {
            return "\(scheme)://\(host):\(port)"
        }
        return "\(scheme)://\(host)"
    }
}

private struct ProfilePickerView: View {
    let errorMessage: String?
    let onSelected: (PlayerProfileDTO) -> Void
    let onRetry: () -> Void
    let onSignedOut: () -> Void

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var profiles: [PlayerProfileDTO] = []
    @State private var isLoading = true
    @State private var localError: String?

    private var columns: [GridItem] {
        let count = horizontalSizeClass == .regular ? 4 : 2
        return Array(repeating: GridItem(.flexible(), spacing: 18), count: count)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 28) {
                        VStack(spacing: 8) {
                            Text("Qui regarde ?")
                                .font(.largeTitle.bold())
                            Text("Choisissez votre profil ZYVIOTV.")
                                .foregroundStyle(.secondary)
                        }

                        if isLoading {
                            ProgressView("Chargement des profils…")
                                .tint(.red)
                                .padding(.top, 40)
                        } else if let message = localError ?? errorMessage {
                            VStack(spacing: 14) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .font(.title)
                                    .foregroundStyle(.red)
                                Text(message)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.center)
                                Button("Réessayer") {
                                    Task { await reload() }
                                    onRetry()
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(.red)
                            }
                            .padding(24)
                        } else {
                            LazyVGrid(columns: columns, spacing: 22) {
                                ForEach(profiles) { profile in
                                    Button {
                                        onSelected(profile)
                                    } label: {
                                        VStack(spacing: 12) {
                                            ZStack {
                                                RoundedRectangle(cornerRadius: 24)
                                                    .fill(Color.white.opacity(0.08))

                                                Image(systemName: profile.isChild ? "figure.and.child.holdinghands" : "person.crop.circle.fill")
                                                    .font(.system(size: horizontalSizeClass == .regular ? 56 : 44))
                                                    .foregroundStyle(.white)

                                                if profile.isChild {
                                                    VStack {
                                                        HStack {
                                                            Spacer()
                                                            Text("ENFANT")
                                                                .font(.caption2.bold())
                                                                .padding(.horizontal, 8)
                                                                .padding(.vertical, 5)
                                                                .background(Color.red)
                                                                .clipShape(Capsule())
                                                        }
                                                        Spacer()
                                                    }
                                                    .padding(10)
                                                }
                                            }
                                            .aspectRatio(1, contentMode: .fit)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 24)
                                                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
                                            )

                                            Text(profile.name)
                                                .font(.headline)
                                                .foregroundStyle(.white)
                                                .lineLimit(1)

                                            if profile.isPrimary {
                                                Text("Principal")
                                                    .font(.caption)
                                                    .foregroundStyle(.secondary)
                                            }
                                        }
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel(profile.name)
                                }
                            }
                            .frame(maxWidth: horizontalSizeClass == .regular ? 760 : .infinity)
                        }
                    }
                    .padding(horizontalSizeClass == .regular ? 40 : 22)
                    .padding(.vertical, 32)
                    .frame(maxWidth: .infinity)
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Déconnexion", action: onSignedOut)
                }
            }
        }
        .preferredColorScheme(.dark)
        .task {
            await reload()
        }
    }

    @MainActor
    private func reload() async {
        isLoading = true
        localError = nil

        do {
            _ = try await SupabaseProfileService.shared.ensurePrimaryProfile()
            profiles = try await SupabaseProfileService.shared.listProfiles()
            if profiles.isEmpty {
                localError = "Aucun profil disponible."
            }
        } catch {
            localError = error.localizedDescription
        }

        isLoading = false
    }
}

#Preview {
    RootView()
}
