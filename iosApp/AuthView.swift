import SwiftUI

struct AuthView: View {
    let onAuthenticated: () -> Void

    enum Mode {
        case signIn
        case signUp
        case reset
    }

    @State private var mode: Mode = .signIn
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var message: String?
    @State private var isLoading = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 16) {
                        Text("ZYVIOTV")
                            .font(.system(size: 34, weight: .black))
                            .foregroundStyle(.white)

                        Text("PLAYER")
                            .font(.headline)
                            .tracking(8)
                            .foregroundStyle(.red)

                        Text(title)
                            .font(.title2.bold())
                            .padding(.top, 20)

                        Text(subtitle)
                            .font(.subheadline)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(.secondary)

                        TextField("Adresse e-mail", text: $email)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.emailAddress)
                            .textFieldStyle(.roundedBorder)
                            .disabled(isLoading)

                        if mode != .reset {
                            SecureField("Mot de passe", text: $password)
                                .textFieldStyle(.roundedBorder)
                                .disabled(isLoading)
                        }

                        if mode == .signUp {
                            SecureField("Confirmer le mot de passe", text: $confirmPassword)
                                .textFieldStyle(.roundedBorder)
                                .disabled(isLoading)
                        }

                        if let message {
                            Text(message)
                                .font(.footnote)
                                .foregroundStyle(.red)
                        }

                        Button {
                            Task { await submit() }
                        } label: {
                            if isLoading {
                                ProgressView()
                            } else {
                                Text(primaryButtonTitle)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                        .frame(maxWidth: .infinity)
                        .disabled(isLoading)

                        if mode == .signIn {
                            Button("Mot de passe oublié ?") {
                                mode = .reset
                                message = nil
                            }
                            .disabled(isLoading)

                            Button("Créer un compte") {
                                mode = .signUp
                                message = nil
                            }
                            .disabled(isLoading)
                        } else {
                            Button("Retour à la connexion") {
                                mode = .signIn
                                message = nil
                            }
                            .disabled(isLoading)
                        }
                    }
                    .padding(24)
                    .frame(maxWidth: 480)
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    @MainActor
    private func submit() async {
        message = nil

        if email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            message = "Saisissez votre adresse e-mail."
            return
        }

        if mode != .reset && password.count < 8 {
            message = "Le mot de passe doit contenir au moins 8 caractères."
            return
        }

        if mode == .signUp && password != confirmPassword {
            message = "Les mots de passe ne correspondent pas."
            return
        }

        isLoading = true
        defer { isLoading = false }

        do {
            switch mode {
            case .signIn:
                try await SupabaseAuthService.shared.signIn(
                    email: email,
                    password: password
                )
                onAuthenticated()

            case .signUp:
                try await SupabaseAuthService.shared.signUp(
                    email: email,
                    password: password
                )
                message = "Compte créé. Vérifiez votre e-mail si une confirmation est demandée."
                mode = .signIn
                password = ""
                confirmPassword = ""

            case .reset:
                try await SupabaseAuthService.shared.requestPasswordReset(email: email)
                message = "E-mail envoyé. Consultez votre boîte de réception."
                mode = .signIn
            }
        } catch {
            message = error.localizedDescription
        }
    }

    private var title: String {
        switch mode {
        case .signIn: return "Connexion"
        case .signUp: return "Créer un compte"
        case .reset: return "Mot de passe oublié"
        }
    }

    private var subtitle: String {
        switch mode {
        case .signIn:
            return "Retrouvez vos playlists et votre progression sur tous vos appareils."
        case .signUp:
            return "Créez votre compte ZyvioTV Player pour synchroniser vos appareils."
        case .reset:
            return "Nous vous enverrons un lien pour réinitialiser votre mot de passe."
        }
    }

    private var primaryButtonTitle: String {
        switch mode {
        case .signIn: return "Se connecter"
        case .signUp: return "Créer mon compte"
        case .reset: return "Envoyer le lien"
        }
    }
}

#Preview {
    AuthView(onAuthenticated: {})
}
