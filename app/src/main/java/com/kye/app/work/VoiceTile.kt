package com.kye.app.work

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kye.app.MainActivity

/** Quick-settings tile ("Speak expense"): opens KYE straight to the mic. */
class VoiceTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        // Android 14+ only accepts a PendingIntent here; older versions take a plain Intent.
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(KyeWidget.open(this, MainActivity.ACTION_VOICE, 20))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_VOICE)
            )
        }
    }
}
