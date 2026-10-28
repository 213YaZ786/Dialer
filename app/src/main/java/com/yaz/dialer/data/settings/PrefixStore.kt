package com.yaz.dialer.data.settings

import android.content.Context
import com.yaz.dialer.core.common.writeTextAtomically
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What happens to a call from a blocked prefix. */
@Serializable
enum class PrefixAction { BLOCK, SILENCE }

/**
 * One prefix the user blocks. [prefix] is the number's start in international form ("+3328103"),
 * [label] what the user typed ("02 81 03"), shown in the lists and in the history.
 * A rule kept as a plain start of number, as SpamBlocker keeps its number rules as a pattern with a
 * blacklist flag (RegexRule in
 * https://github.com/aj3423/SpamBlocker/blob/main/app/src/main/java/spam/blocker/ui/setting/regex/RegexPresets.kt),
 * but without the pattern language.
 */
@Serializable
data class BlockedPrefix(
    val prefix: String,
    val label: String,
    val action: PrefixAction = PrefixAction.BLOCK
)

@Serializable
private data class PrefixFile(val rules: List<BlockedPrefix> = emptyList())

/**
 * The blocked prefixes, plain JSON written atomically in the app's files, as [SettingsStore] keeps
 * the settings (kotlinx.serialization,
 * https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md). Numbers stay on the
 * device and out of Android's blocked list.
 */
class PrefixStore(context: Context) {

    private val file = File(context.filesDir, "prefixes.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _rules = MutableStateFlow(load())
    val rules: StateFlow<List<BlockedPrefix>> = _rules.asStateFlow()

    val current: List<BlockedPrefix> get() = _rules.value

    private fun load(): List<BlockedPrefix> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<PrefixFile>(file.readText()).rules }.getOrDefault(emptyList())
    }

    fun update(transform: (List<BlockedPrefix>) -> List<BlockedPrefix>) {
        val updated = transform(_rules.value)
        _rules.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(PrefixFile(updated))) }
    }
}
