package com.alvinb.counterpos

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val database = AppDatabase.getInstance(applicationContext)
        val supabase = SupabaseRestClient(
            context = applicationContext,
            projectUrl = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        )
        val repository = CounterPosRepository(
            dao = database.counterPosDao(),
            assets = assets,
            supabase = supabase,
            contentResolver = contentResolver
        )

        setContent {
            CounterPosTheme {
                val darkTheme = isSystemInDarkTheme()
                SideEffect {
                    window.statusBarColor = (if (darkTheme) Color(0xFF0B0F17) else Color(0xFFF8FAFC)).toArgb()
                    window.navigationBarColor = (if (darkTheme) Color(0xFF131B26) else Color.White).toArgb()
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
