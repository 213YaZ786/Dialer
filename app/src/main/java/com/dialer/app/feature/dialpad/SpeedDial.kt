package com.dialer.app.feature.dialpad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dialer.app.core.dial.People
import com.dialer.app.core.dial.Person
import com.dialer.app.feature.contacts.StarGold
import com.dialer.app.ui.component.ContactAvatar
import com.dialer.app.ui.component.SearchPill
import com.dialer.app.ui.component.ZoneAlertDialog
import com.dialer.app.ui.icon.DialerIcons

/**
 * Giving a key of the dialpad to someone: held from then on, it calls
 * them. Favourites first, then everyone, with a search.
 */
@Composable
fun SpeedDialDialog(digit: Int, people: List<Person>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(people, query) { People.search(people, query) }
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speed dial $digit") },
        text = {
            Column {
                Text(
                    "Choose who holding $digit calls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                SearchPill(query, { query = it }, hint = "Search", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { person ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPick(person.number) }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            ContactAvatar(person.name, person.photo, 40.dp)
                            Text(person.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            if (person.starred) Icon(DialerIcons.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
