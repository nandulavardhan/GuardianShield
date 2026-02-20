package com.guardianshield.app.ghostmode

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class GhostMode(val aliasName: String, val displayName: String) {
    DEFAULT(".MainActivityDefault", "Guardian Shield"),
    CALCULATOR(".MainActivityCalculator", "Calculator"),
    NOTES(".MainActivityNotes", "My Notes")
}

object GhostModeManager {

    private val allAliases = GhostMode.entries.map { it.aliasName }

    /**
     * Switches the app's launcher icon and name by enabling the target
     * activity-alias and disabling all others.
     *
     * The launcher shortcut updates within a few seconds.
     * The app process is NOT killed (DONT_KILL_APP flag).
     */
    fun switchAppIdentity(context: Context, mode: GhostMode) {
        val pm = context.packageManager
        val packageName = context.packageName

        allAliases.forEach { alias ->
            val component = ComponentName(packageName, "$packageName$alias")
            val newState = if (alias == mode.aliasName)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            pm.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /**
     * Returns which Ghost Mode alias is currently active.
     */
    fun getCurrentMode(context: Context): GhostMode {
        val pm = context.packageManager
        val packageName = context.packageName

        for (mode in GhostMode.entries) {
            val component = ComponentName(packageName, "$packageName${mode.aliasName}")
            val state = pm.getComponentEnabledSetting(component)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && mode == GhostMode.DEFAULT)
            ) {
                return mode
            }
        }
        return GhostMode.DEFAULT
    }
}
