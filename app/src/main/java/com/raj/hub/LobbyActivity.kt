package com.raj.hub

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import java.io.File

/**
 * The lobby: search, filter, favorite and pin your apps.
 * Downloads an app the first time, then it works offline.
 * Apps come from assets/apps.json, so adding an app needs no change here.
 */
class LobbyActivity : AppCompatActivity() {

    private enum class Filter { ALL, FAVORITES, DOWNLOADED, NOT_DOWNLOADED }

    private lateinit var store: AppStore
    private lateinit var list: LinearLayout
    private lateinit var chips: LinearLayout
    private lateinit var search: EditText
    private lateinit var clearBtn: View
    private lateinit var subtitle: TextView
    private lateinit var empty: TextView
    private lateinit var permDot: View

    private var filter = Filter.ALL
    private var query = ""
    private val progress = HashMap<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby)
        store = AppStore(this)

        list = findViewById(R.id.list)
        chips = findViewById(R.id.chips)
        search = findViewById(R.id.search)
        clearBtn = findViewById(R.id.clear)
        subtitle = findViewById(R.id.subtitle)
        empty = findViewById(R.id.empty)
        permDot = findViewById(R.id.permDot)

        findViewById<View>(R.id.permBtn).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        search.doAfterTextChanged {
            query = it?.toString() ?: ""
            clearBtn.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            render()
        }
        search.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                search.clearFocus()
                true
            } else {
                false
            }
        }
        clearBtn.setOnClickListener { search.setText("") }

        render()

        // Pick up newly added apps from the online list (only if one is set in apps.json).
        if (AppRegistry.remoteUrl(this).isNotEmpty() && Net.isOnline(this)) {
            Bg.run {
                try {
                    if (AppRegistry.refreshRemote(this)) runOnUiThread { render() }
                } catch (e: Exception) {
                    // offline or bad file: keep what we have
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ==== ANCHOR: LobbyActivity.render (rebuilds chips + list) ====
    private fun render() {
        if (isFinishing || isDestroyed) return

        val all = AppRegistry.load(this)
        val favs = Prefs.favorites(this)
        val pins = Prefs.pins(this)
        val installed = all.filter { store.isInstalled(it) }.map { it.id }.toSet()

        permDot.visibility = if (Perms.missingCount(this) > 0) View.VISIBLE else View.GONE
        subtitle.text = "${all.size} apps  ·  ${installed.size} ready offline"

        val counts = mapOf(
            Filter.ALL to all.size,
            Filter.FAVORITES to all.count { it.id in favs },
            Filter.DOWNLOADED to installed.size,
            Filter.NOT_DOWNLOADED to all.size - installed.size
        )
        renderChips(counts)

        val q = query.trim().lowercase()
        val shown = all.filter { a ->
            val matches = q.isEmpty() || a.name.lowercase().contains(q) || a.description.lowercase().contains(q)
            val inFilter = when (filter) {
                Filter.ALL -> true
                Filter.FAVORITES -> a.id in favs
                Filter.DOWNLOADED -> a.id in installed
                Filter.NOT_DOWNLOADED -> a.id !in installed
            }
            matches && inFilter
        }
        val pinned = shown.filter { it.id in pins }
        val rest = shown.filter { it.id !in pins }.sortedByDescending { it.id in favs }

        list.removeAllViews()
        if (pinned.isNotEmpty()) {
            addHeader("PINNED", pinned.size)
            for (a in pinned) addCard(a, favs, pins, installed)
        }
        if (rest.isNotEmpty()) {
            addHeader(headerFor(q), rest.size)
            for (a in rest) addCard(a, favs, pins, installed)
        }

        if (shown.isEmpty()) {
            empty.visibility = View.VISIBLE
            empty.text = when {
                q.isNotEmpty() -> "No app matches \"${query.trim()}\""
                filter == Filter.FAVORITES -> "No favorites yet.\nTap ☆ on any app to add it here."
                filter == Filter.DOWNLOADED -> "Nothing downloaded yet.\nOpen an app once to save it offline."
                filter == Filter.NOT_DOWNLOADED -> "Everything is downloaded."
                else -> "No apps yet. Add one in apps.json."
            }
        } else {
            empty.visibility = View.GONE
        }
    }

    private fun headerFor(q: String): String = when {
        q.isNotEmpty() -> "RESULTS"
        filter == Filter.FAVORITES -> "FAVORITES"
        filter == Filter.DOWNLOADED -> "DOWNLOADED"
        filter == Filter.NOT_DOWNLOADED -> "NEED TO DOWNLOAD"
        else -> "ALL APPS"
    }

    private fun addHeader(title: String, count: Int) {
        val tv = TextView(this)
        tv.text = "$title  ·  $count"
        tv.setTextColor(getColor(R.color.sub))
        tv.textSize = 12f
        tv.letterSpacing = 0.15f
        tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        tv.setPadding(dp(2), dp(14), 0, dp(10))
        list.addView(tv)
    }

    // ==== ANCHOR: LobbyActivity.renderChips (filter buttons) ====
    private fun renderChips(counts: Map<Filter, Int>) {
        chips.removeAllViews()
        val defs = listOf(
            Filter.ALL to "All",
            Filter.FAVORITES to "★ Favorites",
            Filter.DOWNLOADED to "Downloaded",
            Filter.NOT_DOWNLOADED to "Need download"
        )
        for ((f, label) in defs) {
            val on = f == filter
            val tv = TextView(this)
            tv.text = "$label  ${counts[f] ?: 0}"
            tv.textSize = 13f
            tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            tv.setTextColor(if (on) Color.WHITE else getColor(R.color.sub))
            tv.setBackgroundResource(if (on) R.drawable.chip_on else R.drawable.chip_off)
            tv.setPadding(dp(16), dp(9), dp(16), dp(9))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(8)
            tv.layoutParams = lp
            tv.setOnClickListener {
                filter = f
                render()
            }
            chips.addView(tv)
        }
    }

    private fun addCard(app: AppInfo, favs: Set<String>, pins: Set<String>, installed: Set<String>) {
        val v = layoutInflater.inflate(R.layout.item_app, list, false)
        bind(v, app, app.id in favs, app.id in pins, app.id in installed)
        list.addView(v)
    }

    // ==== ANCHOR: LobbyActivity.bind (fills one app card) ====
    private fun bind(v: View, app: AppInfo, isFav: Boolean, isPinned: Boolean, isInstalled: Boolean) {
        v.findViewById<TextView>(R.id.name).text = app.name
        v.findViewById<TextView>(R.id.desc).text = app.description
        v.findViewById<View>(R.id.stripe).setBackgroundColor(app.accent)
        loadIcon(v.findViewById(R.id.icon), app)

        val status = v.findViewById<TextView>(R.id.status)
        val bar = v.findViewById<ProgressBar>(R.id.progress)
        val pct = progress[app.id]
        if (pct != null) {
            status.text = "Downloading…  $pct%"
            status.setTextColor(app.accent)
            bar.visibility = View.VISIBLE
            bar.progress = pct
            bar.progressTintList = ColorStateList.valueOf(app.accent)
        } else if (isInstalled) {
            val mb = store.sizeBytes(app) / 1048576.0
            status.text = "●  Ready offline  ·  " + String.format("%.1f MB", mb)
            status.setTextColor(getColor(R.color.green))
            bar.visibility = View.GONE
        } else {
            status.text = "●  Tap to download  ·  needs internet once"
            status.setTextColor(getColor(R.color.amber))
            bar.visibility = View.GONE
        }

        val fav = v.findViewById<ImageView>(R.id.favBtn)
        fav.setImageResource(if (isFav) R.drawable.ic_star_on else R.drawable.ic_star_off)
        fav.setColorFilter(if (isFav) getColor(R.color.amber) else getColor(R.color.sub))
        fav.setOnClickListener { toggleFavorite(app) }

        val pin = v.findViewById<ImageView>(R.id.pinBtn)
        pin.setImageResource(if (isPinned) R.drawable.ic_pin_on else R.drawable.ic_pin_off)
        pin.setColorFilter(if (isPinned) app.accent else getColor(R.color.sub))
        pin.setOnClickListener { togglePin(app) }

        v.setOnClickListener { open(app) }
        v.setOnLongClickListener {
            showOptions(app)
            true
        }
    }

    private fun toggleFavorite(app: AppInfo) {
        val on = Prefs.toggleFavorite(this, app.id)
        Toast.makeText(
            this,
            if (on) "${app.name} added to favorites" else "${app.name} removed from favorites",
            Toast.LENGTH_SHORT
        ).show()
        render()
    }

    private fun togglePin(app: AppInfo) {
        val on = Prefs.togglePin(this, app.id)
        Toast.makeText(
            this,
            if (on) "${app.name} pinned to top" else "${app.name} unpinned",
            Toast.LENGTH_SHORT
        ).show()
        render()
    }

    private fun loadIcon(img: ImageView, app: AppInfo) {
        try {
            assets.open("icons/${app.id}.png").use { img.setImageBitmap(BitmapFactory.decodeStream(it)) }
            return
        } catch (e: Exception) {
            // no bundled icon, try the downloaded one
        }
        val name = app.files.firstOrNull { it.contains("icon") }
        if (name != null) {
            val f = File(store.siteDir(app), name)
            if (f.isFile) {
                val bmp = BitmapFactory.decodeFile(f.absolutePath)
                if (bmp != null) {
                    img.setImageBitmap(bmp)
                    return
                }
            }
        }
        img.setImageBitmap(letterIcon(app))
    }

    /** Icon for apps that have no picture yet: the first letter on the app's colour. */
    private fun letterIcon(app: AppInfo): Bitmap {
        val size = 144
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(app.accent)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.textSize = 72f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(app.name.take(1).uppercase(), size / 2f, y, p)
        return bmp
    }

    private fun open(app: AppInfo) {
        if (progress.containsKey(app.id)) return
        if (store.isInstalled(app)) {
            launch(app)
            return
        }
        if (!Net.isOnline(this)) {
            Toast.makeText(this, "Internet is needed the first time you open ${app.name}", Toast.LENGTH_LONG).show()
            return
        }
        download(app, true)
    }

    private fun download(app: AppInfo, thenLaunch: Boolean) {
        if (progress.containsKey(app.id)) return
        if (!Net.isOnline(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }
        progress[app.id] = 0
        render()
        Bg.run {
            try {
                store.sync(app) { p ->
                    runOnUiThread {
                        progress[app.id] = p
                        render()
                    }
                }
                Prefs.setLastCheck(this, app.id)
                runOnUiThread {
                    progress.remove(app.id)
                    render()
                    if (thenLaunch && !isFinishing && !isDestroyed) launch(app)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.remove(app.id)
                    render()
                    Toast.makeText(this, "Download failed. Check your internet and try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ==== ANCHOR: LobbyActivity.showOptions (long-press menu) ====
    private fun showOptions(app: AppInfo) {
        if (progress.containsKey(app.id)) return
        val installed = store.isInstalled(app)
        val isFav = app.id in Prefs.favorites(this)
        val isPinned = app.id in Prefs.pins(this)

        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        labels.add(if (isFav) "Remove from favorites" else "Add to favorites")
        actions.add { toggleFavorite(app) }

        labels.add(if (isPinned) "Unpin from top" else "Pin to top")
        actions.add { togglePin(app) }

        if (installed) {
            labels.add("Update now")
            actions.add { download(app, false) }
            labels.add("Remove offline copy")
            actions.add {
                store.remove(app)
                if (Prefs.lastApp(this) == app.id) Prefs.setLastApp(this, null)
                render()
            }
        } else {
            labels.add("Download")
            actions.add { download(app, false) }
        }

        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun launch(app: AppInfo) {
        Prefs.setLastApp(this, app.id)
        startActivity(Intent(this, AppActivity::class.java).putExtra(AppActivity.EXTRA_ID, app.id))
        finish()
    }
}
