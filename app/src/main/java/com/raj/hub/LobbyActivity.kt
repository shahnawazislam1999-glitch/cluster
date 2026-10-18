package com.raj.hub

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** The lobby: pick which app to use. Downloads an app the first time, then it works offline. */
class LobbyActivity : AppCompatActivity() {

    private lateinit var store: AppStore
    private lateinit var list: LinearLayout
    private val progress = HashMap<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby)
        store = AppStore(this)
        list = findViewById(R.id.list)
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

    private fun render() {
        if (isFinishing || isDestroyed) return
        list.removeAllViews()
        for (app in AppRegistry.load(this)) {
            val v = layoutInflater.inflate(R.layout.item_app, list, false)
            bind(v, app)
            list.addView(v)
        }
    }

    private fun bind(v: View, app: AppInfo) {
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
            bar.progressTintList = android.content.res.ColorStateList.valueOf(app.accent)
        } else if (store.isInstalled(app)) {
            val mb = store.sizeBytes(app) / 1048576.0
            status.text = "●  Ready offline  ·  " + String.format("%.1f MB", mb)
            status.setTextColor(getColor(R.color.green))
            bar.visibility = View.GONE
        } else {
            status.text = "●  Tap to download  ·  needs internet once"
            status.setTextColor(getColor(R.color.amber))
            bar.visibility = View.GONE
        }

        v.setOnClickListener { open(app) }
        v.setOnLongClickListener {
            showOptions(app)
            true
        }
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
        img.setImageDrawable(null)
        img.setBackgroundColor(app.accent)
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

    private fun showOptions(app: AppInfo) {
        if (progress.containsKey(app.id)) return
        val installed = store.isInstalled(app)
        val items = if (installed) {
            arrayOf("Update now", "Remove offline copy")
        } else {
            arrayOf("Download")
        }
        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(items) { _, which ->
                if (which == 0) {
                    download(app, false)
                } else {
                    store.remove(app)
                    if (Prefs.lastApp(this) == app.id) Prefs.setLastApp(this, null)
                    render()
                }
            }
            .show()
    }

    private fun launch(app: AppInfo) {
        Prefs.setLastApp(this, app.id)
        startActivity(Intent(this, AppActivity::class.java).putExtra(AppActivity.EXTRA_ID, app.id))
        finish()
    }
}
