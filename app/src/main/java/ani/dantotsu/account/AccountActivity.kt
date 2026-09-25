package ani.dantotsu.account

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import ani.dantotsu.R
import ani.dantotsu.databinding.ActivityAccountBinding
import ani.dantotsu.settings.SettingsAboutActivity
import ani.dantotsu.settings.SettingsActivity
import ani.dantotsu.settings.SettingsNotificationActivity
import ani.dantotsu.settings.UserInterfaceSettingsActivity
import ani.dantotsu.themes.ThemeManager

class AccountActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAccountBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadProfile()

        binding.editProfileButton.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        binding.appearanceOption.setOnClickListener {
            startActivity(Intent(this, UserInterfaceSettingsActivity::class.java))
        }
        binding.notificationsOption.setOnClickListener {
            startActivity(Intent(this, SettingsNotificationActivity::class.java))
        }
        binding.settingsOption.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.aboutOption.setOnClickListener {
            startActivity(Intent(this, SettingsAboutActivity::class.java))
        }
        binding.profileOption.setOnClickListener {
            loadProfile()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) loadProfile()
    }

    private fun loadProfile() {
        val profile = AccountRepository.getCurrentUser(this)
        binding.accountUsername.text = profile.username
        binding.accountBio.text = profile.bio

        profile.avatarUri?.let {
            runCatching { binding.accountAvatar.setImageURI(Uri.parse(it)) }
        }
        profile.bannerUri?.let {
            runCatching { binding.accountBanner.setImageURI(Uri.parse(it)) }
        }
    }
}
