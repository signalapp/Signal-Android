package org.thoughtcrime.securesms.loki.utilities

import android.view.ViewGroup

fun ViewGroup.disableClipping() {
    clipToPadding = false
    clipChildren = false
    clipToOutline = false
}