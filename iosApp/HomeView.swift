import SwiftUI

struct HomeView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                ZStack(alignment: .bottomLeading) {
                    LinearGradient(
                        colors: [Color.red.opacity(0.35), Color.black],
                        startPoint: .leading,
                        endPoint: .trailing
                    )

                    VStack(alignment: .leading, spacing: 10) {
                        Text("ZYVIOTV")
                            .font(.system(size: 28, weight: .black))
                        Text("PLAYER")
                            .font(.caption.bold())
                            .tracking(6)
                            .foregroundStyle(.red)
                        Text("Tout votre univers au même endroit.")
                            .font(.title2.bold())
                        Text("Retrouvez vos chaînes, films, séries et votre progression sur vos appareils.")
                            .foregroundStyle(.secondary)
                        Button("Regarder la TV") {}
                            .buttonStyle(.borderedProminent)
                            .tint(.red)
                    }
                    .padding(24)
                }
                .frame(height: 250)
                .clipShape(RoundedRectangle(cornerRadius: 24))

                HomeShelf(title: "Reprendre la lecture")
                HomeShelf(title: "TV en direct")
                HomeShelf(title: "Films")
                HomeShelf(title: "Séries")
            }
            .padding(20)
        }
        .background(Color.black)
        .preferredColorScheme(.dark)
    }
}

private struct HomeShelf: View {
    let title: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.title3.bold())

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(0..<4, id: \.self) { index in
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color.white.opacity(0.08))
                            .frame(width: 180, height: 105)
                            .overlay {
                                Image(systemName: index == 0 ? "play.fill" : "tv")
                                    .foregroundStyle(.red)
                            }
                    }
                }
            }
        }
    }
}

#Preview {
    HomeView()
}
