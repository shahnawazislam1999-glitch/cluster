package com.raj.hub

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/** Shows the Raj Hub logo with "from Raj", then jumps to the last used app (or the lobby). */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val center = findViewById<View>(R.id.center)
        val footer = findViewById<View>(R.id.footer)
        center.alpha = 0f
        footer.alpha = 0f
        center.animate().alpha(1f).setDuration(450).start()
        footer.animate().alpha(1f).setDuration(450).setStartDelay(150).start()

        Handler(Looper.getMainLooper()).postDelayed({ route() }, 1100)
    }

    private fun route() {
        if (isFinishing || isDestroyed) return
        val last = Prefs.lastApp(this)
        val store = AppStore(this)
        val target = AppRegistry.load(this).firstOrNull { it.id == last && store.isInstalled(it) }
        val next = if (target != null) {
            Intent(this, AppActivity::class.java).putExtra(AppActivity.EXTRA_ID, target.id)
        } else {
            Intent(this, LobbyActivity::class.java)
        }
        startActivity(next)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
