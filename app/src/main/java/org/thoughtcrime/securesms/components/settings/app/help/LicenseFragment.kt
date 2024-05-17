/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.ui.Scaffolds
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import java.io.InputStream

class LicenseFragment : ComposeFragment() {
  private val TAG = Log.tag(LicenseFragment::class.java)

  @Composable
  override fun FragmentContent() {
    val textState: State<List<String>> = Single
      .fromCallable {
        requireContext().resources.openRawResource(R.raw.third_party_licenses).readToLines() +
          requireContext().assets.open("acknowledgments/libsignal.md").readToLines() +
          requireContext().assets.open("acknowledgments/ringrtc.md").readToLines()
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeAsState(initial = emptyList())

    Scaffolds.Settings(
      title = stringResource(id = R.string.HelpSettingsFragment__licenses),
      onNavigationClick = findNavController()::popBackStack,
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) {
      LicenseScreen(licenseTextLines = textState.value, modifier = Modifier.padding(it))
    }
  }
}

@Composable
fun LicenseScreen(licenseTextLines: List<String>, modifier: Modifier = Modifier) {
  Surface(modifier = modifier) {
    LazyColumn(modifier = Modifier.padding(horizontal = 4.dp)) {
      licenseTextLines.forEach { line ->
        item {
          Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
    }
  }
}

@Preview
@Composable
fun LicenseFragmentPreview() {
  LicenseScreen(listOf("Lorem ipsum", "Delor"))
}

private fun InputStream.readToLines(): List<String> {
  return this.bufferedReader().use { it.readText().split("\n") }
}
