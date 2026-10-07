import AVFoundation
import AVKit
import CryptoKit
import SwiftUI


struct ParentalProtectedPlayerView: View {
    let title: String
    let streamURL: URL
    let resumePositionSeconds: Double
    let playbackKind: String
    var onPositionChanged: (Double, Double?) -> Void = { _, _ in }
    var onEnded: (Double, Double?) -> Void = { _, _ in }
    var onError: (String) -> Void = { _ in }

    @State private var isChecking = true
    @State private var isBlocked = false
    @State private var blockTitle = "Temps d’écran atteint"
    @State private var pin = ""
    @State private var pinError: String?
    @State private var submitting = false
    @State private var localConsumedSeconds = 0
    @State private var heartbeatTask: Task<Void, Never>?

    private var profileId: String? {
        PlayerProfileSelectionStore.shared.activeProfileId
    }

    private var contentKey: String {
        playbackKind + ":" + sha256(streamURL.absoluteString)
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if isChecking {
                ProgressView("Vérification du contrôle parental…")
                    .tint(.red)
            } else if isBlocked {
                blockedView
            } else {
                NativeVideoPlayerView(
                    title: title,
                    streamURL: streamURL,
                    resumePositionSeconds: resumePositionSeconds,
                    onPositionChanged: onPositionChanged,
                    onEnded: { position, duration in
                        onEnded(position, duration)
                        stopHeartbeat(endException: true)
                    },
                    onError: onError
                )
            }
        }
        .task {
            await evaluateInitialState()
        }
        .onDisappear {
            stopHeartbeat(endException: false)
        }
    }

    private var blockedView: some View {
        VStack(spacing: 18) {
            Image(systemName: "lock.fill")
                .font(.system(size: 44))
                .foregroundStyle(.red)

            Text(blockTitle)
                .font(.title2.bold())

            Text("Saisissez le PIN parental pour autoriser ce contenu pendant 30 minutes.")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            SecureField("PIN parental", text: $pin)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .onChange(of: pin) { _, value in
                    pin = String(value.filter(\.isNumber).prefix(4))
                    pinError = nil
                }
                .padding(12)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .frame(maxWidth: 280)

            if let pinError {
                Text(pinError)
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            Button {
                Task { await grantException() }
            } label: {
                if submitting {
                    ProgressView()
                } else {
                    Text("Continuer 30 min")
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(.red)
            .disabled(submitting || pin.count != 4)
        }
        .padding(28)
        .frame(maxWidth: 520)
    }

    @MainActor
    private func evaluateInitialState() async {
        guard let profileId else {
            isChecking = false
            return
        }

        do {
            let state = try await SupabaseParentalService.shared.runtimeState(
                profileId: profileId,
                contentKey: contentKey
            )
            localConsumedSeconds = max(state.consumedSeconds, 0)

            if state.parentalEnabled && state.isChild {
                if state.blockedByTime {
                    blockTitle = "Temps d’écran atteint"
                    isBlocked = true
                } else if !isScheduleAllowed(state) {
                    blockTitle = "Pas maintenant"
                    isBlocked = true
                }
            }

            isChecking = false

            if !isBlocked {
                startHeartbeat()
            }
        } catch {
            isChecking = false
            // Existing server-side rules remain authoritative when reachable.
            // If unavailable, do not invent a permissive local override.
            isBlocked = false
            startHeartbeat()
        }
    }

    @MainActor
    private func grantException() async {
        guard let profileId else { return }
        submitting = true
        pinError = nil

        do {
            let result = try await SupabaseParentalService.shared.grantException(
                profileId: profileId,
                pin: pin,
                contentKey: contentKey
            )

            if result.success {
                pin = ""
                isBlocked = false
                startHeartbeat()
            } else {
                pinError = parentalReasonMessage(result.reason)
            }
        } catch {
            pinError = error.localizedDescription
        }

        submitting = false
    }

    @MainActor
    private func startHeartbeat() {
        heartbeatTask?.cancel()
        guard let profileId else { return }

        let deviceUid = AppleDeviceIdentityStore.shared.deviceUid
        heartbeatTask = Task {
            while !Task.isCancelled {
                do {
                    let heartbeat = try await SupabaseParentalService.shared.heartbeat(
                        profileId: profileId,
                        deviceUid: deviceUid,
                        playing: true,
                        contentKey: contentKey,
                        localConsumedSeconds: localConsumedSeconds
                    )
                    await MainActor.run {
                        localConsumedSeconds = max(
                            localConsumedSeconds,
                            heartbeat.consumedSeconds
                        )
                        if heartbeat.blockedByTime {
                            blockTitle = "Temps d’écran atteint"
                            isBlocked = true
                        }
                    }
                    if heartbeat.blockedByTime {
                        break
                    }
                } catch {
                    // Keep playback stable; the next heartbeat will reconcile usage.
                }

                try? await Task.sleep(for: .seconds(30))
            }

            if isBlocked {
                await stopServerHeartbeat(profileId: profileId, deviceUid: deviceUid)
            }
        }
    }

    private func stopHeartbeat(endException: Bool) {
        heartbeatTask?.cancel()
        heartbeatTask = nil

        guard let profileId else { return }
        let deviceUid = AppleDeviceIdentityStore.shared.deviceUid
        let contentKey = self.contentKey
        let consumed = localConsumedSeconds

        Task {
            _ = try? await SupabaseParentalService.shared.heartbeat(
                profileId: profileId,
                deviceUid: deviceUid,
                playing: false,
                contentKey: contentKey,
                localConsumedSeconds: consumed
            )
            if endException {
                await SupabaseParentalService.shared.endException(
                    profileId: profileId,
                    contentKey: contentKey
                )
            }
        }
    }

    private func stopServerHeartbeat(profileId: String, deviceUid: String) async {
        _ = try? await SupabaseParentalService.shared.heartbeat(
            profileId: profileId,
            deviceUid: deviceUid,
            playing: false,
            contentKey: contentKey,
            localConsumedSeconds: localConsumedSeconds
        )
    }

    private func isScheduleAllowed(_ state: ParentalRuntimeStateDTO) -> Bool {
        guard state.parentalEnabled, state.isChild, state.scheduleEnabled else {
            return true
        }
        guard !state.scheduleWindows.isEmpty else {
            return false
        }

        let now: Date
        if let server = state.serverNowEpochMs {
            now = Date(timeIntervalSince1970: Double(server) / 1_000)
        } else {
            now = Date()
        }

        var calendar = Calendar.current
        calendar.timeZone = .current
        let weekday = calendar.component(.weekday, from: now)
        let isoDay: Int = weekday == 1 ? 7 : weekday - 1
        let previousIsoDay = isoDay == 1 ? 7 : isoDay - 1
        let minuteOfDay =
            calendar.component(.hour, from: now) * 60 +
            calendar.component(.minute, from: now)

        for window in state.scheduleWindows {
            guard
                case let .array(dayValues)? = window["days"],
                case let .string(startValue)? = window["start"],
                case let .string(endValue)? = window["end"],
                let start = parseMinute(startValue),
                let end = parseMinute(endValue)
            else {
                continue
            }

            let days = Set(dayValues.compactMap {
                if case let .int(value) = $0 { return value }
                return nil
            })

            if start == end, days.contains(isoDay) {
                return true
            }
            if start < end,
               days.contains(isoDay),
               minuteOfDay >= start,
               minuteOfDay < end {
                return true
            }
            if start > end {
                if days.contains(isoDay), minuteOfDay >= start {
                    return true
                }
                if days.contains(previousIsoDay), minuteOfDay < end {
                    return true
                }
            }
        }

        return false
    }

    private func parseMinute(_ value: String) -> Int? {
        let parts = value.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0]),
              let minute = Int(parts[1]),
              (0...23).contains(hour),
              (0...59).contains(minute)
        else {
            return nil
        }
        return hour * 60 + minute
    }

    private func parentalReasonMessage(_ reason: String?) -> String {
        switch reason {
        case "pin_invalid":
            return "Code PIN incorrect."
        case "pin_not_configured":
            return "Aucun code PIN parental n’est configuré."
        case "blocked":
            return "Trop de tentatives. Réessayez dans quelques minutes."
        case "profile_not_found":
            return "Profil introuvable."
        default:
            return "Impossible d’autoriser cette lecture."
        }
    }

    private func sha256(_ value: String) -> String {
        let digest = SHA256.hash(data: Data(value.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}

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
