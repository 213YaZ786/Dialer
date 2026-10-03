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
        // The Contacts app's private names, asked when a number has none in Android's contacts.
        com.yaz.dialer.core.dial.PrivateNames.init(this)
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@DialerApplication)
            modules(appModule)
        }
    }
}
