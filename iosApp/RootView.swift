import SwiftUI

struct RootView: View {
    @State private var isAuthenticated = false
    @State private var isCheckingSession = true

    var body: some View {
        Group {
            if isCheckingSession {
                ZStack {
                    Color.black.ignoresSafeArea()
                    ProgressView()
                        .tint(.red)
                }
            } else if isAuthenticated {
                MainTabView {
                    isAuthenticated = false
                }
            } else {
                AuthView {
                    isAuthenticated = true
                }
            }
        }
        .task {
            isAuthenticated = await SupabaseAuthService.shared.hasStoredSession
            isCheckingSession = false
        }
    }
}

#Preview {
    RootView()
}
