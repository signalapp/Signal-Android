package org.thoughtcrime.securesms.components.settings.app.internal.donor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.donations.StripeDeclineCode
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.UnexpectedSubscriptionCancellation
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.util.DynamicTheme

/**
 * Internal tool for configuring donor error states for testing.
 */
class InternalDonorErrorConfigurationFragment : ComposeFragment() {

  private val viewModel: InternalDonorErrorConfigurationViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SignalTheme(isDarkMode = DynamicTheme.isDarkTheme(LocalContext.current)) {
      InternalDonorErrorConfigurationScreen(
        state = state,
        callback = remember { DefaultInternalDonorErrorConfigurationCallback() }
      )
    }
  }

  /**
   * Default callback that bridges UI interactions to ViewModel updates.
   */
  inner class DefaultInternalDonorErrorConfigurationCallback : InternalDonorErrorConfigurationScreenCallback {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onSelectedBadgeChanged(badgeId: String) {
      val index = viewModel.state.value.badges.indexOfFirst { it.id == badgeId }
      if (index >= 0) {
        viewModel.setSelectedBadge(index)
      }
    }

    override fun onSelectedCancellationChanged(status: String) {
      val index = UnexpectedSubscriptionCancellation.entries.indexOfFirst { it.status == status }
      if (index >= 0) {
        viewModel.setSelectedUnexpectedSubscriptionCancellation(index)
      }
    }

    override fun onSelectedDeclineCodeChanged(code: String) {
      val index = StripeDeclineCode.Code.entries.indexOfFirst { it.code == code }
      if (index >= 0) {
        viewModel.setStripeDeclineCode(index)
      }
    }

    override fun onSaveClick() {
      viewModel.save().subscribe { requireActivity().finish() }
    }

    override fun onClearClick() {
      viewModel.clearErrorState().subscribe()
    }
  }
}

/**
 * Screen for configuring donor error states.
 */
@Composable
fun InternalDonorErrorConfigurationScreen(
  state: InternalDonorErrorConfigurationState,
  callback: InternalDonorErrorConfigurationScreenCallback
) {
  Scaffolds.Settings(
    title = "Donor Error Configuration",
    onNavigationClick = callback::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      val badgeLabels = state.badges.map { it.name }.toTypedArray()
      val badgeValues = state.badges.map { it.id }.toTypedArray()
      val selectedBadgeValue = state.selectedBadge?.id ?: ""

      Rows.RadioListRow(
        text = "Expired Badge",
        labels = badgeLabels,
        values = badgeValues,
        selectedValue = selectedBadgeValue,
        onSelected = callback::onSelectedBadgeChanged
      )

      val cancellationLabels = UnexpectedSubscriptionCancellation.entries.map { it.status }.toTypedArray()
      val cancellationValues = UnexpectedSubscriptionCancellation.entries.map { it.status }.toTypedArray()
      val selectedCancellationValue = state.selectedUnexpectedSubscriptionCancellation?.status ?: ""
      val cancellationEnabled = state.selectedBadge?.isSubscription() == true

      Rows.RadioListRow(
        text = "Cancellation Reason",
        labels = cancellationLabels,
        values = cancellationValues,
        selectedValue = selectedCancellationValue,
        onSelected = callback::onSelectedCancellationChanged,
        enabled = cancellationEnabled
      )

      val declineCodeLabels = StripeDeclineCode.Code.entries.map { it.code }.toTypedArray()
      val declineCodeValues = StripeDeclineCode.Code.entries.map { it.code }.toTypedArray()
      val selectedDeclineCodeValue = state.selectedStripeDeclineCode?.code ?: ""
      val declineCodeEnabled = state.selectedBadge?.isSubscription() == true

      Rows.RadioListRow(
        text = "Charge Failure",
        labels = declineCodeLabels,
        values = declineCodeValues,
        selectedValue = selectedDeclineCodeValue,
        onSelected = callback::onSelectedDeclineCodeChanged,
        enabled = declineCodeEnabled
      )

      Spacer(modifier = Modifier.height(16.dp))

      Buttons.LargeTonal(
        onClick = callback::onSaveClick,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
      ) {
        Text(text = "Save and Finish")
      }

      Spacer(modifier = Modifier.height(8.dp))

      TextButton(
        onClick = callback::onClearClick,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
      ) {
        Text(text = "Clear")
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

/**
 * Callback interface for [InternalDonorErrorConfigurationScreen] interactions.
 */
interface InternalDonorErrorConfigurationScreenCallback {
  fun onNavigationClick()
  fun onSelectedBadgeChanged(badgeId: String)
  fun onSelectedCancellationChanged(status: String)
  fun onSelectedDeclineCodeChanged(code: String)
  fun onSaveClick()
  fun onClearClick()

  object Empty : InternalDonorErrorConfigurationScreenCallback {
    override fun onNavigationClick() = Unit
    override fun onSelectedBadgeChanged(badgeId: String) = Unit
    override fun onSelectedCancellationChanged(status: String) = Unit
    override fun onSelectedDeclineCodeChanged(code: String) = Unit
    override fun onSaveClick() = Unit
    override fun onClearClick() = Unit
  }
}

@DayNightPreviews
@Composable
private fun InternalDonorErrorConfigurationScreenPreview() {
  Previews.Preview {
    InternalDonorErrorConfigurationScreen(
      state = InternalDonorErrorConfigurationState(
        badges = listOf(
          Badge(
            id = "test1",
            category = Badge.Category.Testing,
            name = "Test Badge 1",
            description = "Test description 1",
            imageUrl = android.net.Uri.EMPTY,
            imageDensity = "xxxhdpi",
            expirationTimestamp = 0L,
            visible = true,
            duration = 0L
          ),
          Badge(
            id = "test2",
            category = Badge.Category.Testing,
            name = "Test Badge 2",
            description = "Test description 2",
            imageUrl = android.net.Uri.EMPTY,
            imageDensity = "xxxhdpi",
            expirationTimestamp = 0L,
            visible = true,
            duration = 0L
          )
        ),
        selectedBadge = null,
        selectedUnexpectedSubscriptionCancellation = null,
        selectedStripeDeclineCode = null
      ),
      callback = InternalDonorErrorConfigurationScreenCallback.Empty
    )
  }
}
