package org.thoughtcrime.securesms.stories.archive

import android.view.View
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.Glide
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.glide.decryptableuri.DecryptableUri
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.StoryTextPostModel
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.stories.settings.StorySettingsActivity
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.signal.core.ui.R as CoreUiR

@Composable
fun StoryArchiveScreen(
  onNavigationClick: () -> Unit,
  viewModel: StoryArchiveViewModel = viewModel()
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  LifecycleResumeEffect(Unit) {
    viewModel.refresh()
    onPauseOrDispose { }
  }

  BackHandler(enabled = state.multiSelectEnabled) {
    viewModel.clearSelection()
  }

  val sortMenuController = remember { DropdownMenus.MenuController() }

  Scaffolds.Settings(
    title = if (state.multiSelectEnabled) {
      pluralStringResource(R.plurals.StoryArchive__d_selected, state.selectedIds.size, state.selectedIds.size)
    } else {
      stringResource(R.string.StoryArchive__story_archive)
    },
    onNavigationClick = if (state.multiSelectEnabled) {
      { viewModel.clearSelection() }
    } else {
      onNavigationClick
    },
    navigationIcon = if (state.multiSelectEnabled) SignalIcons.X.imageVector else null,
    actions = {
      if (!state.multiSelectEnabled) {
        Box {
          IconButton(onClick = { sortMenuController.show() }) {
            Icon(
              imageVector = ImageVector.vectorResource(R.drawable.symbol_more_vertical),
              contentDescription = stringResource(R.string.StoryArchive__sort_by)
            )
          }

          DropdownMenus.Menu(
            controller = sortMenuController,
            offsetX = 0.dp
          ) {
            DropdownMenus.Item(
              text = { Text(text = stringResource(R.string.StoryArchive__newest)) },
              onClick = {
                viewModel.setSortOrder(SortOrder.NEWEST)
                sortMenuController.hide()
              }
            )
            DropdownMenus.Item(
              text = { Text(text = stringResource(R.string.StoryArchive__oldest)) },
              onClick = {
                viewModel.setSortOrder(SortOrder.OLDEST)
                sortMenuController.hide()
              }
            )
          }
        }
      }
    }
  ) { paddingValues ->
    StoryArchiveContent(
      state = state,
      pagingController = viewModel.pagingController,
      onToggleSelection = { messageId -> viewModel.toggleSelection(messageId) },
      onDeleteClick = { viewModel.requestDeleteSelected() },
      modifier = Modifier.padding(paddingValues)
    )

    if (state.showDeleteConfirmation) {
      Dialogs.SimpleAlertDialog(
        title = Dialogs.NoTitle,
        body = pluralStringResource(R.plurals.StoryArchive__delete_n_stories, state.selectedIds.size, state.selectedIds.size),
        confirm = stringResource(R.string.StoryArchive__delete),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { viewModel.confirmDeleteSelected() },
        onDeny = { viewModel.cancelDelete() },
        onDismiss = { viewModel.cancelDelete() }
      )
    }
  }
}

@Composable
private fun StoryArchiveContent(
  state: StoryArchiveState,
  pagingController: org.signal.paging.PagingController<Long>,
  onToggleSelection: (Long) -> Unit,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  when {
    state.isLoading -> {
      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator()
      }
    }

    state.stories.isEmpty() -> {
      val context = LocalContext.current

      Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = stringResource(R.string.StoryArchive__no_archived_stories),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center
        )
        Text(
          text = stringResource(R.string.StoryArchive__no_archived_stories_message),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
        )
        Buttons.Small(
          onClick = { context.startActivity(StorySettingsActivity.getIntent(context)) },
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
          ),
          shape = RoundedCornerShape(100),
          elevation = null,
          modifier = Modifier.padding(top = 16.dp)
        ) {
          Text(text = stringResource(R.string.StoryArchive__go_to_settings))
        }
      }
    }

    else -> {
      StoryArchiveGrid(
        stories = state.stories,
        multiSelectEnabled = state.multiSelectEnabled,
        selectedIds = state.selectedIds,
        pagingController = pagingController,
        onToggleSelection = onToggleSelection,
        onDeleteClick = onDeleteClick,
        modifier = modifier
      )
    }
  }
}

