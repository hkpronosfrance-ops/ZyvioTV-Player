import SwiftUI

struct LiveTvView: View {
    @State private var selectedCategory = "Toutes"
    @State private var selectedChannel = "Chaîne 1"

    private let categories = ["Toutes", "France", "Sports", "Information", "Divertissement"]
    private let channels = ["Chaîne 1", "Chaîne 2", "Chaîne 3", "Chaîne 4"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("TV en direct")
                    .font(.largeTitle.bold())

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(categories, id: \.self) { category in
                            Button(category) {
                                selectedCategory = category
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(selectedCategory == category ? .red : .gray.opacity(0.35))
                        }
                    }
                }

                ZStack {
                    RoundedRectangle(cornerRadius: 22)
                        .fill(
                            LinearGradient(
                                colors: [Color.white.opacity(0.10), Color.black],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                    VStack(spacing: 10) {
                        Image(systemName: "play.fill")
                            .foregroundStyle(.red)
                        Text(selectedChannel)
                            .font(.title2.bold())
                        Text("Lecteur vidéo disponible en Phase 7")
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(height: 260)

                Text("Maintenant")
                    .foregroundStyle(.red)
                    .font(.headline)
                Text("Programme en direct")
                    .font(.title3.bold())
                Text("À suivre : Programme suivant")
                    .foregroundStyle(.secondary)

                VStack(spacing: 10) {
                    ForEach(channels, id: \.self) { channel in
                        Button {
                            selectedChannel = channel
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "tv")
                                    .foregroundStyle(.red)
                                    .frame(width: 44, height: 44)
                                    .background(Color.white.opacity(0.06))
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                VStack(alignment: .leading) {
                                    Text(channel).bold()
                                    Text("Programme en direct")
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                            }
                            .padding(12)
                            .background(Color.white.opacity(selectedChannel == channel ? 0.10 : 0.05))
                            .clipShape(RoundedRectangle(cornerRadius: 16))
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

#Preview {
    LiveTvView()
}
