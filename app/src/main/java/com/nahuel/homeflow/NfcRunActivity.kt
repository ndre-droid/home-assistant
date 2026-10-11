package com.nahuel.homeflow

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Bundle
import android.widget.Toast
import com.nahuel.homeflow.data.Store
import com.nahuel.homeflow.engine.RoutineEngine
import com.nahuel.homeflow.engine.RoutineRunService

/**
 * Invisible target for NFC tags (homeflow://run/<id>): runs the routine and closes at once,
 * so tapping a tag doesn't bring up the app. Android only delivers tags to activities.
 */
class NfcRunActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED && data?.scheme == "homeflow" && data.host == "run") {
            val routine = data.lastPathSegment?.let { Store.routine(it) }
            if (routine == null) {
                Toast.makeText(this, "Automation nicht gefunden", Toast.LENGTH_SHORT).show()
            } else {
                // Same tag again while it runs stops it (toggle semantics of the engine).
                val msg = if (RoutineEngine.isRunning(routine.id)) "⏹ ${routine.name}" else "▶ ${routine.name}"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                RoutineRunService.start(this, routine.id)
            }
        }
        finish()
    }
}
