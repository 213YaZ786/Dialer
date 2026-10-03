package com.yaz.dialer

import android.app.Application
import com.yaz.dialer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class DialerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // First start of the new Dialer: the old one's files come over before anything reads them.
        val tookOver = com.yaz.dialer.core.handover.Handover.takeOver(this)
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@DialerApplication)
            modules(appModule)
        }
        // The guide again, for what Android asks the new app itself (phone app, notifications).
        if (tookOver) org.koin.java.KoinJavaComponent.get<com.yaz.dialer.data.settings.SettingsStore>(com.yaz.dialer.data.settings.SettingsStore::class.java)
            .update { it.copy(welcomeSeen = false) }
    }
}
