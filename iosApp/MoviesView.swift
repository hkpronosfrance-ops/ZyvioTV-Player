import SwiftUI

struct MoviesView: View {
    private let titles = ["Film en vedette", "Nouveauté", "À découvrir", "Votre favori"]
    @State private var selectedTitle = "Film en vedette"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ZStack(alignment: .bottomLeading) {
                    LinearGradient(
                        colors: [Color.red.opacity(0.30), Color.black],
                        startPoint: .leading,
                        endPoint: .trailing
                    )

                    VStack(alignment: .leading, spacing: 10) {
                        Text("FILMS").font(.caption.bold()).foregroundStyle(.red)
                        Text(selectedTitle).font(.largeTitle.bold())
                        Text("2026 • ★ 8,2").foregroundStyle(.secondary)
                        Text("Retrouvez les films de votre catalogue et reprenez votre lecture.")
                        Button("Lire") {}
                            .buttonStyle(.borderedProminent)
                            .tint(.red)
                    }
                    .padding(24)
                }
                .frame(height: 300)
                .clipShape(RoundedRectangle(cornerRadius: 24))

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    ForEach(titles, id: \.self) { title in
                        Button {
                            selectedTitle = title
                        } label: {
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.white.opacity(selectedTitle == title ? 0.12 : 0.06))
                                .aspectRatio(2 / 3, contentMode: .fit)
                                .overlay {
                                    VStack {
                                        Image(systemName: "film").foregroundStyle(.red)
                                        Spacer()
                                        Text(title).foregroundStyle(.white).font(.headline).padding(10)
                                    }
                                }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(20)
        }
        .background(Color.black)
        .preferredColorScheme(.dark)
    }
}
