package com.example.backend

import android.content.Intent
import android.os.IBinder
import androidx.annotation.Keep
import com.topjohnwu.superuser.ipc.RootService

/**
 * RootService hosted by libsu in an elevated root process.
 * Bound from application process via RootService.bind(intent, connection).
 */
@Keep
class RootInputService : RootService() {

    override fun onBind(intent: Intent): IBinder {
        return InputServiceImpl.getInstance()
    }
}
