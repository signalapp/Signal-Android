package org.thoughtcrime.securesms.util

import android.view.ViewGroup

fun ViewGroup.disableClipping() {
    clipToPadding = false
    clipChildren = false
    clipToOutline = false
}