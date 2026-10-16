package com.oliver.multiapp

import android.content.Context
import android.net.Uri
import java.io.File

object AppEngine {
    data class Entry(val pkg: String, val instance: Int)

    private fun prefs(c: Context) = c.getSharedPreferences("apps", 0)

    fun list(c: Context): List<Entry> =
        prefs(c).getStringSet("list", emptySet())!!.map {
            val p = it.split("|"); Entry(p[0], p[1].toInt())
        }.sortedWith(compareBy({ it.pkg }, { it.instance }))

    private fun save(c: Context, all: List<Entry>) =
        prefs(c).edit().putStringSet("list", all.map { "${it.pkg}|${it.instance}" }.toSet()).apply()

    // Add App / Clone App: new instance of the same package
    fun add(c: Context, pkg: String) {
        val next = list(c).filter { it.pkg == pkg }.maxOfOrNull { it.instance }?.plus(1) ?: 0
        // TODO engine: create an isolated instance (data/process)
        save(c, list(c) + Entry(pkg, next))
    }

    // Import APK
    fun importApk(c: Context, uri: Uri): String? {
        val f = File(c.cacheDir, "import.apk")
        c.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
        val info = c.packageManager.getPackageArchiveInfo(f.path, 0) ?: return null
        // TODO engine: install APK into the virtual space
        add(c, info.packageName)
        return info.packageName
    }

    fun launch(c: Context, e: Entry) {
        // TODO engine: launch by instance
        c.packageManager.getLaunchIntentForPackage(e.pkg)?.let { c.startActivity(it) }
    }
}
