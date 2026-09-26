package ani.dantotsu.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.R
import ani.dantotsu.connections.shinigami.ShinigamiBackendClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.databinding.ActivitySettingsAccountsBinding
import ani.dantotsu.initActivity
import ani.dantotsu.loadImage
import ani.dantotsu.navBarHeight
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.startMainActivity
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import kotlinx.coroutines.launch

class SettingsAccountActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsAccountsBinding

    private val restartMainActivity = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = startMainActivity(this@SettingsAccountActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this

        binding = ActivitySettingsAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            settingsAccountsLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }

            accountSettingsBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }

            fun reload() {
                lifecycleScope.launch {
                    val token = ShinigamiSessionStore(context).getToken()
                    val session = token?.let {
                        runCatching { ShinigamiBackendClient().currentSession(it) }.getOrNull()
                    }
                    val user = session?.user

                    if (user != null) {
                        settingsAnilistLogin.setText(R.string.logout)
                        settingsAnilistLogin.setOnClickListener {
                            ShinigamiSessionStore(context).clear()
                            restartMainActivity.isEnabled = true
                            reload()
                        }

                        settingsAnilistUsername.visibility = View.VISIBLE
                        settingsAnilistUsername.text = user.displayName ?: user.username
                        settingsAnilistAvatar.setImageResource(R.drawable.ic_round_person_24)
                        user.avatarUrl?.let { settingsAnilistAvatar.loadImage(it) }

                        if (user.bannerUrl != null) {
                            settingsAnilistBanner.visibility = View.VISIBLE
                            settingsAnilistScrim.visibility = View.VISIBLE
                            settingsAnilistBanner.loadImage(user.bannerUrl)
                        } else {
                            settingsAnilistBanner.visibility = View.GONE
                            settingsAnilistScrim.visibility = View.GONE
                        }

                        settingsAnilistTokenExpiry.visibility = View.GONE
                        settingsRecyclerView.visibility = View.VISIBLE
                    } else {
                        settingsAnilistAvatar.setImageResource(R.drawable.ic_round_person_24)
                        settingsAnilistUsername.visibility = View.GONE
                        settingsAnilistTokenExpiry.visibility = View.GONE
                        settingsAnilistBanner.visibility = View.GONE
                        settingsAnilistScrim.visibility = View.GONE
                        settingsRecyclerView.visibility = View.GONE
                        settingsAnilistLogin.setText(R.string.login)
                        settingsAnilistLogin.setOnClickListener {
                            startMainActivity(context)
                        }
                    }

                }
            }

            reload()

            val highlightKey =
                intent.getStringExtra(ani.dantotsu.settings.search.SettingsSearchAdapter.EXTRA_HIGHLIGHT_KEY)

            settingsRecyclerView.adapter = SettingsAdapter(emptyList(), highlightKey = highlightKey)

            settingsRecyclerView.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }
    }

    fun reload() {
        // SettingsSearchAdapter may call this after changing an account preference.
    }
}
