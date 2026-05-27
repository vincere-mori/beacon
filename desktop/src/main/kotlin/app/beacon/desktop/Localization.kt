package app.beacon.desktop

import kotlinx.serialization.Serializable

@Serializable
enum class AppLanguage { RU, EN }

object L {
    var lang = AppLanguage.RU

    fun t(ru: String, en: String): String {
        return if (lang == AppLanguage.RU) ru else en
    }
}
