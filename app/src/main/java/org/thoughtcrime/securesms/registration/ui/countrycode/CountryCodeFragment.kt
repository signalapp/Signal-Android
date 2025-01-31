@file:OptIn(ExperimentalMaterial3Api::class)

package org.thoughtcrime.securesms.registration.ui.countrycode

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel

/**
 * Country picker fragment used in registration V1
 */
class CountryCodeFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(CountryCodeFragment::class.java)
  }

  private val viewModel: CountryCodeViewModel by viewModels()
  private val sharedViewModel by activityViewModels<RegistrationViewModel>()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Screen(
      state = state,
      onSearch = { search -> viewModel.filterCountries(search) },
      onDismissed = { findNavController().popBackStack() },
      onClick = { country ->
        sharedViewModel.setCurrentCountryPicked(country)
        findNavController().popBackStack()
      }
    )
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel.loadCountries()
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(
  state: CountryCodeState,
  onSearch: (String) -> Unit = {},
  onDismissed: () -> Unit = {},
  onClick: (Country) -> Unit = {}
) {
  Scaffold(
    topBar = {
      Scaffolds.DefaultTopAppBar(
        title = stringResource(R.string.CountryCodeFragment__your_country),
        titleContent = { _, title ->
          Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        onNavigationClick = onDismissed,
        navigationIconPainter = rememberVectorPainter(ImageVector.vectorResource(R.drawable.symbol_x_24))
      )
    }
  ) { padding ->
    LazyColumn(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(padding)
    ) {
      item {
        SearchBar(
          text = state.query,
          onSearch = onSearch
        )
        Spacer(modifier = Modifier.size(18.dp))
      }

      if (state.countryList.isEmpty()) {
        item {
          CircularProgressIndicator(
            modifier = Modifier.size(56.dp)
          )
        }
      } else if (state.query.isEmpty()) {
        items(state.commonCountryList) { country ->
          CountryItem(country, onClick)
        }

        item {
          Dividers.Default()
        }

        items(state.countryList) { country ->
          CountryItem(country, onClick)
        }
      } else {
        items(state.filteredList) { country ->
          CountryItem(country, onClick, state.query)
        }
      }
    }
  }
}

@Composable
fun CountryItem(
  country: Country,
  onClick: (Country) -> Unit = {},
  query: String = ""
) {
  val emoji = country.emoji
  val name = country.name
  val code = country.countryCode
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .padding(horizontal = 24.dp)
      .fillMaxWidth()
      .defaultMinSize(minHeight = 56.dp)
      .clickable { onClick(country) }
  ) {
    Text(
      text = emoji,
      modifier = Modifier.size(24.dp)
    )

    if (query.isEmpty()) {
      Text(
        text = name.ifEmpty { stringResource(R.string.CountryCodeFragment__unknown_country) },
        modifier = Modifier
          .padding(start = 24.dp)
          .weight(1f),
        style = MaterialTheme.typography.bodyLarge
      )
      Text(
        text = code,
        modifier = Modifier.padding(start = 24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      val annotatedName = buildAnnotatedString {
        val startIndex = name.indexOf(query, ignoreCase = true)

        if (startIndex >= 0) {
          append(name.substring(0, startIndex))

          withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(name.substring(startIndex, startIndex + query.length))
          }

          append(name.substring(startIndex + query.length))
        } else {
          append(name)
        }
      }

      val annotatedCode = buildAnnotatedString {
        val startIndex = code.indexOf(query, ignoreCase = true)

        if (startIndex >= 0) {
          append(code.substring(0, startIndex))

          withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(code.substring(startIndex, startIndex + query.length))
          }

          append(code.substring(startIndex + query.length))
        } else {
          append(code)
        }
      }

      Text(
        text = annotatedName,
        modifier = Modifier
          .padding(start = 24.dp)
          .weight(1f),
        style = MaterialTheme.typography.bodyLarge
      )
      Text(
        text = annotatedCode,
        modifier = Modifier.padding(start = 24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun SearchBar(
  text: String,
  modifier: Modifier = Modifier,
  hint: String = stringResource(R.string.CountryCodeFragment__search_by),
  onSearch: (String) -> Unit = {}
) {
  TextField(
    value = text,
    onValueChange = { onSearch(it) },
    placeholder = { Text(hint) },
    // TODO(michelle): Add keyboard switch to dialpad
//    trailingIcon = {
//      Icon(
//        imageVector = ImageVector.vectorResource(R.drawable.symbol_number_pad_24),
//        contentDescription = "Search icon"
//      )
//    },
    shape = RoundedCornerShape(32.dp),
    modifier = modifier
      .fillMaxWidth()
      .height(54.dp)
      .padding(horizontal = 16.dp),
    visualTransformation = VisualTransformation.None,
    colors = TextFieldDefaults.colors(
      // TODO move to SignalTheme
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent
    )
  )
}

@SignalPreview
@Composable
private fun ScreenPreview() {
  Previews.Preview {
    Screen(
      state = CountryCodeState(
        countryList = mutableListOf(
          Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", "+1"),
          Country("\uD83C\uDDE8\uD83C\uDDE6", "Canada", "+2"),
          Country("\uD83C\uDDF2\uD83C\uDDFD", "Mexico", "+3")
        ),
        commonCountryList = mutableListOf(
          Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", "+4"),
          Country("\uD83C\uDDE8\uD83C\uDDE6", "Canada", "+5")
        )
      )
    )
  }
}

@SignalPreview
@Composable
private fun LoadingScreenPreview() {
  Previews.Preview {
    Screen(
      state = CountryCodeState(
        countryList = emptyList()
      )
    )
  }
}
