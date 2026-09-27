package it.kituwa.porchlight

import android.app.Application
import it.kituwa.porchlight.work.RefreshWorker

class PorchlightApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedule(this)
    }
}
