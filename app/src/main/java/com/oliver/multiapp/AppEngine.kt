package com.oliver.multiapp

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/** Helpers that run inside the Clone Space (work profile). */
object AppEngine {

    /** Apps present on the phone (any profile) that are not yet in this Clone Space. */
    fun candidates(c: Context): List<ApplicationInfo> {
        val pm = c.packageManager
        return pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .filter {
                it.packageName != c.packageName &&
                    ((it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
            }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    /** Clone App: install an existing phone app into the Clone Space (separate data/accounts). */
    fun installExisting(c: Context, pkg: String): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(c, AdminReceiver::class.java)
        return try { dpm.installExistingPackage(admin, pkg) } catch (e: Exception) { false }
    }

    /** Import APK: copy the picked file into app storage. Returns the saved path or null. */
    fun copyApk(c: Context, uri: Uri): String? {
        val dir = File(c.filesDir, "apks").apply { mkdirs() }
        val tmp = File(dir, "import.tmp")
        c.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        val info = c.packageManager.getPackageArchiveInfo(tmp.path, 0)
        if (info == null) { tmp.delete(); return null }
        val dest = File(dir, "${info.packageName}.apk")
        tmp.copyTo(dest, overwrite = true)
        tmp.delete()
        return dest.path
    }

    /** Opens the system installer for the APK (installs into the Clone Space). */
    fun installApk(c: Context, path: String) {
        val uri = FileProvider.getUriForFile(c, c.packageName + ".fileprovider", File(path))
        c.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
