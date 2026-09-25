package ani.dantotsu.account

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import ani.dantotsu.databinding.ActivityEditProfileBinding
import ani.dantotsu.themes.ThemeManager

class EditProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditProfileBinding
    private var avatarUri: String? = null
    private var bannerUri: String? = null

    companion object {
        private const val PICK_AVATAR = 1001
        private const val PICK_BANNER = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val profile = AccountRepository.getCurrentUser(this)
        avatarUri = profile.avatarUri
        bannerUri = profile.bannerUri

        binding.usernameInput.setText(profile.username)
        binding.bioInput.setText(profile.bio)
        renderImages()

        binding.backButton.setOnClickListener { finish() }
        binding.changeAvatarButton.setOnClickListener { pickImage(PICK_AVATAR) }
        binding.changeBannerButton.setOnClickListener { pickImage(PICK_BANNER) }
        binding.saveButton.setOnClickListener { saveProfile() }
    }

    private fun pickImage(requestCode: Int) {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            },
            requestCode
        )
    }

    @Deprecated("Android activity result API is retained for the existing Dantotsu foundation.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        val uri = data?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        when (requestCode) {
            PICK_AVATAR -> avatarUri = uri.toString()
            PICK_BANNER -> bannerUri = uri.toString()
        }
        renderImages()
    }

    private fun renderImages() {
        avatarUri?.let { runCatching { binding.avatarPreview.setImageURI(Uri.parse(it)) } }
        bannerUri?.let { runCatching { binding.bannerPreview.setImageURI(Uri.parse(it)) } }
    }

    private fun saveProfile() {
        val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        val bio = binding.bioInput.text?.toString()?.trim().orEmpty()

        AccountRepository.updateProfile(
            context = this,
            username = username.ifBlank { "Shinigami User" },
            bio = bio,
            avatarUri = avatarUri,
            bannerUri = bannerUri
        )
        finish()
    }
}
