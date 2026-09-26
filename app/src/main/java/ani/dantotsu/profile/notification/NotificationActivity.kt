package ani.dantotsu.profile.notification

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.dantotsu.R
import ani.dantotsu.databinding.ActivityNotificationBinding
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager

class NotificationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.notificationTitle.text = getString(R.string.notifications)
        binding.notificationToolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = navBarHeight
        }
        binding.notificationNavBar.isVisible = false
        binding.notificationBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.notificationViewPager.isUserInputEnabled = false
        binding.notificationViewPager.adapter =
            NotificationPagerAdapter(supportFragmentManager, lifecycle)
    }

    private class NotificationPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun getItemCount(): Int = 1
        override fun createFragment(position: Int): Fragment = NotificationFragment()
    }
}
