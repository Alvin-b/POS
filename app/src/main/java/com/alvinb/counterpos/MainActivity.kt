package com.alvinb.counterpos

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.local.AppDatabase
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import com.alvinb.counterpos.ui.CounterPosApp
import com.alvinb.counterpos.ui.CounterPosTheme
import com.alvinb.counterpos.ui.CounterPosViewModel
import com.alvinb.counterpos.ui.CounterPosViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val database = AppDatabase.getInstance(applicationContext)
        val repository = CounterPosRepository(
            dao = database.counterPosDao(),
            assets = assets,
            remote = SupabaseRestClient(applicationContext)
        )

        setContent {
            CounterPosTheme {
                val darkTheme = isSystemInDarkTheme()
                SideEffect {
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                val appViewModel: CounterPosViewModel = viewModel(
                    factory = CounterPosViewModelFactory(repository)
                )
                CounterPosApp(viewModel = appViewModel)
            }
        }
    }
}
