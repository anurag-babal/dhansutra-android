package com.example.dhansutra.data.repository

import android.content.Context
import com.example.dhansutra.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPrefsSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _rolloverEnabled = MutableStateFlow(prefs.getBoolean(KEY_ROLLOVER, true))
    override val rolloverEnabled: StateFlow<Boolean> = _rolloverEnabled.asStateFlow()

    override fun setRolloverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ROLLOVER, enabled).apply()
        _rolloverEnabled.value = enabled
    }

    private companion object {
        const val KEY_ROLLOVER = "rollover_enabled"
    }
}
