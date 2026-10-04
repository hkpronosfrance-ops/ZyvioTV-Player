import SwiftUI

struct RootView: View {
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 12) {
                Text("ZYVIOTV")
                    .font(.system(size: 36, weight: .black))
                    .foregroundStyle(.white)

                Text("PLAYER")
                    .font(.headline)
                    .tracking(8)
                    .foregroundStyle(.red)

                Text("Votre univers IPTV, partout avec vous.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.top, 8)
            }
        }
        .preferredColorScheme(.dark)
    }
}

#Preview {
    RootView()
}
