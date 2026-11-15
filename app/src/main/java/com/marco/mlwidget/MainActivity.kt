package com.marco.mlwidget

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.marco.mltheme.MLTheme

class MlWidgetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        Refresh.schedule(this)
    }
}

class MainActivity : ComponentActivity() {
    /** home · settings · log · edit:<id> · note:<id> */
    private var screen by mutableStateOf("home")
    private val stack = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            MLTheme {
                AppUpdateHost()
                Root()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // con l'app aperta si legge la posizione: i widget meteo la usano anche dopo, senza GPS in background
        Thread {
            if (WeatherRepo.mode(this) == "gps") WeatherRepo.updateGps(this)
            if (WeatherRepo.isOld(this)) WeatherRepo.fetch(this)
            WidgetUpdater.updateAll(this)
        }.start()
    }

    private fun handle(i: Intent?) {
        val open = i?.getStringExtra("open") ?: return
        i.removeExtra("open")
        when {
            open == "weather" -> go("settings")
            open.startsWith("note:") || open.startsWith("edit:") -> go("edit:" + open.substringAfter(':'))
        }
    }

    private fun go(s: String) {
        if (screen != s) stack += screen
        screen = s
    }

    private fun back() {
        screen = stack.removeLastOrNull() ?: "home"
    }

    @Composable
    private fun Root() {
        BackHandler(enabled = screen != "home") { back() }
        AnimatedContent(screen, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "screen") { s ->
            when {
                s == "settings" -> SettingsScreen(onBack = { back() }, onLog = { go("log") })
                s == "log" -> LogScreen(onBack = { back() })
                s.startsWith("edit:") -> {
                    val t = Templates.byId(s.substringAfter(':'))
                    if (t == null) HomeScreen(onOpen = { go("edit:${it.id}") }, onSettings = { go("settings") })
                    else EditorScreen(t, onBack = { back() })
                }
                else -> HomeScreen(onOpen = { go("edit:${it.id}") }, onSettings = { go("settings") })
            }
        }
    }
}
