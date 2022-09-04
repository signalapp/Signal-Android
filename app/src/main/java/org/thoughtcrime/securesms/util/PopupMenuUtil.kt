package org.thoughtcrime.securesms.util

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.widget.PopupMenu

@SuppressLint("PrivateApi")
@Deprecated(message = "Not needed when using appcompat 1.4.1+", replaceWith = ReplaceWith("setForceShowIcon(true)"))
fun PopupMenu.forceShowIcon() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this.setForceShowIcon(true)
    } else {
        try {
            val popupField = PopupMenu::class.java.getDeclaredField("mPopup")
            popupField.isAccessible = true
            val menu = popupField.get(this)
            menu.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                .invoke(menu, true)
        } catch (exception: Exception) {
            Log.d("Loki", "Couldn't show message request popupmenu due to error: $exception.")
        }
    }
}
