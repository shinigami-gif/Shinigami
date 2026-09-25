package ani.dantotsu.account

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import ani.dantotsu.R
import ani.dantotsu.MainActivity
import ani.dantotsu.media.CalendarActivity
import ani.dantotsu.media.user.ListActivity
import ani.dantotsu.profile.activity.FeedActivity
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

        binding.includedNavbar.navbar.visibility = View.VISIBLE
        binding.includedNavbar.navbar.selectTabAt(4)
        binding.includedNavbar.navbar.setOnTabSelectListener(
            object : nl.joery.animatedbottombar.AnimatedBottomBar.OnTabSelectListener {
                override fun onTabSelected(
                    lastIndex: Int,
                    lastTab: nl.joery.animatedbottombar.AnimatedBottomBar.Tab?,
                    newIndex: Int,
                    newTab: nl.joery.animatedbottombar.AnimatedBottomBar.Tab
                ) {
                    when (newIndex) {
                        0 -> {
                            startActivity(Intent(this@AccountActivity, MainActivity::class.java).putExtra("goToHome", true))
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            finish()
                        }
                        1 -> {
                            startActivity(Intent(this@AccountActivity, CalendarActivity::class.java))
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            finish()
                        }
                        2 -> {
                            startActivity(Intent(this@AccountActivity, FeedActivity::class.java))
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            finish()
                        }
                        3 -> {
                            startActivity(
                                Intent(this@AccountActivity, ListActivity::class.java)
                                    .putExtra("anime", true)
                                    .putExtra("userId", ani.dantotsu.connections.anilist.Anilist.userid)
                            )
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                            finish()
                        }
                        4 -> Unit
                    }
                }
            }
        )

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
