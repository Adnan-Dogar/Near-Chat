package com.example.neartalk

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Standalone demo launcher: no Firebase, servers, account, or internet required. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, DemoActivity::class.java))
        finish()
    }
}
