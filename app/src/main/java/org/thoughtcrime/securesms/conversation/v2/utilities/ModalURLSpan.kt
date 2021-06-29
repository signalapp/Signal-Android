package org.thoughtcrime.securesms.conversation.v2.utilities

import android.text.style.URLSpan
import android.view.View

class ModalURLSpan(url: String, private val openModalCallback: (String)->Unit): URLSpan(url) {
    override fun onClick(widget: View) {
        openModalCallback(url)
    }
}