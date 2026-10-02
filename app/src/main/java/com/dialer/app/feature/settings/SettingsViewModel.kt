package com.dialer.app.feature.settings

import androidx.lifecycle.ViewModel
import com.dialer.app.core.update.UpdateMode
import com.dialer.app.data.settings.AnnounceMode
import com.dialer.app.data.settings.Settings
import com.dialer.app.data.settings.SettingsStore
import com.dialer.app.data.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    val settings: StateFlow<Settings> = store.settings

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
    fun setFlipToSilence(on: Boolean) = store.update { it.copy(flipToSilence = on) }
    fun setAnnounce(mode: AnnounceMode) = store.update { it.copy(announce = mode) }
    fun setVibrateOnAnswer(on: Boolean) = store.update { it.copy(vibrateOnAnswer = on) }
    fun replayGuide() = store.update { it.copy(welcomeSeen = false, dialpadHintSeen = false) }
}
