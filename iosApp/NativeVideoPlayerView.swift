import AVKit
import SwiftUI

struct NativeVideoPlayerView: View {
    let title: String
    let streamURL: URL
    let resumePositionSeconds: Double

    @State private var player: AVPlayer?

    var body: some View {
        VideoPlayer(player: player)
            .onAppear {
                let item = AVPlayerItem(url: streamURL)
                let newPlayer = AVPlayer(playerItem: item)

                if resumePositionSeconds > 0 {
                    let time = CMTime(seconds: resumePositionSeconds, preferredTimescale: 600)
                    newPlayer.seek(to: time)
                }

                player = newPlayer
                newPlayer.play()
            }
            .onDisappear {
                player?.pause()
                player = nil
            }
            .accessibilityLabel(title)
    }
}

#Preview {
    NativeVideoPlayerView(
        title: "ZYVIOTV Player",
        streamURL: URL(string: "https://example.com/stream.m3u8")!,
        resumePositionSeconds: 0
    )
}
