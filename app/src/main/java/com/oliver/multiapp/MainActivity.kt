package com.oliver.multiapp

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var listView: ListView

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val pkg = AppEngine.importApk(this, uri)
        Toast.makeText(this, pkg ?: "Invalid APK", Toast.LENGTH_SHORT).show()
        refresh()
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

        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo)
        }, LinearLayout.LayoutParams(300, 300).apply { gravity = Gravity.CENTER_HORIZONTAL })

        fun btn(t: String, a: () -> Unit) = Button(this).apply {
            text = t
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFB00000.toInt())
            setOnClickListener { a() }
        }
        root.addView(btn("Add App") { showInstalled() })
        root.addView(btn("Import APK") { pickApk.launch(arrayOf("application/vnd.android.package-archive")) })

        listView = ListView(this)
        root.addView(listView)

        frame.addView(root)
        setContentView(frame)
        refresh()
    }

    private fun refresh() {
        val items = AppEngine.list(this)
        val pm = packageManager
        val names = items.map {
            val label = try { pm.getApplicationInfo(it.pkg, 0).loadLabel(pm).toString() } catch (e: Exception) { it.pkg }
            if (it.instance == 0) label else "$label (Clone ${it.instance})"
        }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.setOnItemClickListener { _, _, i, _ ->
            AlertDialog.Builder(this).setItems(arrayOf("Open", "Clone App")) { _, w ->
                if (w == 0) AppEngine.launch(this, items[i]) else { AppEngine.add(this, items[i].pkg); refresh() }
            }.show()
        }
    }

    private fun showInstalled() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).sortedBy { it.loadLabel(pm).toString() }
        AlertDialog.Builder(this).setTitle("Add App")
            .setItems(apps.map { it.loadLabel(pm).toString() }.toTypedArray()) { _, i ->
                AppEngine.add(this, apps[i].activityInfo.packageName); refresh()
            }.show()
    }
}
