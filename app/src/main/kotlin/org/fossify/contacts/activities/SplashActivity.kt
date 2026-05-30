package org.fossify.contacts.activities

import android.content.Intent
import org.fossify.commons.activities.BaseSplashActivity

// Commons' sideloading detection is neutralised app-wide in App.onCreate, plus a resource keep
// rule (res/raw/keep.xml) keeps the drawable it probes, so there is nothing to handle here.
class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
