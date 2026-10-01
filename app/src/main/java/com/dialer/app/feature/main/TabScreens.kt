package com.dialer.app.feature.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dialer.app.ui.component.EmptyZone
import com.dialer.app.ui.icon.DialerIcons

// The three tabs as they stand before calls, the call log and contacts are
// wired in. Each becomes its own feature package when its data lands.

@Composable
fun FavoritesScreen(onOpenSettings: () -> Unit) {
    TabFrame(title = "Favorites", onOpenSettings = onOpenSettings) {
        EmptyZone(
            title = "No favorites yet",
            message = "Contacts you mark with a star show here, ready to call in one tap.",
            icon = DialerIcons.Star,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun RecentsScreen(onOpenSettings: () -> Unit) {
    TabFrame(title = "Recents", onOpenSettings = onOpenSettings) {
        EmptyZone(
            title = "No calls yet",
            message = "Calls you make and receive show here, newest first.",
            icon = DialerIcons.Recents,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ContactsScreen(onOpenSettings: () -> Unit) {
    TabFrame(title = "Contacts", onOpenSettings = onOpenSettings) {
        EmptyZone(
            title = "No contacts yet",
            message = "The contacts saved on this phone show here.",
            icon = DialerIcons.Person,
            modifier = Modifier.fillMaxSize()
        )
    }
}
