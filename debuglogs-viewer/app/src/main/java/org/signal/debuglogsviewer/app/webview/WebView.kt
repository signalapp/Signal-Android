/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.debuglogsviewer.app.webview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import org.signal.debuglogsviewer.app.R

fun setupWebView(
  context:           Context,
  webview:           WebView,
  findButton:        Button,
  filterLevelButton: Button,
  editButton:        Button,
  cancelEditButton:  Button,
  copyButton:        Button
) {
  val originalContent = org.json.JSONObject.quote(getLogText(context))
  var readOnly        = true

  webview.settings.apply {
    javaScriptEnabled = true
    builtInZoomControls = true
    displayZoomControls = false
  }

  webview.loadUrl("file:///android_asset/debuglogs-viewer.html")

  webview.webViewClient = object : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
      // Set the debug log lines
      webview.evaluateJavascript("editor.setValue($originalContent, -1); logLines = $originalContent;", null)

      // Set dark mode colors if in dark mode
      val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
      if (isDarkMode) {
        webview.evaluateJavascript("document.body.classList.add('dark');", null)
      }
    }
  }

  // Event listeners
  findButton.setOnClickListener {
    webview.evaluateJavascript("document.getElementById('searchBar').style.display = 'block';", null)
  }

  filterLevelButton.setOnClickListener {
    webview.evaluateJavascript("document.getElementById('filterLevel').style.display = 'block';", null)
  }

  editButton.setOnClickListener {
    readOnly = !readOnly
    cancelEditButton.visibility = if (!readOnly) View.VISIBLE else View.GONE
    editButton.text = if (readOnly) "Enable Edit" else "Save Edit"
    webview.evaluateJavascript("editor.setReadOnly($readOnly);", null)
  }

  cancelEditButton.setOnClickListener {
    readOnly = !readOnly
    cancelEditButton.visibility = View.GONE
    editButton.text = if (readOnly) "Enable Edit" else "Save Edit"
    webview.evaluateJavascript("editor.setReadOnly($readOnly);", null)
    webview.evaluateJavascript("editor.setValue($originalContent, -1);", null)
  }

  copyButton.setOnClickListener { // In Signal app, use Util.writeTextToClipboard(context, value) instead
    webview.evaluateJavascript ("editor.getValue();") { value ->
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clip = ClipData.newPlainText(context.getString(R.string.app_name), value)
      clipboard.setPrimaryClip(clip)
    }
  }
}

fun getLogText(context: Context): String {
  return try {
    context.assets.open("log.txt").bufferedReader().use { it.readText() }
  } catch (e: Exception) {
    "Error loading file: ${e.message}"
  }
}