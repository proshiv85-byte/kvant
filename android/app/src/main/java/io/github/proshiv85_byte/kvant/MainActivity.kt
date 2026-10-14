// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.proshiv85_byte.kvant.ui.KvantApp

class MainActivity : ComponentActivity() {

    private val vm: KvantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent { KvantApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Open with Kvant" from a file manager: play that file (single track). */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW) vm.openFiles(listOf(uri), replace = true)
    }
}
