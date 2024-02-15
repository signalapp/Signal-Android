/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.findby

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.TextFields
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.invites.InviteActions
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberVisualTransformation
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.util.CountryPrefix
import org.thoughtcrime.securesms.util.viewModel
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter

/**
 * Allows the user to look up another Signal user by phone number or username and
 * retrieve a RecipientId for that data.
 */
class FindByActivity : PassphraseRequiredActivity() {

  companion object {
    private const val MODE = "FindByActivity.mode"
    private const val RECIPIENT_ID = "FindByActivity.recipientId"
  }

  private val viewModel: FindByViewModel by viewModel {
    FindByViewModel(FindByMode.valueOf(intent.getStringExtra(MODE)!!))
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContent {
      val state by viewModel.state

      val navController = rememberNavController()

      SignalTheme {
        NavHost(
          navController = navController,
          startDestination = "find-by-content",
          enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
          exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
          popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
          popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
        ) {
          composable("find-by-content") {
            val title = remember(state.mode) {
              if (state.mode == FindByMode.USERNAME) R.string.FindByActivity__find_by_username else R.string.FindByActivity__find_by_phone_number
            }

            Scaffolds.Settings(
              title = stringResource(id = title),
              onNavigationClick = { finishAfterTransition() },
              navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24)
            ) {
              val context = LocalContext.current
              Content(
                paddingValues = it,
                state = state,
                onUserEntryChanged = viewModel::onUserEntryChanged,
                onNextClick = {
                  lifecycleScope.launch {
                    when (val result = viewModel.onNextClicked(context)) {
                      is FindByResult.Success -> {
                        setResult(RESULT_OK, Intent().putExtra(RECIPIENT_ID, result.recipientId))
                        finishAfterTransition()
                      }

                      FindByResult.InvalidEntry -> navController.navigate("invalid-entry")
                      is FindByResult.NotFound -> navController.navigate("not-found/${result.recipientId.toLong()}")
                    }
                  }
                },
                onSelectCountryPrefixClick = {
                  navController.navigate("select-country-prefix")
                }
              )
            }
          }

          composable("select-country-prefix") {
            Scaffolds.Settings(
              title = stringResource(id = R.string.FindByActivity__select_country_code),
              onNavigationClick = { navController.popBackStack() },
              navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24)
            ) { paddingValues ->
              SelectCountryScreen(
                paddingValues = paddingValues,
                searchEntry = state.countryPrefixSearchEntry,
                onSearchEntryChanged = viewModel::onCountryPrefixSearchEntryChanged,
                supportedCountryPrefixes = state.supportedCountryPrefixes,
                onCountryPrefixSelected = {
                  navController.popBackStack()
                  viewModel.onCountryPrefixSelected(it)
                  viewModel.onCountryPrefixSearchEntryChanged("")
                }
              )
            }
          }

          dialog("invalid-entry") {
            val title = if (state.mode == FindByMode.USERNAME) {
              stringResource(id = R.string.FindByActivity__invalid_username)
            } else {
              stringResource(id = R.string.FindByActivity__invalid_phone_number)
            }

            val body = if (state.mode == FindByMode.USERNAME) {
              stringResource(id = R.string.FindByActivity__s_is_not_a_valid_username, state.userEntry)
            } else {
              val formattedNumber = remember(state.userEntry) {
                val cleansed = state.userEntry.removePrefix(state.selectedCountryPrefix.digits.toString())
                PhoneNumberFormatter.formatE164(state.selectedCountryPrefix.digits.toString(), cleansed)
              }
              stringResource(id = R.string.FindByActivity__s_is_not_a_valid_phone_number, formattedNumber)
            }

            Dialogs.SimpleAlertDialog(
              title = title,
              body = body,
              confirm = stringResource(id = android.R.string.ok),
              onConfirm = {},
              onDismiss = { navController.popBackStack() }
            )
          }

          dialog(
            route = "not-found/{recipientId}",
            arguments = listOf(navArgument("recipientId") { type = NavType.LongType })
          ) { navBackStackEntry ->
            val title = if (state.mode == FindByMode.USERNAME) {
              stringResource(id = R.string.FindByActivity__username_not_found)
            } else {
              stringResource(id = R.string.FindByActivity__invite_to_signal)
            }

            val body = if (state.mode == FindByMode.USERNAME) {
              stringResource(id = R.string.FindByActivity__s_is_not_a_signal_user, state.userEntry)
            } else {
              val formattedNumber = remember(state.userEntry) {
                val cleansed = state.userEntry.removePrefix(state.selectedCountryPrefix.digits.toString())
                PhoneNumberFormatter.formatE164(state.selectedCountryPrefix.digits.toString(), cleansed)
              }
              stringResource(id = R.string.FindByActivity__s_is_not_a_signal_user_would, formattedNumber)
            }

            val confirm = if (state.mode == FindByMode.USERNAME) {
              stringResource(id = android.R.string.ok)
            } else {
              stringResource(id = R.string.FindByActivity__invite)
            }

            val dismiss = if (state.mode == FindByMode.USERNAME) {
              Dialogs.NoDismiss
            } else {
              stringResource(id = android.R.string.cancel)
            }

            val context = LocalContext.current
            Dialogs.SimpleAlertDialog(
              title = title,
              body = body,
              confirm = confirm,
              dismiss = dismiss,
              onConfirm = {
                if (state.mode == FindByMode.PHONE_NUMBER) {
                  val recipientId = navBackStackEntry.arguments?.getLong("recipientId")?.takeIf { it > 0 }?.let { RecipientId.from(it) } ?: RecipientId.UNKNOWN
                  if (recipientId != RecipientId.UNKNOWN) {
                    InviteActions.inviteUserToSignal(
                      context,
                      Recipient.resolved(recipientId),
                      null,
                      this@FindByActivity::startActivity
                    )
                  }
                }
              },
              onDismiss = { navController.popBackStack() }
            )
          }
        }
      }
    }
  }

  class Contract : ActivityResultContract<FindByMode, RecipientId?>() {
    override fun createIntent(context: Context, input: FindByMode): Intent {
      return Intent(context, FindByActivity::class.java)
        .putExtra(MODE, input.name)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): RecipientId? {
      return intent?.getParcelableExtraCompat(RECIPIENT_ID, RecipientId::class.java)
    }
  }
}

