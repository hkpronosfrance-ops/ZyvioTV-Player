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
