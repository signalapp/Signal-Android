package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.signal.core.util.getParcelableArrayListExtraCompat
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FragmentWrapperActivity
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment.Companion.RESULT_SELECTION

open class MultiselectForwardActivity : FragmentWrapperActivity(), MultiselectForwardFragment.Callback, SearchConfigurationProvider {

  companion object {
    private val TAG = Log.tag(MultiselectForwardActivity::class.java)
    private const val ARGS = "args"
  }

  private val args: MultiselectForwardFragmentArgs get() = intent.getParcelableExtraCompat(ARGS, MultiselectForwardFragmentArgs::class.java)!!

  override val contentViewId: Int = R.layout.multiselect_forward_activity

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    val toolbar: Toolbar = findViewById(R.id.toolbar)
    toolbar.setTitle(args.title)
    toolbar.setNavigationOnClickListener { exitFlow() }
  }

  override fun getFragment(): Fragment {
    return MultiselectForwardFragment.create(args)
  }

  override fun onFinishForwardAction() {
    Log.d(TAG, "Completed forward action...")
  }

  override fun exitFlow() {
    Log.d(TAG, "Exiting flow...")
    ActivityCompat.finishAfterTransition(this)
  }

  override fun onSearchInputFocused() = Unit

  override fun setResult(bundle: Bundle) {
    setResult(RESULT_OK, Intent().putExtras(bundle))
  }

  @Suppress("WrongViewCast")
  override fun getContainer(): ViewGroup {
    return findViewById(R.id.fragment_container_wrapper)
  }

  override fun getDialogBackgroundColor(): Int {
    return ContextCompat.getColor(this, R.color.signal_colorBackground)
  }

  class SelectionContract : ActivityResultContract<MultiselectForwardFragmentArgs, List<ContactSearchKey.RecipientSearchKey>>() {
    override fun createIntent(context: Context, input: MultiselectForwardFragmentArgs): Intent {
      return Intent(context, MultiselectForwardActivity::class.java).putExtra(ARGS, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<ContactSearchKey.RecipientSearchKey> {
      return if (resultCode != RESULT_OK) {
        emptyList()
      } else if (intent == null || !intent.hasExtra(RESULT_SELECTION)) {
        throw IllegalStateException("Selection contract requires a selection.")
      } else {
        val selection: List<ContactSearchKey.RecipientSearchKey> = intent.getParcelableArrayListExtraCompat(RESULT_SELECTION, ContactSearchKey.RecipientSearchKey::class.java)!!
        selection
      }
    }
  }
}
