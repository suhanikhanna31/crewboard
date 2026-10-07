package com.crewboard.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.crewboard.CrewApp
import com.crewboard.service.ShiftService

/** Deliberately a classic View-based Fragment, hosted inside Compose via AndroidView. */
class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        val s = CrewApp.instance.settings
        fun field(hintText: String, value: String, password: Boolean = false) = EditText(ctx).apply {
            hint = hintText
            setText(value)
            setSingleLine()
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val url = field("Server URL", s.baseUrl)
        val user = field("Username", s.username)
        val pass = field("Password", s.password, password = true)
        val save = Button(ctx).apply {
            text = "Save and reconnect"
            setOnClickListener {
                s.baseUrl = url.text.toString()
                s.username = user.text.toString()
                s.password = pass.text.toString()
                s.token = null
                if (s.onShift) ShiftService.restart(ctx)
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(TextView(ctx).apply { text = "Connection"; textSize = 24f })
            listOf(url, user, pass, save).forEach { addView(it, LinearLayout.LayoutParams(-1, -2)) }
        }
    }
}

@Composable
fun SettingsFragmentHost(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as FragmentActivity
    val containerId = remember { View.generateViewId() }
    AndroidView(
        modifier = modifier,
        factory = { FragmentContainerView(it).apply { id = containerId } },
        update = {
            val fm = activity.supportFragmentManager
            if (fm.findFragmentById(containerId) == null) {
                fm.beginTransaction().replace(containerId, SettingsFragment()).commitNowAllowingStateLoss()
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            val fm = activity.supportFragmentManager
            if (!fm.isStateSaved) fm.findFragmentById(containerId)?.let {
                fm.beginTransaction().remove(it).commitNowAllowingStateLoss()
            }
        }
    }
}
