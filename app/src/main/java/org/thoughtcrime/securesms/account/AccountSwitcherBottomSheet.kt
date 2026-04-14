package org.thoughtcrime.securesms.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.NameUtil

/**
 * Bottom sheet that displays the list of registered accounts and allows switching
 * between them or adding a new account.
 */
class AccountSwitcherBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.5f

  interface Callback {
    fun onAccountSelected(accountId: String)
    fun onAddAccountClicked()
    fun onSettingsClicked()
  }

  private val callback: Callback?
    get() = activity as? Callback

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      AccountSwitcherBottomSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  @Composable
  override fun SheetContent() {
    val application = requireContext().applicationContext as android.app.Application
    val registry = AccountRegistry.getInstance(application)
    val accounts = remember {
      AccountSwitcher.syncRegistryWithActiveAccount(application)
      registry.getAllAccounts()
    }
    val activeAccount = accounts.find { it.isActive }

    AccountSwitcherContent(
      accounts = accounts,
      activeAccountId = activeAccount?.accountId,
      onAccountClick = { accountId ->
        callback?.onAccountSelected(accountId)
        dismissAllowingStateLoss()
      },
      onAddAccountClick = {
        callback?.onAddAccountClicked()
        dismissAllowingStateLoss()
      },
      onSettingsClick = {
        callback?.onSettingsClicked()
        dismissAllowingStateLoss()
      }
    )
  }
}

@Composable
fun AccountSwitcherContent(
  accounts: List<AccountRegistry.AccountEntry>,
  activeAccountId: String?,
  onAccountClick: (String) -> Unit,
  onAddAccountClick: () -> Unit,
  onSettingsClick: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 24.dp)
  ) {
    BottomSheets.Handle(modifier = Modifier.align(Alignment.CenterHorizontally))

    Text(
      text = "Accounts",
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )

    for (account in accounts) {
      AccountRow(
        account = account,
        isActive = account.accountId == activeAccountId,
        onClick = { onAccountClick(account.accountId) }
      )
    }

    HorizontalDivider(
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      color = MaterialTheme.colorScheme.outlineVariant
    )

    // Add account row
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onAddAccountClick)
        .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer)
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.ic_plus_24),
          contentDescription = "Add account",
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(16.dp))

      Text(
        text = "Add account",
        style = MaterialTheme.typography.bodyLarge
      )
    }

    // Settings row
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onSettingsClick)
        .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.ic_settings_24),
          contentDescription = "Settings",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(16.dp))

      Text(
        text = "Settings",
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@Composable
private fun AccountRow(
  account: AccountRegistry.AccountEntry,
  isActive: Boolean,
  onClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 24.dp, vertical = 12.dp)
  ) {
    // Account avatar placeholder
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(
          if (isActive) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
      Text(
        text = getAccountInitial(account),
        style = MaterialTheme.typography.titleMedium,
        color = if (isActive) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.width(16.dp))

    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = account.displayName ?: account.e164 ?: account.accountId,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      if (account.e164 != null && account.displayName != null && account.displayName != account.e164) {
        Text(
          text = account.e164,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    if (isActive) {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_check_24),
        contentDescription = "Active account",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}

private fun getAccountInitial(account: AccountRegistry.AccountEntry): String {
  val name = account.displayName ?: account.e164 ?: account.accountId
  return NameUtil.getAbbreviation(name) ?: name.take(1).uppercase()
}
