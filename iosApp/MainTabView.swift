import SwiftUI

struct MainTabView: View {
    let onSignedOut: () -> Void

    var body: some View {
        TabView {
            HomeView()
                .tabItem {
                    Label("Accueil", systemImage: "house.fill")
                }

            LiveTvView()
                .tabItem {
                    Label("TV", systemImage: "tv.fill")
                }

            MoviesView()
                .tabItem {
                    Label("Films", systemImage: "film.fill")
                }

            SeriesView()
                .tabItem {
                    Label("Séries", systemImage: "rectangle.stack.fill")
                }

            AccountView(onSignedOut: onSignedOut)
                .tabItem {
                    Label("Plus", systemImage: "ellipsis.circle.fill")
                }
        }
        .tint(.red)
        .preferredColorScheme(.dark)
    }
}

private struct AccountView: View {
    let onSignedOut: () -> Void
    @State private var isSigningOut = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()

                VStack(alignment: .leading, spacing: 20) {
                    Text("Compte")
                        .font(.largeTitle.bold())

                    Text("Votre compte ZyvioTV Player synchronise vos appareils, favoris et progressions.")
                        .foregroundStyle(.secondary)

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
    MainTabView(onSignedOut: {})
}
