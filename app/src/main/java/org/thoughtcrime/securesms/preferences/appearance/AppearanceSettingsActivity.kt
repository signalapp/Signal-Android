package org.thoughtcrime.securesms.preferences.appearance

import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityAppearanceSettingsBinding
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_DARK
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN_DARK
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN_LIGHT
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.util.ThemeState

@AndroidEntryPoint
class AppearanceSettingsActivity: PassphraseRequiredActionBarActivity(), View.OnClickListener {

    companion object {
        private const val SCROLL_PARCEL = "scroll_parcel"
    }

    val viewModel: AppearanceSettingsViewModel by viewModels()
    lateinit var binding : ActivityAppearanceSettingsBinding

    var currentTheme: ThemeState? = null

    private val accentColors
        get() = mapOf(
            binding.accentGreen to R.style.PrimaryGreen,
            binding.accentBlue to R.style.PrimaryBlue,
            binding.accentYellow to R.style.PrimaryYellow,
            binding.accentPink to R.style.PrimaryPink,
            binding.accentPurple to R.style.PrimaryPurple,
            binding.accentOrange to R.style.PrimaryOrange,
            binding.accentRed to R.style.PrimaryRed
        )

    private val themeViews
        get() = listOf(
            binding.themeOptionClassicDark,
            binding.themeRadioClassicDark,
            binding.themeOptionClassicLight,
            binding.themeRadioClassicLight,
            binding.themeOptionOceanDark,
            binding.themeRadioOceanDark,
            binding.themeOptionOceanLight,
            binding.themeRadioOceanLight
        )

    override fun onClick(v: View?) {
        v ?: return
        val accents = accentColors
        val themes = themeViews
        if (v in accents) {
            val entry = accents[v]
            entry?.let { viewModel.setNewAccent(it) }
        } else if (v in themes) {
            val currentBase = if (currentTheme?.theme == R.style.Classic_Dark || currentTheme?.theme == R.style.Classic_Light) R.style.Classic else R.style.Ocean
            val (mappedStyle, newBase) = when (v) {
                binding.themeOptionClassicDark, binding.themeRadioClassicDark -> CLASSIC_DARK to R.style.Classic
                binding.themeOptionClassicLight, binding.themeRadioClassicLight -> CLASSIC_LIGHT to R.style.Classic
                binding.themeOptionOceanDark, binding.themeRadioOceanDark -> OCEAN_DARK to R.style.Ocean
                binding.themeOptionOceanLight, binding.themeRadioOceanLight -> OCEAN_LIGHT to R.style.Ocean
                else -> throw NullPointerException("Invalid style for view [$v]")
            }
            viewModel.setNewStyle(mappedStyle)
            if (currentBase != newBase) {
                if (newBase == R.style.Ocean) {
                    viewModel.setNewAccent(R.style.PrimaryBlue)
                } else if (newBase == R.style.Classic) {
                    viewModel.setNewAccent(R.style.PrimaryGreen)
                }
            }
        } else if (v == binding.systemSettingsSwitch) {
            viewModel.setNewFollowSystemSettings((v as SwitchCompat).isChecked)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val scrollParcelArray = SparseArray<Parcelable>()
        binding.scrollView.saveHierarchyState(scrollParcelArray)
        outState.putSparseParcelableArray(SCROLL_PARCEL, scrollParcelArray)
    }

    private fun updateSelectedTheme(themeStyle: Int) {
        mapOf(
            R.style.Classic_Dark to binding.themeRadioClassicDark,
            R.style.Classic_Light to binding.themeRadioClassicLight,
            R.style.Ocean_Dark to binding.themeRadioOceanDark,
            R.style.Ocean_Light to binding.themeRadioOceanLight
        ).forEach { (style, view) ->
            view.isChecked = themeStyle == style
        }
    }

    private fun updateSelectedAccent(accentStyle: Int) {
        accentColors.forEach { (view, style) ->
            view.isSelected = style == accentStyle
        }
    }

    private fun updateFollowSystemToggle(followSystemSettings: Boolean) {
        binding.systemSettingsSwitch.isChecked = followSystemSettings
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityAppearanceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        savedInstanceState?.let { bundle ->
            val scrollStateParcel = bundle.getSparseParcelableArray<Parcelable>(SCROLL_PARCEL)
            if (scrollStateParcel != null) {
                binding.scrollView.restoreHierarchyState(scrollStateParcel)
            }
        }
        supportActionBar!!.title = getString(R.string.activity_settings_message_appearance_button_title)
        with (binding) {
            // accent toggles
            accentContainer.children.forEach { view ->
                view.setOnClickListener(this@AppearanceSettingsActivity)
            }
            // theme toggles
            themeViews.forEach {
                it.setOnClickListener(this@AppearanceSettingsActivity)
            }
            // system settings toggle
            systemSettingsSwitch.setOnClickListener(this@AppearanceSettingsActivity)
        }

        lifecycleScope.launchWhenResumed {
            viewModel.uiState.collectLatest { themeState ->
                val (theme, accent, followSystem) = themeState
                updateSelectedTheme(theme)
                updateSelectedAccent(accent)
                updateFollowSystemToggle(followSystem)
                if (currentTheme != null && currentTheme != themeState) {
                    recreate()
                } else {
                    currentTheme = themeState
                }
            }
        }

    }
}