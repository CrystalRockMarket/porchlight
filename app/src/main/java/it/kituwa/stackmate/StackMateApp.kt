package it.kituwa.stackmate

import android.app.Application
import it.kituwa.stackmate.work.RefreshWorker

class StackMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedule(this)
    }
}
