package com.example.neartalk

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Separate Firebase-free preview, opens the working local features immediately. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, DemoActivity::class.java))
        finish()
    }
}
