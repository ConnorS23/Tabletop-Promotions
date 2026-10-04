package com.tableads.app

import android.app.Application
import com.tableads.app.data.Api
import com.tableads.app.data.DeviceIdentity
import com.tableads.app.data.Repository

class TableAdsApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val identity = DeviceIdentity(this)
        repository = Repository(this, identity, Api(identity, BuildConfig.VERSION_NAME))
        repository.start()
    }
}
