package org.thoughtcrime.securesms.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import com.google.android.material.datepicker.MaterialDatePicker
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale
import kotlin.time.Duration.Companion.days

/**
 * A bottom sheet that allows you to aly additional filters to message search.
 */
class SearchFilterBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.66f

  companion object {
    const val REQUEST_KEY = "search_filter_result"
    const val RESULT_ACTION = "action"
    const val RESULT_START_DATE = "start_date"
    const val RESULT_END_DATE = "end_date"
    const val RESULT_AUTHOR_ID = "author_id"

    const val ACTION_APPLY = "apply"
    const val ACTION_CLEAR = "clear"
    const val ACTION_SELECT_AUTHOR = "select_author"

    private const val ARG_START_DATE = "arg_start_date"
    private const val ARG_END_DATE = "arg_end_date"
    private const val ARG_AUTHOR_ID = "arg_author_id"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, startDate: Long?, endDate: Long?, authorId: RecipientId?) {
      val fragment = SearchFilterBottomSheet().apply {
        arguments = bundleOf(
          ARG_START_DATE to (startDate ?: -1L),
          ARG_END_DATE to (endDate ?: -1L),
          ARG_AUTHOR_ID to authorId?.serialize()
        )
      }
      fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  @Composable
  override fun SheetContent() {
    val context = LocalContext.current
    val args = requireArguments()

    var startDate by remember {
      val v = args.getLong(ARG_START_DATE, -1L)
      mutableStateOf(if (v == -1L) null else v)
    }
    var endDate by remember {
      val v = args.getLong(ARG_END_DATE, -1L)
      mutableStateOf(if (v == -1L) null else v)
    }
    val authorIdRaw = remember { args.getString(ARG_AUTHOR_ID) }
    var authorId by remember { mutableStateOf(authorIdRaw?.let { RecipientId.from(it.toLong()) }) }

    val authorName = remember(authorId) {
      authorId?.let {
        Recipient.resolved(it).getDisplayName(context)
      }
    }

    val startDateText = remember(startDate) {
      startDate?.let { DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), it) }
        ?: context.getString(R.string.SearchFilterBottomSheet__not_set)
    }

    val endDateText = remember(endDate) {
      endDate?.let { DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), it) }
        ?: context.getString(R.string.SearchFilterBottomSheet__not_set)
    }

    SearchFilterSheetContent(
      startDateText = startDateText,
      endDateText = endDateText,
      authorName = authorName ?: stringResource(R.string.SearchFilterBottomSheet__anyone),
      onStartDateClick = {
        val datePicker = MaterialDatePicker.Builder.datePicker()
          .setTitleText(context.getString(R.string.SearchFilterBottomSheet__select_date))
          .apply {
            startDate?.let { setSelection(it) }
          }
          .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
          startDate = selection
        }
        datePicker.show(childFragmentManager, "START_DATE_PICKER")
      },
      onEndDateClick = {
        val datePicker = MaterialDatePicker.Builder.datePicker()
          .setTitleText(context.getString(R.string.SearchFilterBottomSheet__select_date))
          .apply {
            endDate?.let { setSelection(it) }
          }
          .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
          // Set to end of the selected day
          endDate = selection + 1.days.inWholeMilliseconds - 1
        }
        datePicker.show(childFragmentManager, "END_DATE_PICKER")
      },
      onAuthorClick = {
        setFragmentResult(
          REQUEST_KEY,
          bundleOf(
            RESULT_ACTION to ACTION_SELECT_AUTHOR,
            RESULT_START_DATE to (startDate ?: -1L),
            RESULT_END_DATE to (endDate ?: -1L),
            RESULT_AUTHOR_ID to authorId?.serialize()
          )
        )
        dismissAllowingStateLoss()
      },
      onApplyClick = {
        setFragmentResult(
          REQUEST_KEY,
          bundleOf(
            RESULT_ACTION to ACTION_APPLY,
            RESULT_START_DATE to (startDate ?: -1L),
            RESULT_END_DATE to (endDate ?: -1L),
            RESULT_AUTHOR_ID to authorId?.serialize()
          )
        )
        dismissAllowingStateLoss()
      },
      onClearClick = {
        setFragmentResult(
          REQUEST_KEY,
          bundleOf(
            RESULT_ACTION to ACTION_CLEAR
          )
        )
        dismissAllowingStateLoss()
      }
    )
  }
}

@Composable
private fun SearchFilterSheetContent(
  startDateText: String,
  endDateText: String,
  authorName: String,
  onStartDateClick: () -> Unit,
  onEndDateClick: () -> Unit,
  onAuthorClick: () -> Unit,
  onApplyClick: () -> Unit,
  onClearClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle()

    Text(
      text = stringResource(R.string.SearchFilterBottomSheet__filter_search),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(top = 18.dp, bottom = 24.dp)
    )

    FilterRow(
      label = stringResource(R.string.SearchFilterBottomSheet__start_date),
      value = startDateText,
      onClick = onStartDateClick
    )

    FilterRow(
      label = stringResource(R.string.SearchFilterBottomSheet__end_date),
      value = endDateText,
      onClick = onEndDateClick
    )

    FilterRow(
      label = stringResource(R.string.SearchFilterBottomSheet__author),
      value = authorName,
      onClick = onAuthorClick
    )

    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 24.dp)
    ) {
      Buttons.MediumTonal(
        onClick = onClearClick
      ) {
        Text(stringResource(R.string.SearchFilterBottomSheet__clear))
      }

      Buttons.MediumTonal(
        onClick = onApplyClick
      ) {
        Text(stringResource(R.string.SearchFilterBottomSheet__apply))
      }
    }
  }
}

@Composable
private fun FilterRow(
  label: String,
  value: String,
  onClick: () -> Unit
) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 24.dp, vertical = 16.dp)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.ic_expand_down_24),
        contentDescription = null,
        modifier = Modifier
          .padding(start = 8.dp)
          .size(24.dp)
      )
    }
  }
}
