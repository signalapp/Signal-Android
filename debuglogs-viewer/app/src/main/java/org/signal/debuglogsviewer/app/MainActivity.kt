/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.debuglogsviewer.app

import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import org.signal.debuglogsviewer.app.webview.setupWebView

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContentView(R.layout.activity_main)

    val webview: WebView = findViewById(R.id.webview)
    val findButton: Button = findViewById(R.id.findButton)
    val filterLevelButton: Button = findViewById(R.id.filterLevelButton)
    val editButton: Button = findViewById(R.id.editButton)
    val cancelEditButton: Button = findViewById(R.id.cancelEditButton)
    val copyButton: Button = findViewById(R.id.copyButton)

    setupWebView(this, webview, findButton, filterLevelButton, editButton, cancelEditButton, copyButton)
  }
}