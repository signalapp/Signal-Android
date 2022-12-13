package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.view.drawToBitmap
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import org.signal.core.util.concurrent.SimpleTask
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.SignalProgressDialog
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.models.SplashImage
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import java.io.ByteArrayOutputStream
import java.util.Locale

class DonationReceiptDetailFragment : DSLSettingsFragment(layoutId = R.layout.donation_receipt_detail_fragment) {

  private lateinit var progressDialog: SignalProgressDialog

  private val viewModel: DonationReceiptDetailViewModel by viewModels(
    factoryProducer = {
      DonationReceiptDetailViewModel.Factory(
        DonationReceiptDetailFragmentArgs.fromBundle(requireArguments()).id,
        DonationReceiptDetailRepository()
      )
    }
  )

  override fun bindAdapter(adapter: MappingAdapter) {
    SplashImage.register(adapter)

    val sharePngButton: MaterialButton = requireView().findViewById(R.id.share_png)
    sharePngButton.isEnabled = false

    viewModel.state.observe(viewLifecycleOwner) { state ->
      if (state.donationReceiptRecord != null) {
        adapter.submitList(getConfiguration(state.donationReceiptRecord, state.subscriptionName).toMappingModelList())
      }

      if (state.donationReceiptRecord != null && state.subscriptionName != null) {
        sharePngButton.isEnabled = true
        sharePngButton.setOnClickListener {
          renderPng(state.donationReceiptRecord, state.subscriptionName)
        }
      }
    }
  }

  private fun renderPng(record: DonationReceiptRecord, subscriptionName: String) {
    progressDialog = SignalProgressDialog.show(requireContext())

    val today: String = DateUtils.formatDateWithDayOfWeek(Locale.getDefault(), System.currentTimeMillis())
    val amount: String = FiatMoneyUtil.format(resources, record.amount)
    val type: String = when (record.type) {
      DonationReceiptRecord.Type.RECURRING -> getString(R.string.DonationReceiptDetailsFragment__s_dash_s, subscriptionName, getString(R.string.DonationReceiptListFragment__recurring))
      DonationReceiptRecord.Type.BOOST -> getString(R.string.DonationReceiptListFragment__one_time)
      DonationReceiptRecord.Type.GIFT -> getString(R.string.DonationReceiptListFragment__gift)
    }
    val datePaid: String = DateUtils.formatDate(Locale.getDefault(), record.timestamp)

    SimpleTask.run(viewLifecycleOwner.lifecycle, {
      val outputStream = ByteArrayOutputStream()
      val view = LayoutInflater
        .from(requireContext())
        .inflate(R.layout.donation_receipt_png, null)

      view.findViewById<TextView>(R.id.date).text = today
      view.findViewById<TextView>(R.id.amount).text = amount
      view.findViewById<TextView>(R.id.donation_type).text = type
      view.findViewById<TextView>(R.id.date_paid).text = datePaid

      view.measure(View.MeasureSpec.makeMeasureSpec(DONATION_RECEIPT_WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
      view.layout(0, 0, view.measuredWidth, view.measuredHeight)

      val bitmap = view.drawToBitmap()
      bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream)

      BlobProvider.getInstance()
        .forData(outputStream.toByteArray())
        .withMimeType("image/png")
        .withFileName("Signal-Donation-Receipt.png")
        .createForSingleSessionInMemory()
    }, {
      progressDialog.dismiss()
      openShareSheet(it)
    })
  }

  private fun openShareSheet(uri: Uri) {
    val mimeType = Intent.normalizeMimeType("image/png")
    val shareIntent = ShareCompat.IntentBuilder(requireContext())
      .setStream(uri)
      .setType(mimeType)
      .createChooserIntent()
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    try {
      startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "No activity existed to share the media.", e)
      Toast.makeText(requireContext(), R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
    }
  }

  private fun getConfiguration(record: DonationReceiptRecord, subscriptionName: String?): DSLConfiguration {
    return configure {
      customPref(
        SplashImage.Model(
          splashImageResId = R.drawable.ic_signal_logo_type
        )
      )

      textPref(
        title = DSLSettingsText.from(
          charSequence = FiatMoneyUtil.format(resources, record.amount),
          DSLSettingsText.TextAppearanceModifier(R.style.Signal_Text_Giant),
          DSLSettingsText.CenterModifier
        )
      )

      dividerPref()

      textPref(
        title = DSLSettingsText.from(R.string.DonationReceiptDetailsFragment__donation_type),
        summary = DSLSettingsText.from(
          when (record.type) {
            DonationReceiptRecord.Type.RECURRING -> getString(R.string.DonationReceiptDetailsFragment__s_dash_s, subscriptionName, getString(R.string.DonationReceiptListFragment__recurring))
            DonationReceiptRecord.Type.BOOST -> getString(R.string.DonationReceiptListFragment__one_time)
            DonationReceiptRecord.Type.GIFT -> getString(R.string.DonationReceiptListFragment__gift)
          }
        )
      )

      textPref(
        title = DSLSettingsText.from(R.string.DonationReceiptDetailsFragment__date_paid),
        summary = record.let { DSLSettingsText.from(DateUtils.formatDateWithYear(Locale.getDefault(), it.timestamp)) }
      )
    }
  }

  companion object {
    private const val DONATION_RECEIPT_WIDTH = 1916

    private val TAG = Log.tag(DonationReceiptDetailFragment::class.java)
  }
}
