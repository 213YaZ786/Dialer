package com.yaz.dialer.di

import com.yaz.dialer.core.call.CallStore
import com.yaz.dialer.core.dial.DialRequests
import com.yaz.dialer.core.network.CellWatch
import com.yaz.dialer.data.calllog.CallHistory
import com.yaz.dialer.data.contacts.PhoneBook
import com.yaz.dialer.data.settings.SettingsStore
import com.yaz.dialer.feature.settings.SettingsViewModel
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
    single { CallStore(androidContext(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), get()) }
    single { PhoneBook(androidContext(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
    single { CallHistory(androidContext(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
    single { CellWatch(androidContext(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
    single { DialRequests() }
    viewModelOf(::SettingsViewModel)
}
