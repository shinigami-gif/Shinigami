package ani.dantotsu.account
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import ani.dantotsu.databinding.ActivityAccountBinding
import ani.dantotsu.settings.SettingsAboutActivity
import ani.dantotsu.settings.SettingsActivity
import ani.dantotsu.settings.SettingsNotificationActivity
import ani.dantotsu.settings.UserInterfaceSettingsActivity
import ani.dantotsu.themes.ThemeManager

class AccountFragment : Fragment(ani.dantotsu.R.layout.activity_account) {
    private var _binding: ActivityAccountBinding? = null
    private val binding get() = _binding!!
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ActivityAccountBinding.bind(view)
        ThemeManager(requireActivity()).applyTheme()
        binding.includedNavbar.navbarContainer.visibility = View.GONE
        loadProfile()
        binding.editProfileButton.setOnClickListener { startActivity(android.content.Intent(requireContext(), EditProfileActivity::class.java)) }
        binding.appearanceOption.setOnClickListener { startActivity(android.content.Intent(requireContext(), UserInterfaceSettingsActivity::class.java)) }
        binding.notificationsOption.setOnClickListener { startActivity(android.content.Intent(requireContext(), SettingsNotificationActivity::class.java)) }
        binding.settingsOption.setOnClickListener { startActivity(android.content.Intent(requireContext(), SettingsActivity::class.java)) }
        binding.aboutOption.setOnClickListener { startActivity(android.content.Intent(requireContext(), SettingsAboutActivity::class.java)) }
        binding.profileOption.setOnClickListener { loadProfile() }
    }
    override fun onResume() {
        super.onResume()
        if (_binding != null) loadProfile()
    }
    private fun loadProfile() {
        val profile = AccountRepository.getCurrentUser(requireContext())
        binding.accountUsername.text = profile.username
        binding.accountBio.text = profile.bio
        profile.avatarUri?.let { runCatching { binding.accountAvatar.setImageURI(Uri.parse(it)) } }
        profile.bannerUri?.let { runCatching { binding.accountBanner.setImageURI(Uri.parse(it)) } }
    }
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}