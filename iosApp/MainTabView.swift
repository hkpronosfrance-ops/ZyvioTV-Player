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
