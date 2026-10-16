package com.oliver.multiapp

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout

    private val dpm by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }

    // true when this copy of the app is running INSIDE the Clone Space (work profile)
    private val inClone: Boolean get() = dpm.isProfileOwnerApp(packageName)

    private class Item(val label: String, val icon: Drawable)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    private inner class IconAdapter(val items: List<Item>, val asGrid: Boolean) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            val row = LinearLayout(this@MainActivity).apply {
                orientation = if (asGrid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = if (asGrid) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            row.addView(ImageView(this@MainActivity).apply { setImageDrawable(item.icon) },
                LinearLayout.LayoutParams(dp(56), dp(56)))
            row.addView(TextView(this@MainActivity).apply {
                text = item.label
                setTextColor(0xFFFFFFFF.toInt())
                textSize = if (asGrid) 12f else 16f
                maxLines = 2
                gravity = if (asGrid) Gravity.CENTER_HORIZONTAL else Gravity.START
                setPadding(if (asGrid) 0 else dp(12), if (asGrid) dp(6) else 0, 0, 0)
            }, LinearLayout.LayoutParams(-2, -2))
            return row
        }
    }

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        if (inClone) {
            val path = AppEngine.copyApk(this, uri)
            if (path == null) toast("Invalid APK") else AppEngine.installApk(this, path)
        } else {
            sendImportToClone(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val frame = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        frame.addView(ImageView(this).apply {
            setImageResource(R.drawable.bg)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.35f
        }, FrameLayout.LayoutParams(-1, -1))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
        }
        root.addView(ImageView(this).apply { setImageResource(R.drawable.logo) },
            LinearLayout.LayoutParams(300, 300).apply { gravity = Gravity.CENTER_HORIZONTAL })

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)

        frame.addView(root)
        setContentView(frame)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        if (inClone) renderClone() else renderMain()
    }

    private fun btn(t: String, a: () -> Unit) = Button(this).apply {
        text = t
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFFB00000.toInt())
        setOnClickListener { a() }
    }

    private fun info(t: String) = TextView(this).apply {
        text = t
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 15f
        setPadding(0, dp(12), 0, dp(12))
    }

    // ---------- Normal (main) profile = the Launcher for cloned apps ----------
    private fun cloneUser(): UserHandle? {
        if (Build.VERSION.SDK_INT < 28) return null
        return try {
            (getSystemService("crossprofileapps") as CrossProfileApps).targetUserProfiles.firstOrNull()
        } catch (e: Exception) { null }
    }

    private fun renderMain() {
        // Only the Clone Space copy should answer "open clone" requests
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, LaunchProxyActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {}

        val um = getSystemService(Context.USER_SERVICE) as UserManager
        val otherProfileExists = um.userProfiles.size > 1
        val user = cloneUser()
        val ours = if (Build.VERSION.SDK_INT >= 28) user != null else otherProfileExists

        if (ours) {
            content.addView(btn("Add App") { showAddFromMain() })
            content.addView(btn("Import APK") { pickApk.launch(arrayOf("application/vnd.android.package-archive")) })
            content.addView(btn("Open Space") { openCloneSpace() })
            if (user != null) renderCloneLauncher(user)
        } else {
            // First run: offer to create the built-in Space automatically (once)
            val prefs = getSharedPreferences("space", 0)
            if (!prefs.getBoolean("asked", false)) {
                prefs.edit().putBoolean("asked", true).apply()
                createCloneSpace()
            }
            content.addView(info(
                if (otherProfileExists)
                    "This phone already has another separate space (work profile or system clone), " +
                    "not created by this app. Android allows only one. Tap Create Clone Space; " +
                    "if it is refused, that other space must be removed first."
                else
                    "Create a Clone Space: an isolated copy of Android where cloned apps " +
                    "keep their own accounts and data."
            ))
            content.addView(btn("Create Clone Space") { createCloneSpace() })
        }
    }

    private fun renderCloneLauncher(user: UserHandle) {
        val la = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val raw: List<LauncherActivityInfo> = try { la.getActivityList(null, user) } catch (e: Exception) { emptyList() }
        val acts = raw.filter { it.applicationInfo.packageName != packageName }
            .sortedBy { it.label.toString().lowercase() }
        if (acts.isEmpty()) {
            content.addView(info("No cloned apps yet. Tap Add App or Import APK."))
            return
        }
        val items = acts.map { Item(it.label.toString(), it.getBadgedIcon(0)) }
        val grid = GridView(this).apply { numColumns = 3 }
        grid.adapter = IconAdapter(items, true)
        grid.setOnItemClickListener { _, _, i, _ -> launchClone(la, user, acts[i]) }
        content.addView(grid)
    }

    private fun launchClone(la: LauncherApps, user: UserHandle, a: LauncherActivityInfo) {
        try {
            la.startMainActivity(a.componentName, user, null, null)
        } catch (e: Exception) {
            try {
                startActivity(
                    Intent(AppEngine.ACTION_LAUNCH)
                        .addCategory(Intent.CATEGORY_DEFAULT)
                        .putExtra("pkg", a.applicationInfo.packageName)
                )
            } catch (e2: Exception) {
                toast("Could not open: ${e.message}")
            }
        }
    }

    private fun showAddFromMain() {
        val pm = packageManager
        val cloned: Set<String> = try {
            val u = cloneUser()
            if (u == null) emptySet()
            else (getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
                .getActivityList(null, u).map { it.applicationInfo.packageName }.toSet()
        } catch (e: Exception) { emptySet() }
        val apps = launcherApps().filter { it.activityInfo.packageName !in cloned }
        if (apps.isEmpty()) { toast("No apps available to add"); return }
        val items = apps.map { Item(it.loadLabel(pm).toString(), it.loadIcon(pm)) }
        AlertDialog.Builder(this).setTitle("Add App")
            .setAdapter(IconAdapter(items, false)) { _, i ->
                try {
                    startActivity(
                        Intent(AppEngine.ACTION_ADD)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .putExtra("pkg", apps[i].activityInfo.packageName)
                    )
                    Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) render() }, 2500)
                } catch (e: Exception) {
                    toast("Could not reach the Space. Tap Open Space and add it there.")
                }
            }.show()
    }

    private fun sendImportToClone(uri: Uri) {
        try {
            startActivity(
                Intent(AppEngine.ACTION_IMPORT)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .setClipData(ClipData.newRawUri("apk", uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            toast("Import failed. Tap Open Space and import there.")
        }
    }

    private fun openCloneSpace() {
        if (Build.VERSION.SDK_INT < 28) {
            toast("Open the copy with the briefcase badge from your app drawer")
            return
        }
        try {
            val cpa = getSystemService("crossprofileapps") as CrossProfileApps
            val target = cpa.targetUserProfiles.firstOrNull()
            if (target == null) { toast("Clone Space not found"); return }
            cpa.startMainActivity(ComponentName(this, MainActivity::class.java), target)
        } catch (e: Exception) {
            toast("Could not open: ${e.message}")
        }
    }

    private fun createCloneSpace() {
        if (!dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            toast("This phone does not allow creating a Clone Space")
            return
        }
        val admin = ComponentName(this, AdminReceiver::class.java)
        val i = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
        try { startActivity(i) } catch (e: Exception) { toast("Not supported on this phone") }
    }

    // ---------- Inside the Clone Space ----------
    private fun launcherApps() = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
    ).filter { it.activityInfo.packageName != packageName }
        .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

    private fun renderClone() {
        AppEngine.registerBridge(this)
        content.addView(btn("Add App") { showCandidates() })
        content.addView(btn("Import APK") { pickApk.launch(arrayOf("application/vnd.android.package-archive")) })

        val pm = packageManager
        val apps = launcherApps()
        val items = apps.map { Item(it.loadLabel(pm).toString(), it.loadIcon(pm)) }
        val grid = GridView(this).apply { numColumns = 3 }
        grid.adapter = IconAdapter(items, true)
        grid.setOnItemClickListener { _, _, i, _ ->
            val pkg = apps[i].activityInfo.packageName
            AlertDialog.Builder(this).setTitle(items[i].label)
                .setItems(arrayOf("Open", "Uninstall from Clone Space")) { _, w ->
                    if (w == 0) pm.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                    else startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
                }.show()
        }
        content.addView(grid)
    }

    private fun showCandidates() {
        val pm = packageManager
        val have = launcherApps().map { it.activityInfo.packageName }.toSet()
        val apps = AppEngine.candidates(this).filter { it.packageName !in have }
        if (apps.isEmpty()) { toast("No apps available to add"); return }
        val items = apps.map { Item(it.loadLabel(pm).toString(), it.loadIcon(pm)) }
        AlertDialog.Builder(this).setTitle("Add App to Clone Space")
            .setAdapter(IconAdapter(items, false)) { _, i ->
                val ok = AppEngine.installExisting(this, apps[i].packageName)
                toast(if (ok) "Added: ${items[i].label}" else "Could not add this app")
                render()
            }.show()
    }
}
