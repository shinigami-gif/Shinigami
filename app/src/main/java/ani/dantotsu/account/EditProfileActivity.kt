package ani.dantotsu.account

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.loadImage
import ani.dantotsu.connections.shinigami.ShinigamiBackendClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import ani.dantotsu.databinding.ActivityEditProfileBinding
import ani.dantotsu.themes.ThemeManager
import kotlinx.coroutines.launch

class EditProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadProfile()

        binding.backButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { saveProfile() }
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            val token = ShinigamiSessionStore(this@EditProfileActivity).getToken() ?: return@launch
            val session = runCatching {
                ShinigamiBackendClient().currentSession(token)
            }.getOrNull() ?: return@launch
            binding.usernameInput.setText(session.user.username)
            binding.bioInput.setText(session.user.bio.orEmpty())
            session.user.avatarUrl?.let { binding.avatarPreview.loadImage(it) }
            session.user.bannerUrl?.let { binding.bannerPreview.loadImage(it) }
        }
    }

    private fun saveProfile() {
        lifecycleScope.launch {
            val token = ShinigamiSessionStore(this@EditProfileActivity).getToken() ?: return@launch
            val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
            if (username.isBlank()) return@launch

            val session = runCatching {
                ShinigamiBackendClient().updateProfile(
                    token = token,
                    username = username,
                    bio = binding.bioInput.text?.toString()?.trim()
                )
            }.getOrNull() ?: return@launch

            ShinigamiSessionStore(this@EditProfileActivity).save(session)
            finish()
        }
    }
}