@Composable
private fun StoryArchiveGrid(
  stories: List<ArchivedStoryItem?>,
  multiSelectEnabled: Boolean,
  selectedIds: Set<Long>,
  pagingController: org.signal.paging.PagingController<Long>,
  onToggleSelection: (Long) -> Unit,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activity = LocalActivity.current
  val density = LocalDensity.current
  var bottomActionBarPadding by remember { mutableStateOf(0.dp) }

  Box(modifier = modifier.fillMaxSize()) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      contentPadding = PaddingValues(bottom = bottomActionBarPadding),
      modifier = Modifier.padding(horizontal = 2.dp)
    ) {
      items(
        count = stories.size,
        key = { index -> stories[index]?.messageId ?: -index.toLong() }
      ) { index ->
        LaunchedEffect(index) {
          pagingController.onDataNeededAroundIndex(index)
        }

        val item = stories[index]
        if (item == null) {
          StoryArchivePlaceholder()
        } else {
          val isFirstOfDate = computeDateIndicator(stories, index)

          StoryArchiveTile(
            item = item,
            showDateIndicator = isFirstOfDate,
            isSelected = selectedIds.contains(item.messageId),
            onClick = { view ->
              if (multiSelectEnabled) {
                onToggleSelection(item.messageId)
              } else {
                val textModel = if (item.storyType.isTextStory && item.body != null) {
                  StoryTextPostModel.parseFrom(
                    body = item.body,
                    storySentAtMillis = item.dateSent,
                    storyAuthor = Recipient.self().id,
                    bodyRanges = null
                  )
                } else {
                  null
                }

                val args = StoryViewerArgs(
                  recipientId = Recipient.self().id,
                  isInHiddenStoryMode = false,
                  storyId = item.messageId,
                  isFromArchive = true,
                  storyThumbTextModel = textModel,
                  storyThumbUri = if (textModel == null) item.thumbnailUri else null,
                  storyThumbBlur = if (textModel == null) item.blurHash else null
                )
                val intent = StoryViewerActivity.createIntent(context, args)
                if (view != null && activity != null) {
                  val options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, view, ViewCompat.getTransitionName(view) ?: "")
                  context.startActivity(intent, options.toBundle())
                } else {
                  context.startActivity(intent)
                }
              }
            },
            onLongClick = { onToggleSelection(item.messageId) }
          )
        }
      }
    }

    SignalBottomActionBar(
      visible = multiSelectEnabled,
      items = listOf(
        ActionItem(
          iconRes = CoreUiR.drawable.symbol_trash_24,
          title = stringResource(R.string.StoryArchive__delete),
          action = { onDeleteClick() }
        )
      ),
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .onGloballyPositioned { layoutCoordinates ->
          bottomActionBarPadding = with(density) { layoutCoordinates.size.height.toDp() }
        }
    )
  }
}

