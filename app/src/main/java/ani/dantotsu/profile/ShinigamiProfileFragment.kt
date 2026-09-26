package ani.dantotsu.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import ani.dantotsu.R
import ani.dantotsu.databinding.FragmentProfileBinding
import ani.dantotsu.openOrCopyAnilistLink
import ani.dantotsu.setBaseline
import ani.dantotsu.util.AniMarkdown.Companion.getFullAniHTML

class ShinigamiProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val activity: ProfileActivity get() = requireActivity() as ProfileActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val profile = activity.shinigamiProfile
        binding.root.setBaseline(activity.binding.profileNavBarContainer)

        binding.statsEpisodesWatched.text = profile.stats.episodesWatched.toString()
        binding.statsDaysWatched.text = "—"
        binding.statsAnimeMeanScore.text = profile.stats.meanScore?.toString() ?: "—"

        val bio = profile.user.bio.orEmpty()
        binding.userInfoContainer.isVisible = bio.isNotBlank()
        if (bio.isNotBlank()) {
            val styledHtml = getFullAniHTML(
                bio,
                ContextCompat.getColor(requireContext(), R.color.bg_opp)
            )
            binding.profileUserBio.settings.loadWithOverviewMode = true
            binding.profileUserBio.settings.useWideViewPort = true
            binding.profileUserBio.setInitialScale(1)
            binding.profileUserBio.loadDataWithBaseURL(null, styledHtml, "text/html; charset=utf-8", "UTF-8", null)
            binding.profileUserBio.setBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.transparent)
            )
            binding.profileUserBio.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    openOrCopyAnilistLink(request?.url.toString())
                    return true
                }
            }
        }

    }

    override fun onDestroyView() {
        _binding?.profileUserBio?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        _binding = null
        super.onDestroyView()
    }
}
