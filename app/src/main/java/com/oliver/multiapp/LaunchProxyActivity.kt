package com.oliver.multiapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Runs inside the Clone Space: does what the main app asks (open / add / import). */
class LaunchProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            when (intent.action) {
                AppEngine.ACTION_LAUNCH -> {
                    val pkg = intent.getStringExtra("pkg")
                    if (pkg != null) packageManager.getLaunchIntentForPackage(pkg)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                    }
                }
                AppEngine.ACTION_ADD -> {
                    val pkg = intent.getStringExtra("pkg")
                    val ok = pkg != null && AppEngine.installExisting(this, pkg)
                    Toast.makeText(this, if (ok) "Added to Clone Space" else "Could not add this app",
                        Toast.LENGTH_LONG).show()
                }
                AppEngine.ACTION_IMPORT -> {
                    val uri = intent.clipData?.getItemAt(0)?.uri
                    val path = if (uri != null) AppEngine.copyApk(this, uri) else null
                    if (path != null) AppEngine.installApk(this, path)
                    else Toast.makeText(this, "Import failed", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
