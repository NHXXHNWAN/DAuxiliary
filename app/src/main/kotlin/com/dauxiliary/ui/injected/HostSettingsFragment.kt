package com.dauxiliary.ui.injected

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.dauxiliary.core.registry.AppTarget
import com.dauxiliary.ui.theme.AppTheme

/** QQ-native back-stack page whose content is rendered by DAuxiliary. */
class HostSettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AppTheme {
                InjectedModuleSettings(AppTarget.QQ)
            }
        }
    }

    companion object {
        fun newInstance() = HostSettingsFragment()
    }
}