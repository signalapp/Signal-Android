package org.thoughtcrime.securesms.components.settings

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme

/**
 * The DSL API can be completely replaced by compose.
 * See ComposeFragment or ComposeBottomSheetFragment for an alternative to this API"
 */
open class DSLSettingsActivity : PassphraseRequiredActivity() {

  protected open val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()

  protected lateinit var navController: NavController
    private set

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContentView(R.layout.dsl_settings_activity)

    if (savedInstanceState == null) {
      val navGraphId = intent.getIntExtra(ARG_NAV_GRAPH, -1)
      if (navGraphId == -1) {
        throw IllegalStateException("No navgraph id was passed to activity")
      }

      val fragment: NavHostFragment = NavHostFragment.create(navGraphId, intent.getBundleExtra(ARG_START_BUNDLE))

      supportFragmentManager.beginTransaction()
        .replace(R.id.nav_host_fragment, fragment)
        .commitNow()

      navController = fragment.navController
    } else {
      val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
      navController = fragment.navController
    }

    dynamicTheme.onCreate(this)

    onBackPressedDispatcher.addCallback(this, OnBackPressed())
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  override fun onNavigateUp(): Boolean {
    return if (!Navigation.findNavController(this, R.id.nav_host_fragment).popBackStack()) {
      onWillFinish()
      finish()
      true
    } else {
      false
    }
  }

  protected open fun onWillFinish() {}

  companion object {
    const val ARG_NAV_GRAPH = "nav_graph"
    const val ARG_START_BUNDLE = "start_bundle"
  }

  private inner class OnBackPressed : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
      onNavigateUp()
    }
  }
}
