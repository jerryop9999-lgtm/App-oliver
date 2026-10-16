package com.oliver.multiapp

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager

class AdminReceiver : DeviceAdminReceiver() {
    // Runs INSIDE the new Clone Space (work profile) right after it is created.
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AdminReceiver::class.java)
        try { dpm.setProfileName(admin, "OLIVER Clone Space") } catch (e: Exception) {}
        try { dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES) } catch (e: Exception) {}
        try { dpm.setProfileEnabled(admin) } catch (e: Exception) {}
        try {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        } catch (e: Exception) {}
    }
}
