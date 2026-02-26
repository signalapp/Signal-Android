package org.thoughtcrime.securesms

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.components.settings.conversation.MuteUntilTimePickerBottomSheet
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object MuteDialog {

  private const val MUTE_UNTIL: Long = -1L

  private data class MuteOption(
    @DrawableRes val iconRes: Int,
    val title: String,
    val duration: Long
  )

  @JvmStatic
  fun show(context: Context, fragmentManager: FragmentManager, lifecycleOwner: LifecycleOwner, action: MuteSelectionListener) {
    fragmentManager.setFragmentResultListener(MuteUntilTimePickerBottomSheet.REQUEST_KEY, lifecycleOwner) { _, bundle ->
      action.onMuted(bundle.getLong(MuteUntilTimePickerBottomSheet.RESULT_TIMESTAMP))
    }

    val options = listOf(
      MuteOption(R.drawable.ic_daytime_24, context.getString(R.string.arrays__mute_for_one_hour), 1.hours.inWholeMilliseconds),
      MuteOption(R.drawable.ic_nighttime_26, context.getString(R.string.arrays__mute_for_eight_hours), 8.hours.inWholeMilliseconds),
      MuteOption(R.drawable.symbol_calendar_one, context.getString(R.string.arrays__mute_for_one_day), 1.days.inWholeMilliseconds),
      MuteOption(R.drawable.symbol_calendar_week, context.getString(R.string.arrays__mute_for_seven_days), 7.days.inWholeMilliseconds),
      MuteOption(R.drawable.symbol_calendar_24, context.getString(R.string.MuteDialog__mute_until), MUTE_UNTIL),
      MuteOption(R.drawable.symbol_bell_slash_24, context.getString(R.string.arrays__always), Long.MAX_VALUE)
    )

    val adapter = object : BaseAdapter() {
      override fun getCount(): Int = options.size
      override fun getItem(position: Int): MuteOption = options[position]
      override fun getItemId(position: Int): Long = position.toLong()

      override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.mute_dialog_item, parent, false)
        val option = options[position]
        view.findViewById<ImageView>(R.id.mute_dialog_icon).setImageResource(option.iconRes)
        view.findViewById<TextView>(R.id.mute_dialog_title).text = option.title
        return view
      }
    }

    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.MuteDialog_mute_notifications)
      .setAdapter(adapter) { _, which ->
        val option = options[which]
        when (option.duration) {
          MUTE_UNTIL -> MuteUntilTimePickerBottomSheet.show(fragmentManager)
          Long.MAX_VALUE -> action.onMuted(Long.MAX_VALUE)
          else -> action.onMuted(System.currentTimeMillis() + option.duration)
        }
      }
      .show()
  }

  fun interface MuteSelectionListener {
    fun onMuted(until: Long)
  }
}
