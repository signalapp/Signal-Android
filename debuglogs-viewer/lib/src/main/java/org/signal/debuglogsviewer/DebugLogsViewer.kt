/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.debuglogsviewer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Runnable
import org.json.JSONArray
import org.json.JSONObject
import org.signal.core.util.ThreadUtil
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer

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

    webview.setBackgroundColor(Color.TRANSPARENT)
    webview.background = null

    webview.loadUrl("file:///android_asset/debuglogs-viewer.html")

    webview.webViewClient = object : WebViewClient() {
      override fun onPageFinished(view: WebView?, url: String?) {
        if (context.isDarkTheme) {
          webview.evaluateJavascript("document.body.classList.add('dark');", null)
        }
        onFinished.run()
      }
    }
  }

  @JvmStatic
  fun appendLines(webview: WebView, lines: String) {
    // Set the debug log lines
    val escaped = JSONObject.quote(lines)
    val latch = CountDownLatch(1)
    ThreadUtil.runOnMain {
      webview.evaluateJavascript("appendLines($escaped)") { latch.countDown() }
    }
    latch.await()
  }

  @JvmStatic
  fun readLogs(webview: WebView): LogReader {
    var position = 0
    return LogReader { size ->
      val latch = CountDownLatch(1)
      var result: String? = null
      ThreadUtil.runOnMain {
        webview.evaluateJavascript("readLines($position, $size)") { value ->
          // Annoying, string returns from javascript land are always encoded as JSON strings (wrapped in quotes, various strings escaped, etc)
          val parsed = JSONArray("[$value]").getString(0)
          result = parsed.takeUnless { it == "<<END OF INPUT>>" }
          position += size
          latch.countDown()
        }
      }

      latch.await()
      result
    }
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
  fun onSearchInput(webview: WebView, query: String) {
    webview.evaluateJavascript("onSearchInput('$query')", null)
  }

  @JvmStatic
  fun onSearch(webview: WebView) {
    webview.evaluateJavascript("onSearch()", null)
  }

  @JvmStatic
  fun onFilter(webview: WebView) {
    webview.evaluateJavascript("onFilter()", null)
  }

  @JvmStatic
  fun onFilterClose(webview: WebView) {
    webview.evaluateJavascript("onFilterClose()", null)
  }

  @JvmStatic
  fun onFilterLevel(webview: WebView, selectedLevels: String) {
    webview.evaluateJavascript("if (isFiltered) { onFilter(); }", null)
    webview.evaluateJavascript("onFilterLevel($selectedLevels)", null)
  }

  @JvmStatic
  fun onSearchUp(webview: WebView) {
    webview.evaluateJavascript("onSearchUp();", null)
  }

  @JvmStatic
  fun onSearchDown(webview: WebView) {
    webview.evaluateJavascript("onSearchDown();", null)
  }

  @JvmStatic
  fun getSearchPosition(webView: WebView, callback: Consumer<String?>) {
    webView.evaluateJavascript("getSearchPosition();", ValueCallback { value: String? -> callback.accept(value?.trim('"') ?: "") })
  }

  @JvmStatic
  fun onToggleCaseSensitive(webview: WebView) {
    webview.evaluateJavascript("onToggleCaseSensitive();", null)
  }

  @JvmStatic
  fun onSearchClose(webview: WebView) {
    webview.evaluateJavascript("onSearchClose();", null)
  }

  private val Context.isDarkTheme
    get() = (this.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

  fun interface LogReader {
    /** Returns the next bit of log, containing at most [size] lines (but may be less), or null if there are no logs remaining. */
    fun nextChunk(size: Int): String?
  }
}
