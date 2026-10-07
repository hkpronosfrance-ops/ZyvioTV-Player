import AVFoundation
import AVKit
import SwiftUI

struct NativeVideoPlayerView: View {
    let title: String
    let streamURL: URL
    let resumePositionSeconds: Double

    var onPositionChanged: (Double, Double?) -> Void = { _, _ in }
    var onEnded: (Double, Double?) -> Void = { _, _ in }
    var onError: (String) -> Void = { _ in }

    @State private var player: AVPlayer?
    @State private var failedObserver: NSObjectProtocol?
    @State private var endObserver: NSObjectProtocol?
    @State private var timeObserver: Any?

    var body: some View {
        VideoPlayer(player: player)
            .onAppear {
                configureAudioSession()

                let item = AVPlayerItem(url: streamURL)
                item.preferredForwardBufferDuration = 6

                let newPlayer = AVPlayer(playerItem: item)
                newPlayer.automaticallyWaitsToMinimizeStalling = true

                if resumePositionSeconds > 0 {
                    let time = CMTime(seconds: resumePositionSeconds, preferredTimescale: 600)
                    newPlayer.seek(to: time)
                }

                failedObserver = NotificationCenter.default.addObserver(
                    forName: .AVPlayerItemFailedToPlayToEndTime,
                    object: item,
                    queue: .main
                ) { _ in
                    onError("Impossible de lire ce flux. Réessayez ou choisissez un autre contenu.")
                }

                endObserver = NotificationCenter.default.addObserver(
                    forName: .AVPlayerItemDidPlayToEndTime,
                    object: item,
                    queue: .main
                ) { _ in
                    let position = safeSeconds(newPlayer.currentTime())
                    let duration = safeDuration(newPlayer.currentItem?.duration)
                    onEnded(position, duration)
                }

                timeObserver = newPlayer.addPeriodicTimeObserver(
                    forInterval: CMTime(seconds: 15, preferredTimescale: 600),
                    queue: .main
                ) { time in
                    let seconds = time.seconds
                    if seconds.isFinite && seconds >= 0 {
                        onPositionChanged(
                            seconds,
                            safeDuration(newPlayer.currentItem?.duration)
                        )
                    }
                }

                player = newPlayer
                newPlayer.play()
            }
            .onDisappear {
                if let player {
                    reportPosition(player)
                    if let timeObserver {
                        player.removeTimeObserver(timeObserver)
                    }
                    player.pause()
                }

                if let failedObserver {
                    NotificationCenter.default.removeObserver(failedObserver)
                }
                if let endObserver {
                    NotificationCenter.default.removeObserver(endObserver)
                }

                timeObserver = nil
                failedObserver = nil
                endObserver = nil
                player = nil
            }
            .accessibilityLabel(title)
    }

    private func reportPosition(_ player: AVPlayer) {
        let seconds = player.currentTime().seconds
        if seconds.isFinite && seconds >= 0 {
            onPositionChanged(
                seconds,
                safeDuration(player.currentItem?.duration)
            )
        }
    }

    private func safeSeconds(_ time: CMTime) -> Double {
        let seconds = time.seconds
        return seconds.isFinite && seconds >= 0 ? seconds : 0
    }

    private func safeDuration(_ time: CMTime?) -> Double? {
        guard let time else { return nil }
        let seconds = time.seconds
        return seconds.isFinite && seconds > 0 ? seconds : nil
    }

    private func configureAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .moviePlayback)
            try session.setActive(true)
        } catch {
            // Playback can still proceed; never expose provider URLs or credentials in diagnostics.
        }
    }
}

#Preview {
    NativeVideoPlayerView(
        title: "ZYVIOTV Player",
        streamURL: URL(string: "https://example.com/stream.m3u8")!,
        resumePositionSeconds: 0
    )
}
