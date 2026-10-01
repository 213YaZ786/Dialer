package com.dialer.app.di

import com.dialer.app.core.call.CallStore
import com.dialer.app.core.dial.DialRequests
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.data.settings.SettingsStore
import com.dialer.app.feature.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Single composition root. Calls, recents and contacts join it as they land. */
val appModule = module {
    single { SettingsStore(androidContext()) }
    // On the main thread: Telecom calls back there, and a Call is only
    // touched from it.
    single { CallStore(androidContext(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
    single { PhoneBook(androidContext(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
    single { DialRequests() }
    viewModelOf(::SettingsViewModel)
}
