/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.apng

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activity)

    findViewById<Button>(R.id.demo_button).setOnClickListener {
      startActivity(Intent(this, DemoActivity::class.java))
    }
    findViewById<Button>(R.id.player_button).setOnClickListener {
      startActivity(Intent(this, PlayerActivity::class.java))
    }
  }
}
