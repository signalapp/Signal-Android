package org.thoughtcrime.securesms.util

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import java.util.function.Consumer

/**
 * Generic Fragment result contract.
 */
abstract class FragmentResultContract<T> protected constructor(private val resultKey: String) {

  protected abstract fun getResult(bundle: Bundle): T

  fun registerForResult(fragmentManager: FragmentManager, lifecycleOwner: LifecycleOwner, consumer: Consumer<T>) {
    fragmentManager.setFragmentResultListener(resultKey, lifecycleOwner) { key, bundle ->
      if (key == resultKey) {
        val result = getResult(bundle)
        consumer.accept(result)
      }
    }
  }
}
