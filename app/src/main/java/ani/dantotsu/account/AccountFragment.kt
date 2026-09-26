package ani.dantotsu.account

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.connections.shinigami.ShinigamiBackendClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.databinding.ActivityAccountBinding
import ani.dantotsu.settings.SettingsAboutActivity
import ani.dantotsu.settings.SettingsActivity
import ani.dantotsu.settings.SettingsNotificationActivity
import ani.dantotsu.settings.UserInterfaceSettingsActivity
import ani.dantotsu.themes.ThemeManager
import kotlinx.coroutines.launch

class AccountFragment : Fragment(ani.dantotsu.R.layout.activity_account) {
    private var _binding: ActivityAccountBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ActivityAccountBinding.bind(view)
        ThemeManager(requireActivity()).applyTheme()
        binding.includedNavbar.navbarContainer.visibility = View.GONE
        loadProfile()
        binding.editProfileButton.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), EditProfileActivity::class.java))
        }
        binding.appearanceOption.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), UserInterfaceSettingsActivity::class.java))
        }
        binding.notificationsOption.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), SettingsNotificationActivity::class.java))
        }
        binding.settingsOption.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.aboutOption.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), SettingsAboutActivity::class.java))
        }
        binding.profileOption.setOnClickListener { loadProfile() }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) loadProfile()
    }

    private fun loadProfile() {
        val context = context ?: return
        lifecycleScope.launch {
            val token = ShinigamiSessionStore(context).getToken() ?: return@launch
            val session = runCatching {
                ShinigamiBackendClient().currentSession(token)
            }.getOrNull() ?: return@launch
            val profile = session.user
            binding.accountUsername.text = profile.displayName ?: profile.username
            binding.accountBio.text = profile.bio.orEmpty()
            profile.avatarUrl?.let { ani.dantotsu.loadImage(binding.accountAvatar, it) }
            profile.bannerUrl?.let { ani.dantotsu.loadImage(binding.accountBanner, it) }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
