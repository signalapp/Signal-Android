package org.signal.registration.screens.localbackuprestore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.test.TestTags

/**
 * Scaffold used for each stage in [LocalBackupRestoreScreen]
 * */
@Composable
internal fun LocalBackupRestoreLayout(
  modifier: Modifier = Modifier,
  description: (@Composable ColumnScope.() -> Unit)? = null,
  primaryButton: (@Composable (Modifier) -> Unit)? = null,
  secondaryButton: (@Composable (Modifier) -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  when (val params = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> {
      val scrollState = rememberScrollState()

      OnePaneRegistrationScaffold(
        modifier = modifier.fillMaxSize(),
        params = params,
        content = { paddingValues ->
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(scrollState)
              .padding(paddingValues)
              .testTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            description?.invoke(this)
            Spacer(modifier = Modifier.height(24.dp))
            content()
          }
        },
        footer = {
          RegistrationScaffold.FooterSurface(
            isElevated = scrollState.canScrollForward
          ) {
            if (primaryButton != null || secondaryButton != null) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(params.footerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                primaryButton?.invoke(Modifier.fillMaxWidth())
                if (secondaryButton != null) {
                  Spacer(modifier = Modifier.height(8.dp))
                }
                secondaryButton?.invoke(Modifier.fillMaxWidth())
              }
            }
          }
        }
      )
    }

    is RegistrationScaffold.Params.TwoPane -> {
      val firstPaneScrollState = rememberScrollState()
      val secondPaneScrollState = rememberScrollState()

      TwoPaneRegistrationScaffold(
        modifier = modifier
          .fillMaxSize()
          .testTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN),
        params = params,
        firstPane = { paddingValues ->
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .verticalScroll(firstPaneScrollState)
              .padding(paddingValues)
          ) {
            description?.invoke(this)
          }
        },
        secondPane = { paddingValues ->
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .verticalScroll(secondPaneScrollState)
              .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            content()
          }
        },
        footer = {
          RegistrationScaffold.FooterSurface(
            isElevated = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward
          ) {
            if (primaryButton != null || secondaryButton != null) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(params.footerPadding),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
              ) {
                secondaryButton?.invoke(Modifier)
                if (secondaryButton != null) {
                  Spacer(modifier = Modifier.width(16.dp))
                }
                primaryButton?.invoke(Modifier)
              }
            }
          }
        }
      )
    }
  }
}

@Composable
internal fun Description(headline: String, body: String) {
  Text(
    text = headline,
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = body,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp)
  )
}

@Composable
internal fun CancelButton(
  onEvent: (LocalBackupRestoreEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  TextButton(
    onClick = { onEvent(LocalBackupRestoreEvents.Cancel) },
    modifier = modifier
  ) {
    Text(text = stringResource(android.R.string.cancel))
  }
}

@Composable
internal fun Loading(
  label: String,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp)
      .testTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(48.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge
    )
  }
}
