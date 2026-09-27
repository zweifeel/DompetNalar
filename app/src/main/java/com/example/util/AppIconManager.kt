package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.data.model.AppIconEdition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppIconManager {
    private const val TAG = "AppIconManager"
    private const val PREFS_NAME = "finsmart_icon_prefs"
    private const val KEY_ACTIVE_ICON = "active_icon_edition"

    private val _currentIconEdition = MutableStateFlow(AppIconEdition.DEFAULT_CLASSIC)
    val currentIconEdition: StateFlow<AppIconEdition> = _currentIconEdition.asStateFlow()

    private const val ALIAS_DEFAULT = "com.example.MainActivityDefault"
    private const val ALIAS_GOLD = "com.example.MainActivityGold"
    private const val ALIAS_DARK = "com.example.MainActivityDark"
    private const val ALIAS_CYBERPUNK = "com.example.MainActivityCyberpunk"

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_ACTIVE_ICON, AppIconEdition.DEFAULT_CLASSIC.name)
        val edition = try {
            AppIconEdition.valueOf(savedName ?: AppIconEdition.DEFAULT_CLASSIC.name)
        } catch (_: Exception) {
            AppIconEdition.DEFAULT_CLASSIC
        }
        _currentIconEdition.value = edition
    }

    fun canUnlockIcon(edition: AppIconEdition, streak: Int, isMasterDev: Boolean = false): Boolean {
        if (isMasterDev) return true
        if (!edition.isLockedByDefault) return true
        return streak >= edition.requiredStreak
    }

    /**
     * Safely switches the application launcher icon via Activity-Alias.
     *
     * Protection for OEM Launchers (MIUI, HyperOS, ColorOS, FunTouchOS, OneUI):
     * 1. Uses PackageManager.DONT_KILL_APP to avoid abrupt force-closure.
     * 2. Enables the new target alias FIRST before disabling others to prevent no-launcher-state.
     * 3. Wraps operations in defensive try-catch blocks to prevent crashes if dynamic alias modification is restricted.
     */
    fun setAppIcon(
        context: Context,
        edition: AppIconEdition,
        currentStreak: Int,
        isMasterDev: Boolean = false
    ): Boolean {
        if (!canUnlockIcon(edition, currentStreak, isMasterDev)) {
            Log.w(TAG, "Cannot set locked icon $edition with streak $currentStreak")
            return false
        }

        val pm = context.packageManager
        val packageName = context.packageName

        val targetAlias = when (edition) {
            AppIconEdition.DEFAULT_CLASSIC -> ALIAS_DEFAULT
            AppIconEdition.GOLD_LUXURY -> ALIAS_GOLD
            AppIconEdition.DARK_OBSIDIAN -> ALIAS_DARK
            AppIconEdition.CYBERPUNK_NEON -> ALIAS_CYBERPUNK
        }

        val allAliases = listOf(ALIAS_DEFAULT, ALIAS_GOLD, ALIAS_DARK, ALIAS_CYBERPUNK)
        val targetComponent = ComponentName(packageName, targetAlias)

        try {
            // STEP 1: Enable the target Activity-Alias first with DONT_KILL_APP
            pm.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "Successfully enabled target alias: $targetAlias")

            // STEP 2: Disable other inactive aliases with DONT_KILL_APP
            for (alias in allAliases) {
                if (alias != targetAlias) {
                    try {
                        val otherComponent = ComponentName(packageName, alias)
                        pm.setComponentEnabledSetting(
                            otherComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    } catch (aliasEx: Exception) {
                        Log.w(TAG, "Non-fatal: could not disable alias $alias: ${aliasEx.message}")
                    }
                }
            }

            // STEP 3: Persist active preference
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_ACTIVE_ICON, edition.name).apply()
            _currentIconEdition.value = edition
            Log.d(TAG, "Dynamic App Icon safely updated to: $edition")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "OEM Launcher prevented dynamic component modification: ${e.message}", e)
            return false
        }
    }
}
