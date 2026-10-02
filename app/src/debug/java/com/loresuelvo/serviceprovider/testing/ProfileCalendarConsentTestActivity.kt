package com.loresuelvo.serviceprovider.testing

import android.app.Activity
import android.os.Bundle
import android.view.View

/** Debug-only target for deterministic instrumented Activity-result boundary tests. */
class ProfileCalendarConsentTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this))
    }
}
