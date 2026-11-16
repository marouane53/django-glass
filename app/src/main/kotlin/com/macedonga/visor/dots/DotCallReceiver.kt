package com.macedonga.visor.dots

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Private notification action; no exported remote start/end endpoint. */
@AndroidEntryPoint
class DotCallReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: DotCallController
    override fun onReceive(context: Context, intent: Intent) { controller.endCall() }
}
