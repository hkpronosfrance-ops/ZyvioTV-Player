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
                ZyvioDesign.Palette.base.ignoresSafeArea()

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

                        TextField((Locale.current.language.languageCode?.identifier == "fr" ? "Adresse e-mail" : "Email address"), text: $email)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.emailAddress)
                            .textFieldStyle(.roundedBorder)
                            .disabled(isLoading)

                        if mode != .reset {
                            SecureField((Locale.current.language.languageCode?.identifier == "fr" ? "Mot de passe" : "Password"), text: $password)
                                .textFieldStyle(.roundedBorder)
                                .disabled(isLoading)
                        }

                        if mode == .signUp {
                            SecureField((Locale.current.language.languageCode?.identifier == "fr" ? "Confirmer le mot de passe" : "Confirm password"), text: $confirmPassword)
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
                            Button((Locale.current.language.languageCode?.identifier == "fr" ? "Mot de passe oublié ?" : "Forgot password?")) {
                                mode = .reset
                                message = nil
                            }
                            .disabled(isLoading)

                            Button((Locale.current.language.languageCode?.identifier == "fr" ? "Créer un compte" : "Create an account")) {
                                mode = .signUp
                                message = nil
                            }
                            .disabled(isLoading)
                        } else {
                            Button((Locale.current.language.languageCode?.identifier == "fr" ? "Retour à la connexion" : "Back to sign in")) {
                                mode = .signIn
                                message = nil
                            }
                            .disabled(isLoading)
                        }
                    }
                    .padding(ZyvioDesign.Space.s6)
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
            message = (Locale.current.language.languageCode?.identifier == "fr" ? "Saisissez votre adresse e-mail." : "Enter your email address.")
            return
        }

        if mode != .reset && password.count < 8 {
            message = (Locale.current.language.languageCode?.identifier == "fr" ? "Le mot de passe doit contenir au moins 8 caractères." : "Password must contain at least 8 characters.")
            return
        }

        if mode == .signUp && password != confirmPassword {
            message = (Locale.current.language.languageCode?.identifier == "fr" ? "Les mots de passe ne correspondent pas." : "Passwords do not match.")
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
                message = (Locale.current.language.languageCode?.identifier == "fr" ? "Compte créé. Vérifiez votre e-mail si une confirmation est demandée." : "Account created. Check your email if confirmation is required.")
                mode = .signIn
                password = ""
                confirmPassword = ""

            case .reset:
                try await SupabaseAuthService.shared.requestPasswordReset(email: email)
                message = (Locale.current.language.languageCode?.identifier == "fr" ? "E-mail envoyé. Consultez votre boîte de réception." : "Email sent. Check your inbox.")
                mode = .signIn
            }
        } catch {
            message = error.localizedDescription
        }
    }

    private var title: String {
        switch mode {
        case .signIn: return (Locale.current.language.languageCode?.identifier == "fr" ? "Connexion" : "Sign in")
        case .signUp: return (Locale.current.language.languageCode?.identifier == "fr" ? "Créer un compte" : "Create an account")
        case .reset: return (Locale.current.language.languageCode?.identifier == "fr" ? "Mot de passe oublié" : "Forgot password")
        }
    }

    private var subtitle: String {
        switch mode {
        case .signIn:
            return (Locale.current.language.languageCode?.identifier == "fr" ? "Retrouvez vos playlists et votre progression sur tous vos appareils." : "Access your playlists and watch progress on all your devices.")
        case .signUp:
            return (Locale.current.language.languageCode?.identifier == "fr" ? "Créez votre compte ZyvioTV Player pour synchroniser vos appareils." : "Create your ZyvioTV Player account to sync your devices.")
        case .reset:
            return (Locale.current.language.languageCode?.identifier == "fr" ? "Nous vous enverrons un lien pour réinitialiser votre mot de passe." : "We will send you a password reset link.")
        }
    }

    private var primaryButtonTitle: String {
        switch mode {
        case .signIn: return (Locale.current.language.languageCode?.identifier == "fr" ? "Se connecter" : "Sign in")
        case .signUp: return (Locale.current.language.languageCode?.identifier == "fr" ? "Créer mon compte" : "Create my account")
        case .reset: return (Locale.current.language.languageCode?.identifier == "fr" ? "Envoyer le lien" : "Send link")
        }
    }
}

#Preview {
    AuthView(onAuthenticated: {})
}
