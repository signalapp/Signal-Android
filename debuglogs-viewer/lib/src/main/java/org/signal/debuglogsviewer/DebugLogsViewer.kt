/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.debuglogsviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Runnable
import org.json.JSONObject

var readOnly = true

object DebugLogsViewer {
  @JvmStatic
  fun initWebView(webview: WebView, context: Context, onFinished: Runnable) {
    webview.settings.apply {
      javaScriptEnabled = true
      builtInZoomControls = true
      displayZoomControls = false
    }
    webview.isVerticalScrollBarEnabled = false
    webview.isHorizontalScrollBarEnabled = false

    webview.loadUrl("file:///android_asset/debuglogs-viewer.html")

    webview.webViewClient = object : WebViewClient() {
      override fun onPageFinished(view: WebView?, url: String?) {
        // Set dark mode colors if in dark mode
        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isDarkMode) {
          webview.evaluateJavascript("document.body.classList.add('dark');", null)
        }
        onFinished.run()
      }
    }
  }

  @JvmStatic
  fun presentLines(webview: WebView, lines: String) {
    // Set the debug log lines
    val escaped = JSONObject.quote(lines)
    webview.evaluateJavascript("editor.insert($escaped);", null)
  }

  @JvmStatic
  fun scrollToTop(webview: WebView) {
    webview.evaluateJavascript("editor.scrollToRow(0);", null)
  }

  @JvmStatic
  fun scrollToBottom(webview: WebView) {
    webview.evaluateJavascript("editor.scrollToRow(editor.session.getLength() - 1);", null)
  }

  @JvmStatic
  fun onFind(webview: WebView) {
    webview.evaluateJavascript("document.getElementById('searchBar').style.display = 'block';", null)
  }

  @JvmStatic
  fun onFilter(webview: WebView) {
    webview.evaluateJavascript("document.getElementById('filterLevel').style.display = 'block';", null)
  }

  @JvmStatic
  fun onEdit(webview: WebView) {
    readOnly = !readOnly
    webview.evaluateJavascript("editor.setReadOnly($readOnly);", null)
  }

  @JvmStatic
  fun onCancelEdit(webview: WebView, lines: String) {
    readOnly = !readOnly
    webview.evaluateJavascript("editor.setReadOnly($readOnly);", null)
    webview.evaluateJavascript("editor.setValue($lines, -1);", null)
  }

  @JvmStatic
  fun onCopy(webview: WebView, context: Context, appName: String) {
    webview.evaluateJavascript ("editor.getValue();") { value ->
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clip = ClipData.newPlainText(appName, value)
      clipboard.setPrimaryClip(clip)
    }
  }
}