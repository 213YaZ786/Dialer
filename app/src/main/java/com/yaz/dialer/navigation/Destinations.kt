package com.yaz.dialer.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.yaz.dialer.ui.icon.AppIcons

/** The three tabs of the main screen, in dock order. */
enum class TopDestination(val label: String, val icon: ImageVector) {
    FAVORITES("Favorites", AppIcons.Star),
    RECENTS("Recents", AppIcons.Recents),
    CONTACTS("Contacts", AppIcons.Person)
}

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val NUMBER = "number?n={n}"


    /** A number's page; empty for a hidden number. */
    fun number(number: String) = "number?n=" + android.net.Uri.encode(number)
}
