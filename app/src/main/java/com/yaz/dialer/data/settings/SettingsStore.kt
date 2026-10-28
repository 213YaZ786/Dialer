package com.yaz.dialer.data.settings

import android.content.Context
import com.yaz.dialer.core.call.ScreeningPrefs
import com.yaz.dialer.core.common.writeTextAtomically
import com.yaz.dialer.core.update.UpdateMode
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** When the caller's name is read out while the phone rings. */
@Serializable
enum class AnnounceMode { OFF, HEADPHONES, ALWAYS }

/**
 * What a swipe on a call of Recents does, chosen for each side in Settings. Ported from SMS's
 * SwipeAction enum: https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/core/sms/Lists.kt
 * (same label and name pairs), with Samsung One UI Phone's choices.
 */
@Serializable
enum class SwipeChoice(val label: String) {
    CALL("Call"), MESSAGE("Message"), DELETE("Delete"), BLOCK("Block"), NONE("Nothing")
}

@Serializable
data class Settings(
    /** To call back cards already shown once (CallBacks.key), so each shows once. */
    val callBackSeen: Set<String> = emptySet(),
    /**
     * What happens when a newer version is out, checked once when the app
     * opens. Installing by default: the first launch page says so, and that
     * this one request is the app's only use of the internet.
     */
    val updates: UpdateMode = UpdateMode.INSTALL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** True black instead of dark grey in dark mode. */
    val pureBlack: Boolean = false,
    /** Zones and floating controls in liquid glass, over a soft light in the wallpaper's colours. */
    val glass: Boolean = true,
    /** Multiplier on every text style, one of the steps in ui.theme.TEXT_SCALES. */
    val textScale: Float = 1f,
    /** Index of the tab shown last, so the app reopens where it was left. */
    val lastTab: Int = 0,
    /** The first launch page was closed. */
    val welcomeSeen: Boolean = false,
    /** Numbers not in the contacts ring without a sound; the call still shows and can be answered. */
    val silenceUnknown: Boolean = false,
    /** Calls whose number the network found faked are turned away; they still show in Recents. */
    val blockSpoofed: Boolean = true,
    /** The app's picture in the recent apps screen stays blank: no calls nor contacts to be seen there. */
    val hideInRecents: Boolean = true,
    /** Calls with a hidden number are turned away; they still show in Recents. */
    val blockHidden: Boolean = false,
    /** Turning the phone face down stops the ringing. */
    val flipToSilence: Boolean = true,
    /** The caller's name read out while the phone rings. */
    val announce: AnnounceMode = AnnounceMode.OFF,
    /** One firm buzz when the person called picks up. */
    val vibrateOnAnswer: Boolean = true,
    /**
     * Where the user left the dialpad button, as fractions of the room it
     * moves in (0 to 1 across, 0 to 1 down); below 0, its usual place.
     */
    val dialpadX: Float = -1f,
    val dialpadY: Float = -1f,
    /** The little show of the dialpad button moving was seen. */
    val dialpadHintSeen: Boolean = false,
    /**
     * Calls from the ranges kept for sales calls are turned away, on unless the user turned it off
     * (Google's Phone filters spam calls by default too).
     */
    val blockSalesCalls: Boolean = true,
    /** Numbers the user said are not sales calls: they ring, whatever the range. Never in Android's blocked list. */
    val salesAllowed: Set<String> = emptySet(),
    /** Held keys 2 to 9 of the dialpad call these numbers. */
    val speedDial: Map<Int, String> = emptyMap(),
    /** A swipe to the right on a call of Recents. */
    val swipeRight: SwipeChoice = SwipeChoice.CALL,
    /** A swipe to the left on a call of Recents. */
    val swipeLeft: SwipeChoice = SwipeChoice.DELETE
)

/**
 * Small preference file, plain JSON written atomically, like the other apps.
 * Nothing here is a secret, and none of it leaves the device.
 */
class SettingsStore(private val context: Context) {

    private val file = File(context.filesDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    // The screening choices are copied where a call before the first unlock can read them.
    init {
        ScreeningPrefs.keep(context, ScreeningPrefs.of(_settings.value))
    }

    val current: Settings get() = _settings.value

    private fun load(): Settings {
        if (!file.exists()) return Settings()
        return runCatching { json.decodeFromString<Settings>(file.readText()) }
            .getOrDefault(Settings())
    }

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
        ScreeningPrefs.keep(context, ScreeningPrefs.of(updated))
    }
}
