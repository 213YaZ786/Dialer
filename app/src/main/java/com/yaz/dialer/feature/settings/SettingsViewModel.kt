package com.yaz.dialer.feature.settings

import androidx.lifecycle.ViewModel
import com.yaz.dialer.core.call.PrefixRules
import com.yaz.dialer.core.update.UpdateMode
import com.yaz.dialer.data.settings.BlockedPrefix
import com.yaz.dialer.data.settings.PrefixStore
import com.yaz.dialer.data.settings.AnnounceMode
import com.yaz.dialer.data.settings.Settings
import com.yaz.dialer.data.settings.SettingsStore
import com.yaz.dialer.data.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val store: SettingsStore, private val prefixStore: PrefixStore) : ViewModel() {

    val settings: StateFlow<Settings> = store.settings
    val prefixes: StateFlow<List<BlockedPrefix>> = prefixStore.rules

    // Setters like the others (https://developer.android.com/topic/libraries/architecture/viewmodel).
    fun addPrefix(rule: BlockedPrefix) = prefixStore.update { PrefixRules.with(it, rule) }
    fun removePrefix(prefix: String) = prefixStore.update { PrefixRules.without(it, prefix) }

    fun setTheme(mode: ThemeMode) = store.update { it.copy(themeMode = mode) }
    fun setPureBlack(on: Boolean) = store.update { it.copy(pureBlack = on) }
    fun setGlass(on: Boolean) = store.update { it.copy(glass = on) }
    fun setTextScale(scale: Float) = store.update { it.copy(textScale = scale) }
    fun setUpdates(mode: UpdateMode) = store.update { it.copy(updates = mode) }
    fun setSilenceUnknown(on: Boolean) = store.update { it.copy(silenceUnknown = on) }
    fun setBlockSpoofed(on: Boolean) = store.update { it.copy(blockSpoofed = on) }
    fun setHideInRecents(on: Boolean) = store.update { it.copy(hideInRecents = on) }
    fun setBlockHidden(on: Boolean) = store.update { it.copy(blockHidden = on) }
    fun setBlockSalesCalls(on: Boolean) = store.update { it.copy(blockSalesCalls = on) }
    // A setter like the others (https://developer.android.com/topic/libraries/architecture/viewmodel).
    fun removeSalesAllowed(number: String) =
        store.update { it.copy(salesAllowed = com.yaz.dialer.core.call.ScreeningRules.withAllowed(it.salesAllowed, number, false)) }
    fun setFlipToSilence(on: Boolean) = store.update { it.copy(flipToSilence = on) }
    fun setAnnounce(mode: AnnounceMode) = store.update { it.copy(announce = mode) }
    fun setVibrateOnAnswer(on: Boolean) = store.update { it.copy(vibrateOnAnswer = on) }
    // Setters like the others (https://developer.android.com/topic/libraries/architecture/viewmodel).
    fun replayGuide() = store.update { it.copy(welcomeSeen = false, dialpadHintSeen = false) }
}
