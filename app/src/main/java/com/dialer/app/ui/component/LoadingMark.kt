package com.dialer.app.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's loading mark, the signature the shared code expects.
 *
 * The other apps draw it from their launcher icon. Dialer's icon is not
 * designed yet, so this is the plain Material indicator until the icon is
 * chosen, then it is drawn from it like the others.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: Float = 0f
) {
    if (running) {
        CircularProgressIndicator(modifier.size(size), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
    } else {
        CircularProgressIndicator(
            progress = { progress },
            modifier = modifier.size(size),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )
    }
}
