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
                    MainTabView(
                        profile: activeProfile,
                        onSignedOut: {
                            isAuthenticated = false
                            self.activeProfile = nil
                        },
                        onSwitchProfile: {
                            self.activeProfile = nil
                        }
                    )
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
