package com.dialer.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.dialer.app.ui.icon.DialerIcons

/** The three tabs of the main screen, in dock order. */
enum class TopDestination(val label: String, val icon: ImageVector) {
    FAVORITES("Favorites", DialerIcons.Star),
    RECENTS("Recents", DialerIcons.Recents),
    CONTACTS("Contacts", DialerIcons.Person)
}

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
}
