package com.dialer.app.di

import com.dialer.app.data.settings.SettingsStore
import com.dialer.app.feature.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Single composition root. Calls, recents and contacts join it as they land. */
val appModule = module {
    single { SettingsStore(androidContext()) }
    viewModelOf(::SettingsViewModel)
}
