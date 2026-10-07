package com.crewboard

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import com.crewboard.service.ShiftService
import com.crewboard.ui.CrewBoardRoot
import com.crewboard.ui.CrewBoardTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** FragmentActivity (a ComponentActivity subclass) so Compose and a classic Fragment can coexist. */
class MainActivity : FragmentActivity() {
    private val deepLink = MutableStateFlow<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            deepLink.value = intent.taskId()   // not again after rotation
            // The toggle shows the saved shift state, but only the toggle used to start the
            // service that owns the socket. Restore it on launch so the two can't disagree.
            val s = CrewApp.instance.settings
            if (s.onShift && !s.token.isNullOrEmpty()) ShiftService.start(this)
        }
        setContent {
            CrewBoardTheme {
                AskNotificationPermission()
                CrewBoardRoot(deepLink = deepLink, onDeepLinkHandled = { deepLink.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {   // notification tapped while activity already open
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.taskId()
    }

    private fun Intent.taskId(): Int? = getIntExtra(EXTRA_TASK_ID, -1).takeIf { it >= 0 }

    companion object { const val EXTRA_TASK_ID = "task_id" }
}

@Composable
private fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}