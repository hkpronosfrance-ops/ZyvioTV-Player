import SwiftUI

struct AuthView: View {
    enum Mode {
        case signIn
        case signUp
        case reset
    }

    @State private var mode: Mode = .signIn
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""

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

                        if mode != .reset {
                            SecureField("Mot de passe", text: $password)
                                .textFieldStyle(.roundedBorder)
                        }

                        if mode == .signUp {
                            SecureField("Confirmer le mot de passe", text: $confirmPassword)
                                .textFieldStyle(.roundedBorder)
                        }

                        Button(primaryButtonTitle) {
                            // Backend adapter will be connected after the auth backend is configured.
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                        .frame(maxWidth: .infinity)

                        if mode == .signIn {
                            Button("Mot de passe oublié ?") {
                                mode = .reset
                            }

                            Button("Créer un compte") {
                                mode = .signUp
                            }
                        } else {
                            Button("Retour à la connexion") {
                                mode = .signIn
                            }
                        }
                    }
                    .padding(24)
                    .frame(maxWidth: 480)
                    .frame(maxWidth: .infinity)
                }
            }
            .preferredColorScheme(.dark)
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
    AuthView()
}
