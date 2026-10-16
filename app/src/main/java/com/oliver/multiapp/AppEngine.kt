package com.oliver.multiapp

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object AppEngine {
    data class Entry(val pkg: String, val instance: Int, val apkPath: String? = null)

    /** Plug a real virtual engine in here later. Return true if it launched the app. */
    interface Engine { fun launch(c: Context, e: Entry): Boolean }
    var engine: Engine? = null

    private fun prefs(c: Context) = c.getSharedPreferences("apps", 0)

    fun list(c: Context): List<Entry> =
        prefs(c).getStringSet("list", emptySet())!!.map {
            val p = it.split("|", limit = 3)
            Entry(p[0], p[1].toInt(), p.getOrNull(2)?.ifEmpty { null })
        }.sortedWith(compareBy({ it.pkg }, { it.instance }))

    private fun save(c: Context, all: List<Entry>) =
        prefs(c).edit().putStringSet(
            "list", all.map { "${it.pkg}|${it.instance}|${it.apkPath ?: ""}" }.toSet()
        ).apply()

    /** Add App / Clone App: always creates a NEW instance, so you can have many clones. */
    fun add(c: Context, pkg: String, apkPath: String? = null) {
        val all = list(c)
        val same = all.filter { it.pkg == pkg }
        val next = same.maxOfOrNull { it.instance }?.plus(1) ?: 0
        val path = apkPath ?: same.firstNotNullOfOrNull { it.apkPath }
        save(c, all + Entry(pkg, next, path))
    }

    fun remove(c: Context, e: Entry) = save(c, list(c) - e)

    private fun archiveInfo(c: Context, path: String): ApplicationInfo? {
        val info = c.packageManager.getPackageArchiveInfo(path, 0) ?: return null
        val ai = info.applicationInfo ?: return null
        ai.sourceDir = path
        ai.publicSourceDir = path
        return ai
    }

    private fun installedInfo(c: Context, pkg: String): ApplicationInfo? =
        try { c.packageManager.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }

    fun label(c: Context, e: Entry): String {
        val pm = c.packageManager
        val ai = installedInfo(c, e.pkg) ?: e.apkPath?.let { archiveInfo(c, it) }
        return ai?.loadLabel(pm)?.toString() ?: e.pkg
    }

    fun icon(c: Context, e: Entry): Drawable {
        val pm = c.packageManager
        val ai = installedInfo(c, e.pkg) ?: e.apkPath?.let { archiveInfo(c, it) }
        return ai?.loadIcon(pm) ?: pm.defaultActivityIcon
    }

    /** Import APK: copy it into app storage, read its package + icon, add to the list. */
    fun importApk(c: Context, uri: Uri): String? {
        val dir = File(c.filesDir, "apks").apply { mkdirs() }
        val tmp = File(dir, "import.tmp")
        c.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        val info = c.packageManager.getPackageArchiveInfo(tmp.path, 0)
        if (info == null) { tmp.delete(); return null }
        val dest = File(dir, "${info.packageName}.apk")
        tmp.copyTo(dest, overwrite = true); tmp.delete()
        if (list(c).none { it.pkg == info.packageName }) add(c, info.packageName, dest.path)
        return info.packageName
    }

    fun launch(c: Context, e: Entry) {
        engine?.let { if (it.launch(c, e)) return }
        val i = c.packageManager.getLaunchIntentForPackage(e.pkg)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
            return
        }
        val path = e.apkPath
        if (path != null && File(path).exists()) {
            val uri = FileProvider.getUriForFile(c, c.packageName + ".fileprovider", File(path))
            c.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Toast.makeText(c, "Install the APK, then tap Open again", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(c, "App not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
