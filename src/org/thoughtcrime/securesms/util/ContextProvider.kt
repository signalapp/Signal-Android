package org.thoughtcrime.securesms.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment

/**
 * A simplified version of [android.content.ContextWrapper],
 * but properly supports [startActivityForResult] for the implementations.
 */
interface ContextProvider {
    fun getContext(): Context
    fun startActivityForResult(intent: Intent, requestCode: Int)
}

class ActivityContextProvider(private val activity: Activity): ContextProvider {
    
    override fun getContext(): Context {
        return activity
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        activity.startActivityForResult(intent, requestCode)
    }
}

class FragmentContextProvider(private val fragment: Fragment): ContextProvider {

    override fun getContext(): Context {
        return fragment.requireContext()
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        fragment.startActivityForResult(intent, requestCode)
    }
}