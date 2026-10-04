package com.tableads.app.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.tableads.app.MainActivity

/**
 * Full kiosk mode requires the app to be device owner:
 *   adb shell dpm set-device-owner com.tableads.app/.kiosk.AdminReceiver
 * Without it the app still runs fullscreen (and can be set as the Home app), but the
 * system navigation is not locked.
 */
object Kiosk {
    private const val TAG = "Kiosk"

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)

    fun isLocked(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    fun enter(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(activity, AdminReceiver::class.java)
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(activity.packageName, "com.android.settings"))
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, home, ComponentName(activity, MainActivity::class.java))
            dpm.setKeyguardDisabled(admin, true)
            if (!isLocked(activity)) activity.startLockTask()
        }.onFailure { Log.w(TAG, "Could not enter lock task mode", it) }
    }

    fun exit(activity: Activity) {
        runCatching { if (isLocked(activity)) activity.stopLockTask() }
            .onFailure { Log.w(TAG, "Could not exit lock task mode", it) }
    }

    /** Fully releases kiosk control so the tablet can be repurposed. */
    fun releaseDeviceOwner(activity: Activity) {
        exit(activity)
        if (!isDeviceOwner(activity)) return
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(activity, AdminReceiver::class.java)
        runCatching {
            dpm.clearPackagePersistentPreferredActivities(admin, activity.packageName)
            dpm.setKeyguardDisabled(admin, false)
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(activity.packageName)
        }.onFailure { Log.w(TAG, "Could not release device owner", it) }
    }
}
