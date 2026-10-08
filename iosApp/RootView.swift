import SwiftUI

private struct ParentalRecoveryLink: Identifiable {
    let id = UUID()
    let url: URL
}

struct RootView: View {
    @State private var isAuthenticated = false
    @State private var isCheckingSession = true
    @State private var isLoadingProfile = false
    @State private var activeProfile: PlayerProfileDTO?
    @State private var profileError: String?
    @State private var parentalRecoveryLink: ParentalRecoveryLink?

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
        .onOpenURL { url in
            guard
                url.scheme?.lowercased() == "zyviotv",
                url.host?.lowercased() == "parental-pin-recovery"
            else { return }
            parentalRecoveryLink = ParentalRecoveryLink(url: url)
        }
        .sheet(item: $parentalRecoveryLink) { link in
            ParentalPinRecoveryView(
                url: link.url,
                onCompleted: {
                    parentalRecoveryLink = nil
                    isAuthenticated = true
                    Task { await loadActiveProfile(useStoredSelection: true) }
                }
            )
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




private struct ParentalPinRecoveryView: View {
    let url: URL
    let onCompleted: () -> Void

    @State private var linkReady = false
    @State private var loading = true
    @State private var busy = false
    @State private var newPin = ""
    @State private var confirmPin = ""
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                Text((Locale.current.language.languageCode?.identifier == "fr" ? "Réinitialiser le code PIN" : "Reset parental PIN"))
                    .font(.largeTitle.bold())

                Text((Locale.current.language.languageCode?.identifier == "fr" ? "Créez un nouveau code PIN parental à 4 chiffres." : "Create a new 4-digit parental PIN."))
                    .foregroundStyle(.secondary)

                if loading {
                    ProgressView()
                        .tint(.red)
                } else if let errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                } else if linkReady {
                    SecureField((Locale.current.language.languageCode?.identifier == "fr" ? "Nouveau PIN" : "New PIN"), text: $newPin)
                        .keyboardType(.numberPad)
                        .onChange(of: newPin) { _, value in
                            newPin = String(value.filter(\.isNumber).prefix(4))
                        }
                        .textFieldStyle(.roundedBorder)

                    SecureField((Locale.current.language.languageCode?.identifier == "fr" ? "Confirmer le PIN" : "Confirm PIN"), text: $confirmPin)
                        .keyboardType(.numberPad)
                        .onChange(of: confirmPin) { _, value in
                            confirmPin = String(value.filter(\.isNumber).prefix(4))
                        }
                        .textFieldStyle(.roundedBorder)

                    Button {
                        Task { await save() }
                    } label: {
                        if busy {
                            ProgressView()
                        } else {
                            Text((Locale.current.language.languageCode?.identifier == "fr" ? "Enregistrer le nouveau PIN" : "Save new PIN"))
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                    .disabled(
                        busy ||
                        newPin.count != 4 ||
                        confirmPin.count != 4 ||
                        newPin != confirmPin
                    )

                    if newPin.count == 4, confirmPin.count == 4, newPin != confirmPin {
                        Text((Locale.current.language.languageCode?.identifier == "fr" ? "Les deux codes PIN ne correspondent pas." : "The PINs do not match."))
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
            }
            .padding(24)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black)
            .preferredColorScheme(.dark)
            .task { await consumeLink() }
        }
    }

    @MainActor
    private func consumeLink() async {
        loading = true
        errorMessage = nil
        do {
            try await SupabaseAuthService.shared.consumeParentalRecoveryURL(url)
            linkReady = true
        } catch {
            linkReady = false
            errorMessage = error.localizedDescription
        }
        loading = false
    }

    @MainActor
    private func save() async {
        busy = true
        defer { busy = false }
        do {
            try await SupabaseParentalService.shared.resetPinAfterRecentAuth(newPin: newPin)
            onCompleted()
        } catch {
            errorMessage = error.localizedDescription
        }
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
                    ProgressView((Locale.current.language.languageCode?.identifier == "fr" ? "Vérification du service…" : "Checking service…"))
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
                            title: (Locale.current.language.languageCode?.identifier == "fr" ? "Maintenance programmée" : "Scheduled maintenance"),
                            message: message ?? (Locale.current.language.languageCode?.identifier == "fr" ? "Une maintenance est prévue prochainement. Vous pouvez continuer à utiliser ZYVIOTV." : "Maintenance is scheduled soon. You can continue using ZYVIOTV."),
                            blocking: false
                        )
                    }
                case .blockingMaintenance(let message):
                    systemStateView(
                        title: (Locale.current.language.languageCode?.identifier == "fr" ? "Maintenance en cours" : "Maintenance in progress"),
                        message: message ?? (Locale.current.language.languageCode?.identifier == "fr" ? "Le service est momentanément indisponible pendant la maintenance." : "The service is temporarily unavailable during maintenance."),
                        blocking: true
                    )
                case .accountSuspended(let message):
                    systemStateView(
                        title: (Locale.current.language.languageCode?.identifier == "fr" ? "Compte suspendu" : "Account suspended"),
                        message: message ?? (Locale.current.language.languageCode?.identifier == "fr" ? "L’accès au service est actuellement suspendu pour ce compte." : "Service access is currently suspended for this account."),
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
                    Button((Locale.current.language.languageCode?.identifier == "fr" ? "Continuer" : "Continue")) {
                        ignoredPlannedMaintenance = true
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                } else {
                    Button((Locale.current.language.languageCode?.identifier == "fr" ? "Réessayer" : "Try again")) {
                        Task { await reload() }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)

                    Button((Locale.current.language.languageCode?.identifier == "fr" ? "Se déconnecter" : "Sign out"), role: .destructive) {
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
                    ProgressView((Locale.current.language.languageCode?.identifier == "fr" ? "Vérification de vos playlists…" : "Checking your playlists…"))
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
                        Text((Locale.current.language.languageCode?.identifier == "fr" ? "Ajoutez votre première playlist" : "Add your first playlist"))
                            .font(.largeTitle.bold())
                        Text((Locale.current.language.languageCode?.identifier == "fr" ? "ZYVIOTV doit disposer d’une source active avant d’ouvrir l’Accueil." : "ZYVIOTV needs an active source before opening Home."))
                            .foregroundStyle(.secondary)
                    }

                    Picker("Type", selection: $type) {
                        Text("Xtream Codes").tag("xtream")
                        Text("M3U").tag("m3u")
                    }
                    .pickerStyle(.segmented)

                    TextField((Locale.current.language.languageCode?.identifier == "fr" ? "Nom de la playlist" : "Playlist name"), text: $name)
                        .textFieldStyle(.roundedBorder)

                    if type == "xtream" {
                        TextField((Locale.current.language.languageCode?.identifier == "fr" ? "Adresse du serveur" : "Server address"), text: $serverURL)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                            .textFieldStyle(.roundedBorder)
                        TextField((Locale.current.language.languageCode?.identifier == "fr" ? "Nom d’utilisateur" : "Username"), text: $username)
                            .textInputAutocapitalization(.never)
                            .textFieldStyle(.roundedBorder)
                        SecureField((Locale.current.language.languageCode?.identifier == "fr" ? "Mot de passe" : "Password"), text: $password)
                            .textFieldStyle(.roundedBorder)
                    } else {
                        TextField("URL M3U", text: $m3uURL)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                            .textFieldStyle(.roundedBorder)
                        TextField((Locale.current.language.languageCode?.identifier == "fr" ? "URL XMLTV (optionnelle)" : "XMLTV URL (optional)"), text: $xmlTvURL)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                            .textFieldStyle(.roundedBorder)
                    }

                    if let text = message ?? errorMessage {
                        Text(text)
                            .font(.footnote)
                            .foregroundStyle(((message ?? "").hasPrefix("Erreur") || (message ?? "").hasPrefix("Error:")) ? .red : .secondary)
                    }

                    Button {
                        Task { await save() }
                    } label: {
                        if busy {
                            ProgressView()
                        } else {
                            Text((Locale.current.language.languageCode?.identifier == "fr" ? "Tester et enregistrer" : "Test and save"))
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                    .disabled(busy || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                    Text((Locale.current.language.languageCode?.identifier == "fr" ? "Les identifiants sont testés puis enregistrés de façon sécurisée. Ils ne sont jamais affichés en clair." : "Credentials are tested and saved securely. They are never displayed in plain text."))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding(22)
                .frame(maxWidth: 680)
                .frame(maxWidth: .infinity)
            }
            .background(Color.black)
            .navigationTitle((Locale.current.language.languageCode?.identifier == "fr" ? "Configurer ZYVIOTV" : "Set up ZYVIOTV"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button((Locale.current.language.languageCode?.identifier == "fr" ? "Déconnexion" : "Sign out"), action: onSignedOut)
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

            message = (Locale.current.language.languageCode?.identifier == "fr" ? "Playlist enregistrée." : "Playlist saved.")
            onSaved()
        } catch {
            message = (Locale.current.language.languageCode?.identifier == "fr" ? "Erreur : " : "Error: ") + error.localizedDescription
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
                            Text((Locale.current.language.languageCode?.identifier == "fr" ? "Qui regarde ?" : "Who's watching?"))
                                .font(.largeTitle.bold())
                            Text((Locale.current.language.languageCode?.identifier == "fr" ? "Choisissez votre profil ZYVIOTV." : "Choose your ZYVIOTV profile."))
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
                                Button((Locale.current.language.languageCode?.identifier == "fr" ? "Réessayer" : "Try again")) {
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
                                                            Text((Locale.current.language.languageCode?.identifier == "fr" ? "ENFANT" : "KIDS"))
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
                                                Text((Locale.current.language.languageCode?.identifier == "fr" ? "Principal" : "Main"))
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
                    Button((Locale.current.language.languageCode?.identifier == "fr" ? "Déconnexion" : "Sign out"), action: onSignedOut)
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
                localError = (Locale.current.language.languageCode?.identifier == "fr" ? "Aucun profil disponible." : "No profiles available.")
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
