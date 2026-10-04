package com.tableads.app.kiosk

import android.app.admin.DeviceAdminReceiver

/** Needed so the app can be made device owner for full kiosk (lock task) mode. */
class AdminReceiver : DeviceAdminReceiver()