@Composable
private fun Content(
  paddingValues: PaddingValues,
  state: FindByState,
  onUserEntryChanged: (String) -> Unit,
  onNextClick: () -> Unit,
  onSelectCountryPrefixClick: () -> Unit
) {
  val placeholderLabel = remember(state.mode) {
    if (state.mode == FindByMode.PHONE_NUMBER) R.string.FindByActivity__phone_number else R.string.FindByActivity__username
  }

  val focusRequester = remember {
    FocusRequester()
  }

  val keyboardType = remember(state.mode) {
    if (state.mode == FindByMode.PHONE_NUMBER) {
      KeyboardType.Phone
    } else {
      KeyboardType.Text
    }
  }

  Column(
    modifier = Modifier
      .padding(paddingValues)
      .fillMaxSize()
  ) {
    val onNextAction = remember(state.isLookupInProgress) {
      KeyboardActions(onNext = {
        if (!state.isLookupInProgress) {
          onNextClick()
        }
      })
    }

    val visualTransformation = if (state.mode == FindByMode.USERNAME) {
      VisualTransformation.None
    } else {
      remember(state.selectedCountryPrefix) {
        PhoneNumberVisualTransformation(state.selectedCountryPrefix.regionCode)
      }
    }

    TextFields.TextField(
      enabled = !state.isLookupInProgress,
      value = state.userEntry,
      onValueChange = onUserEntryChanged,
      singleLine = true,
      placeholder = { Text(text = stringResource(id = placeholderLabel)) },
      prefix = if (state.mode == FindByMode.USERNAME) {
        null
      } else {
        {
          PhoneNumberEntryPrefix(
            enabled = !state.isLookupInProgress,
            selectedCountryPrefix = state.selectedCountryPrefix,
            onSelectCountryPrefixClick = onSelectCountryPrefixClick
          )
        }
      },
      visualTransformation = visualTransformation,
      keyboardOptions = KeyboardOptions(
        keyboardType = keyboardType,
        imeAction = ImeAction.Next
      ),
      shape = RoundedCornerShape(32.dp),
      colors = TextFieldDefaults.colors(
        unfocusedIndicatorColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.onSurface
      ),
      keyboardActions = onNextAction,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .focusRequester(focusRequester)
        .heightIn(min = 44.dp),
      contentPadding = if (state.mode == FindByMode.PHONE_NUMBER) {
        TextFieldDefaults.contentPaddingWithoutLabel(start = 4.dp, top = 10.dp, bottom = 10.dp)
      } else {
        TextFieldDefaults.contentPaddingWithoutLabel(top = 10.dp, bottom = 10.dp)
      }
    )

    if (state.mode == FindByMode.USERNAME) {
      Text(
        text = stringResource(id = R.string.FindByActivity__enter_username_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
          .padding(top = 8.dp)
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    Box(
      contentAlignment = Alignment.BottomEnd,
      modifier = Modifier.fillMaxWidth()
    ) {
      Buttons.LargeTonal(
        enabled = !state.isLookupInProgress,
        onClick = onNextClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
          .padding(16.dp)
          .size(48.dp)
      ) {
        Icon(
          painter = painterResource(id = R.drawable.symbol_arrow_right_24),
          contentDescription = stringResource(id = R.string.FindByActivity__next)
        )
      }
    }

    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
    }
  }
}

@Composable
private fun PhoneNumberEntryPrefix(
  enabled: Boolean,
  selectedCountryPrefix: CountryPrefix,
  onSelectCountryPrefixClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(end = 16.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .clip(RoundedCornerShape(1000.dp))
        .clickable(onClick = onSelectCountryPrefixClick, enabled = enabled)
    ) {
      Text(
        text = selectedCountryPrefix.toString(),
        modifier = Modifier
          .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
      )
      Icon(
        painter = painterResource(id = R.drawable.symbol_dropdown_triangle_24),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .size(24.dp)
          .padding(end = 1.dp)
      )
    }
    Dividers.Vertical(
      thickness = 1.dp,
      color = MaterialTheme.colorScheme.outline,
      modifier = Modifier
        .padding(vertical = 2.dp)
        .padding(start = 7.dp)
        .height(20.dp)
    )
  }
}

@Composable
private fun SelectCountryScreen(
  paddingValues: PaddingValues,
  searchEntry: String,
  onSearchEntryChanged: (String) -> Unit,
  onCountryPrefixSelected: (CountryPrefix) -> Unit,
  supportedCountryPrefixes: List<CountryPrefix>
) {
  val focusRequester = remember {
    FocusRequester()
  }

  Column(
    modifier = Modifier.padding(paddingValues)
  ) {
    TextFields.TextField(
      value = searchEntry,
      onValueChange = onSearchEntryChanged,
      placeholder = { Text(text = stringResource(id = R.string.FindByActivity__search)) },
      shape = RoundedCornerShape(32.dp),
      colors = TextFieldDefaults.colors(
        unfocusedIndicatorColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.onSurface
      ),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .focusRequester(focusRequester)
        .heightIn(min = 44.dp),
      contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(top = 10.dp, bottom = 10.dp)
    )

    LazyColumn {
      items(
        items = supportedCountryPrefixes
      ) {
        CountryPrefixRowItem(
          searchTerm = searchEntry,
          countryPrefix = it,
          onClick = { onCountryPrefixSelected(it) }
        )
      }
    }
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}

@Composable
private fun CountryPrefixRowItem(
  searchTerm: String,
  countryPrefix: CountryPrefix,
  onClick: () -> Unit
) {
  val regionDisplayName = remember(countryPrefix.regionCode, Locale.current) {
    PhoneNumberFormatter.getRegionDisplayName(countryPrefix.regionCode).orElse(countryPrefix.regionCode)
  }

  if (searchTerm.isNotBlank() && !regionDisplayName.contains(searchTerm, ignoreCase = true)) {
    return
  }

  val highlightedName: AnnotatedString = remember(regionDisplayName, searchTerm) {
    if (searchTerm.isBlank()) {
      AnnotatedString(regionDisplayName)
    } else {
      buildAnnotatedString {
        append(regionDisplayName)

        val startIndex = regionDisplayName.indexOf(searchTerm, ignoreCase = true)

        addStyle(
          style = SpanStyle(
            fontWeight = FontWeight.Bold
          ),
          start = startIndex,
          end = startIndex + searchTerm.length
        )
      }
    }
  }

  Column(
    verticalArrangement = spacedBy((-2).dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
      .padding(top = 16.dp, bottom = 14.dp)
  ) {
    Text(
      text = highlightedName
    )

    Text(
      text = countryPrefix.toString(),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium
    )
  }
}

@Preview(name = "Light Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewPhoneNumber() {
  Previews.Preview {
    Content(
      paddingValues = PaddingValues(0.dp),
      state = FindByState(
        mode = FindByMode.PHONE_NUMBER,
        userEntry = ""
      ),
      onUserEntryChanged = {},
      onNextClick = {},
      onSelectCountryPrefixClick = {}
    )
  }
}

@Preview(name = "Light Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewUsername() {
  Previews.Preview {
    Content(
      paddingValues = PaddingValues(0.dp),
      state = FindByState(
        mode = FindByMode.USERNAME,
        userEntry = ""
      ),
      onUserEntryChanged = {},
      onNextClick = {},
      onSelectCountryPrefixClick = {}
    )
  }
}

@Preview(name = "Light Theme", group = "select country", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "select country", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SelectCountryScreenPreview() {
  Previews.Preview {
    SelectCountryScreen(
      paddingValues = PaddingValues(0.dp),
      searchEntry = "",
      onSearchEntryChanged = {},
      supportedCountryPrefixes = FindByState(mode = FindByMode.PHONE_NUMBER).supportedCountryPrefixes,
      onCountryPrefixSelected = {}
    )
  }
}
