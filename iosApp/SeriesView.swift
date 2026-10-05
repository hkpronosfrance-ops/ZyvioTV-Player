import SwiftUI

struct SeriesView: View {
    @State private var selectedSeason = 1

    private let seasons = [1, 2]

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
                        Text("SÉRIES").font(.caption.bold()).foregroundStyle(.red)
                        Text("Série en vedette").font(.largeTitle.bold())
                        Text("2026 • ★ 8,4").foregroundStyle(.secondary)
                        Text("Retrouvez toutes les saisons et reprenez exactement là où vous vous êtes arrêté.")
                    }
                    .padding(24)
                }
                .frame(height: 280)
                .clipShape(RoundedRectangle(cornerRadius: 24))

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(seasons, id: \.self) { season in
                            Button("Saison \(season)") {
                                selectedSeason = season
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(selectedSeason == season ? .red : .gray.opacity(0.35))
                        }
                    }
                }

                VStack(spacing: 12) {
                    ForEach(1...4, id: \.self) { episode in
                        HStack(spacing: 14) {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.white.opacity(0.06))
                                .frame(width: 110, height: 68)
                                .overlay {
                                    Image(systemName: "play.fill").foregroundStyle(.red)
                                }

                            VStack(alignment: .leading, spacing: 8) {
                                Text("Épisode \(episode)")
                                    .font(.headline)
                                ProgressView(value: episode == 1 ? 1 : episode == 2 ? 0.42 : 0)
                                    .tint(.red)
                            }
                            Spacer()
                        }
                        .padding(12)
                        .background(Color.white.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                }
            }
            .padding(20)
        }
        .background(Color.black)
        .preferredColorScheme(.dark)
    }
}
