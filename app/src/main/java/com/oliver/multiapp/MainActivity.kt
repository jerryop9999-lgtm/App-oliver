package com.oliver.multiapp

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var grid: GridView

    private class Item(val label: String, val icon: Drawable)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
        root.addView(ImageView(this).apply { setImageResource(R.drawable.logo) },
            LinearLayout.LayoutParams(300, 300).apply { gravity = Gravity.CENTER_HORIZONTAL })

        fun btn(t: String, a: () -> Unit) = Button(this).apply {
            text = t
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFB00000.toInt())
            setOnClickListener { a() }
        }
        root.addView(btn("Add App") { showInstalled() })
        root.addView(btn("Import APK") { pickApk.launch(arrayOf("application/vnd.android.package-archive")) })

        grid = GridView(this).apply { numColumns = 3 }
        root.addView(grid)

        frame.addView(root)
        setContentView(frame)
        refresh()
    }

    private fun refresh() {
        val entries = AppEngine.list(this)
        val items = entries.map {
            val name = AppEngine.label(this, it)
            Item(if (it.instance == 0) name else "$name (Clone ${it.instance})", AppEngine.icon(this, it))
        }
        grid.adapter = IconAdapter(items, true)
        grid.setOnItemClickListener { _, _, i, _ ->
            val e = entries[i]
            AlertDialog.Builder(this).setTitle(items[i].label)
                .setItems(arrayOf("Open", "Clone App", "Remove")) { _, w ->
                    when (w) {
                        0 -> AppEngine.launch(this, e)
                        1 -> { AppEngine.add(this, e.pkg); refresh() }
                        2 -> { AppEngine.remove(this, e); refresh() }
                    }
                }.show()
        }
    }

    private fun showInstalled() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val items = apps.map { Item(it.loadLabel(pm).toString(), it.loadIcon(pm)) }
        AlertDialog.Builder(this).setTitle("Add App")
            .setAdapter(IconAdapter(items, false)) { _, i ->
                AppEngine.add(this, apps[i].activityInfo.packageName); refresh()
            }.show()
    }
}
