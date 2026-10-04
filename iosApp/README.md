# iOS client

This folder is the native SwiftUI shell for the iPhone/iPad client.

The production Xcode project will:

- link the generated Kotlin Multiplatform `shared` framework
- use SwiftUI for the interface
- use AVPlayer for IPTV/VOD playback
- use Keychain for local session/token storage
- keep provider credentials out of logs
- share account, playlist, EPG, history and synchronization domain logic with Android through `shared`

No Apple signing material belongs in the repository.