@Composable
private fun StoryArchivePlaceholder() {
  Box(
    modifier = Modifier
      .aspectRatio(9f / 16f)
      .padding(1.dp)
      .background(MaterialTheme.colorScheme.surfaceVariant)
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryArchiveTile(
  item: ArchivedStoryItem,
  showDateIndicator: Boolean,
  isSelected: Boolean,
  onClick: (View?) -> Unit,
  onLongClick: () -> Unit
) {
  var imageViewRef by remember { mutableStateOf<View?>(null) }
  val haptics = LocalHapticFeedback.current

  Box(
    modifier = Modifier
      .aspectRatio(9f / 16f)
      .padding(1.dp)
      .combinedClickable(
        onClick = { onClick(imageViewRef) },
        onLongClick = {
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          onLongClick()
        },
        onLongClickLabel = stringResource(R.string.StoryArchive__select_story)
      )
  ) {
    if (item.storyType.isTextStory && item.body != null) {
      val textModel = StoryTextPostModel.parseFrom(
        body = item.body,
        storySentAtMillis = item.dateSent,
        storyAuthor = Recipient.self().id,
        bodyRanges = null
      )
      AndroidView(
        factory = { context ->
          ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            ViewCompat.setTransitionName(this, "story")
          }.also { imageViewRef = it }
        },
        update = { iv ->
          Glide.with(iv).load(textModel).centerCrop().into(iv)
        },
        onReset = { Glide.with(it).clear(it) },
        modifier = Modifier.fillMaxSize()
      )
    } else if (item.thumbnailUri != null) {
      AndroidView(
        factory = { context ->
          ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            ViewCompat.setTransitionName(this, "story")
          }.also { imageViewRef = it }
        },
        update = { iv ->
          Glide.with(iv).load(DecryptableUri(item.thumbnailUri)).centerCrop().into(iv)
        },
        onReset = { Glide.with(it).clear(it) },
        modifier = Modifier.fillMaxSize()
      )
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.surfaceVariant)
      )
    }

    if (showDateIndicator) {
      val (day, month) = remember(item.dateSent) { formatDateIndicator(item.dateSent) }
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(4.dp)
          .defaultMinSize(40.dp)
          .background(
            color = Color.White.copy(alpha = 0.8f),
            shape = RoundedCornerShape(4.dp)
          )
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text(
          text = day,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = Color.Black,
          textAlign = TextAlign.Center
        )
        Text(
          text = month,
          fontSize = 10.sp,
          style = MaterialTheme.typography.bodySmall,
          color = Color.Black,
          textAlign = TextAlign.Center
        )
      }
    }

    if (isSelected) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = SignalIcons.CheckCircle.imageVector,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(32.dp)
        )
      }
    }
  }
}

private fun computeDateIndicator(stories: List<ArchivedStoryItem?>, index: Int): Boolean {
  val item = stories[index] ?: return false
  if (index == 0) return true
  val prev = stories[index - 1] ?: return true
  val cal = Calendar.getInstance()
  cal.timeInMillis = item.dateSent
  val year = cal.get(Calendar.YEAR)
  val day = cal.get(Calendar.DAY_OF_YEAR)
  cal.timeInMillis = prev.dateSent
  return year != cal.get(Calendar.YEAR) || day != cal.get(Calendar.DAY_OF_YEAR)
}

private fun formatDateIndicator(timestamp: Long): Pair<String, String> {
  val date = Date(timestamp)
  val day = SimpleDateFormat("d", Locale.getDefault()).format(date)
  val month = SimpleDateFormat("MMM", Locale.getDefault()).format(date)
  return day to month
}

@DayNightPreviews
@Composable
private fun StoryArchiveLoadingPreview() {
  Previews.Preview {
    StoryArchiveContent(
      state = StoryArchiveState(isLoading = true),
      pagingController = NoOpPagingController,
      onToggleSelection = {},
      onDeleteClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun StoryArchiveEmptyPreview() {
  Previews.Preview {
    StoryArchiveContent(
      state = StoryArchiveState(isLoading = false, stories = emptyList()),
      pagingController = NoOpPagingController,
      onToggleSelection = {},
      onDeleteClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun StoryArchiveTilePreview() {
  Previews.Preview {
    Box(modifier = Modifier.size(120.dp, 213.dp)) {
      StoryArchiveTile(
        item = ArchivedStoryItem(
          messageId = 1L,
          dateSent = System.currentTimeMillis(),
          thumbnailUri = null,
          blurHash = null,
          storyType = StoryType.NONE,
          body = null
        ),
        showDateIndicator = true,
        isSelected = false,
        onClick = { _ -> },
        onLongClick = {}
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun StoryArchiveSelectedTilePreview() {
  Previews.Preview {
    Box(modifier = Modifier.size(120.dp, 213.dp)) {
      StoryArchiveTile(
        item = ArchivedStoryItem(
          messageId = 1L,
          dateSent = System.currentTimeMillis(),
          thumbnailUri = null,
          blurHash = null,
          storyType = StoryType.NONE,
          body = null
        ),
        showDateIndicator = false,
        isSelected = true,
        onClick = { _ -> },
        onLongClick = {}
      )
    }
  }
}

private object NoOpPagingController : org.signal.paging.PagingController<Long> {
  override fun onDataNeededAroundIndex(aroundIndex: Int) = Unit
  override fun onDataInvalidated() = Unit
  override fun onDataItemChanged(key: Long) = Unit
  override fun onDataItemInserted(key: Long, position: Int) = Unit
}
