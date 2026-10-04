package fr.zyviotv.player.shared

object AppIdentity {
    const val name: String = "ZyvioTV Player"
    const val tagline: String = "Votre univers IPTV, partout avec vous."
}

enum class ClientPlatform {
    AndroidPhone,
    AndroidTablet,
    AndroidTv,
    IPhone,
    IPad,
    SamsungTizen,
    LgWebOs,
}
