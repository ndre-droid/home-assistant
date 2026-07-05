package com.nahuel.homeflow.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nahuel.homeflow.data.Store

/** Restarts the trigger service after a reboot if any state-trigger routine is enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Store.init(context.applicationContext)
            TriggerService.sync(context)
        }
    }
}
