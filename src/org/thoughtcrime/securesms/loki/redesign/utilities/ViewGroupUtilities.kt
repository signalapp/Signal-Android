package org.thoughtcrime.securesms.loki.redesign.utilities

import android.view.ViewGroup

fun ViewGroup.disableClipping() {
    clipToPadding = false
    clipChildren = false
    clipToOutline = false
}