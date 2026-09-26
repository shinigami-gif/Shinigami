package ani.dantotsu.media.anime

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
//import androidx.compose.ui.test.performClick
//import androidx.compose.ui.geometry.isEmpty
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
//import androidx.glance.visibility
//import androidx.glance.visibility
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withCreated
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.R
import ani.dantotsu.connections.crashlytics.CrashlyticsInterface
import ani.dantotsu.databinding.BottomSheetSelectorBinding
import ani.dantotsu.databinding.ItemStreamBinding
import ani.dantotsu.databinding.ItemUrlBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.hideSystemBarsExtendView
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaDetailsViewModel
import ani.dantotsu.navBarHeight
import ani.dantotsu.parsers.Subtitle
import ani.dantotsu.parsers.Video
import ani.dantotsu.parsers.VideoExtractor
import ani.dantotsu.parsers.VideoType
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.toast
import ani.dantotsu.tryWith
import ani.dantotsu.util.Logger
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DecimalFormat


class SelectorDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSelectorBinding? = null
    private val binding get() = _binding!!
    val model: MediaDetailsViewModel by activityViewModels()
    private var scope: CoroutineScope = lifecycleScope
    private var media: Media? = null
    private var episode: Episode? = null
    private var prevEpisode: String? = null
    private var makeDefault = false
    private var selected: String? = null
    private var launch: Boolean? = null
    private var episodes: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selected = it.getString("server")
            launch = it.getBoolean("launch", true)
            prevEpisode = it.getString("prev")
            episodes = it.getStringArrayList("episodes")
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSelectorBinding.inflate(inflater, container, false)
        val window = dialog?.window
        window?.statusBarColor = Color.TRANSPARENT
        window?.navigationBarColor =
            requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var loaded = false
        model.getMedia().observe(viewLifecycleOwner) { m ->
            media = m
            if (media != null && !loaded) {
                loaded = true

                fun fail(resId: Int){
                    ContextCompat.getMainExecutor(context ?: currContext() ?: return).execute {
                        snackString(getString(resId))
                        tryWith {
                            dismissAllowingStateLoss()
                        }
                    }
                }

                fun initializeVideoServerSelector(ep: Episode) {
                    binding.selectorRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = navBarHeight
                    }
                    binding.selectorRecyclerView.adapter = null
                    binding.selectorProgressBar.visibility = View.VISIBLE
                    makeDefault = PrefManager.getVal(PrefName.MakeDefault)
                    binding.selectorMakeDefault.isChecked = makeDefault
                    binding.selectorMakeDefault.setOnClickListener {
                        makeDefault = binding.selectorMakeDefault.isChecked
                        PrefManager.setVal(PrefName.MakeDefault, makeDefault)
                    }
                    binding.selectorRecyclerView.layoutManager =
                        LinearLayoutManager(
                            requireActivity(),
                            LinearLayoutManager.VERTICAL,
                            false
                        )
                    val adapter = ExtractorAdapter()
                    binding.selectorRecyclerView.adapter = adapter
                    if (!ep.allStreams) {
                        ep.extractorCallback = { extractor ->
                            scope.launch(Dispatchers.Main) {
                                if (_binding == null || !isAdded) return@launch
                                adapter.add(extractor)
                                binding.selectorProgressBar.visibility = View.GONE
                            }
                        }
                        scope.launch(Dispatchers.IO) {
                            model.loadEpisodeVideos(ep, media!!.selected!!.sourceIndex)
                            withContext(Dispatchers.Main) {
                                if (_binding == null || !isAdded) return@withContext
                                binding.selectorProgressBar.visibility = View.GONE
                                if (adapter.itemCount == 0) {
                                    fail(R.string.stream_selection_empty)
                                }
                            }
                        }
                    } else {
                        val epKey = media?.anime?.episodes?.getEpisodeKey(ep.number) ?: media?.anime?.selectedEpisode
                        if (epKey != null) {
                            media!!.anime?.episodes?.set(epKey, ep)
                        }
                        adapter.addAll(ep.extractors)
                        if (ep.extractors?.size == 0) {
                            fail(R.string.stream_selection_empty)
                        }
                        binding.selectorProgressBar.visibility = View.GONE
                    }
                }

                fun autoSelectServerAndPlay(ep: Episode) {
                    binding.selectorListContainer.visibility = View.GONE
                    binding.selectorAutoListContainer.visibility = View.VISIBLE
                    binding.selectorAutoText.text = getString(R.string.auto_select_server)
                    var isCancelled = false
                    binding.selectorCancel.setOnClickListener {
                        isCancelled = true
                        binding.selectorAutoListContainer.visibility = View.GONE
                        binding.selectorListContainer.visibility = View.VISIBLE
                        initializeVideoServerSelector(ep)
                    }

                    fun selectAndStart(chosenExtractor: VideoExtractor): Boolean {
                        if (chosenExtractor.videos.isEmpty()) return false
                        ep.selectedExtractor = chosenExtractor.server.name
                        ep.selectedVideo = 0
                        val currentKey = media!!.anime!!.selectedEpisode ?: ep.number
                        media!!.anime!!.episodes?.getEpisode(currentKey)?.selectedExtractor = ep.selectedExtractor
                        media!!.anime!!.episodes?.getEpisode(currentKey)?.selectedVideo = ep.selectedVideo
                        startExoplayer(media!!)
                        return true
                    }

                    if (ep.allStreams) {
                        val extractors = ep.extractors ?: emptyList()
                        val validExtractor = extractors.firstOrNull { it.videos.isNotEmpty() }
                        if (validExtractor != null && selectAndStart(validExtractor)) {
                            return
                        } else {
                            binding.selectorAutoListContainer.visibility = View.GONE
                            binding.selectorListContainer.visibility = View.VISIBLE
                            initializeVideoServerSelector(ep)
                            return
                        }
                    }

                    var hasStarted = false
                    ep.extractorCallback = { extractor ->
                        scope.launch(Dispatchers.Main) {
                            if (_binding == null || !isAdded || isCancelled || hasStarted) return@launch
                            if (extractor.videos.isNotEmpty()) {
                                hasStarted = true
                                selectAndStart(extractor)
                            }
                        }
                    }
                    scope.launch(Dispatchers.IO) {
                        model.loadEpisodeVideos(ep, media!!.selected!!.sourceIndex)
                        withContext(Dispatchers.Main) {
                            if (_binding == null || !isAdded || isCancelled) return@withContext
                            if (!hasStarted) {
                                val valid = ep.extractors?.firstOrNull { it.videos.isNotEmpty() }
                                if (valid != null) {
                                    hasStarted = true
                                    selectAndStart(valid)
                                } else {
                                    binding.selectorAutoListContainer.visibility = View.GONE
                                    binding.selectorListContainer.visibility = View.VISIBLE
                                    initializeVideoServerSelector(ep)
                                }
                            }
                        }
                    }
                }

                suspend fun loadEpisodeSingleServer(episodeName: String, selectedServerName: String): Boolean{
                    val ep = media?.anime?.episodes?.getEpisode(episodeName) ?: media?.anime?.episodes?.getEpisode(media?.anime?.selectedEpisode)
                    if (ep == null) return false
                    episode = ep

                    var success = false
                    scope.launch(Dispatchers.IO) {
                        success = model.loadEpisodeSingleVideo(
                            ep,
                            media!!.selected!!,
                            selectedServerName = selectedServerName
                        )
                    }.join()
                    Log.d("StreamSelector", "Loading Episode Server State: $success")
                    return success
                }
                Log.d("StreamSelector", "Selected Server for watching: $selected")
                if(episodes.isNullOrEmpty()){
                    fail(R.string.empty_episodes_list)
                }
                if (true) {
                    val rawKey = episodes?.get(0)
                    val ep = media?.anime?.episodes?.getEpisode(rawKey)
                    val actualKey = media?.anime?.episodes?.getEpisodeKey(rawKey) ?: rawKey
                    media?.anime?.selectedEpisode = actualKey
                    episode = ep
                    if (ep != null) {
                        if (selected != null && media?.format != "LOCAL") {
                            binding.selectorListContainer.visibility = View.GONE
                            binding.selectorAutoListContainer.visibility = View.VISIBLE
                            binding.selectorAutoText.text = selected
                            binding.selectorCancel.setOnClickListener {
                                media!!.selected!!.server = null
                                model.saveSelected(media!!.id, media!!.selected!!)
                                tryWith {
                                    dismissAllowingStateLoss()
                                }
                            }

                            fun failToList() {
                                snackString(getString(R.string.auto_select_server_error))
                                media!!.selected!!.server = null
                                model.saveSelected(media!!.id, media!!.selected!!)
                                binding.selectorAutoListContainer.visibility = View.GONE
                                binding.selectorListContainer.visibility = View.VISIBLE
                                initializeVideoServerSelector(ep)
                            }

                            fun load() {
                                val size =                    ep.extractors?.find { it.server.name == selected }?.videos?.size

                                if (size != null && size >= media!!.selected!!.video) {
                                    val currentKey = media!!.anime!!.selectedEpisode ?: actualKey
                                    media!!.anime!!.episodes?.getEpisode(currentKey)?.selectedExtractor = selected
                                    media!!.anime!!.episodes?.getEpisode(currentKey)?.selectedVideo = media!!.selected!!.video
                                    startExoplayer(media!!)
                                } else failToList()
                            }

                            if (ep.extractors?.filter { it.server.name == selected } == null) {
                                scope.launch{
                                    val success = withContext(Dispatchers.IO){
                                        loadEpisodeSingleServer(ep.number, selected!!)
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (_binding == null || !isAdded) return@withContext
                                        if (!success) {
                                            failToList()
                                        } else {
                                            load()
                                        }
                                    }
                                }
                            } else load()
                        }
                        else if (PrefManager.getVal<Boolean>(PrefName.AutoSelectServer) && media?.format != "LOCAL" && true) {
                            autoSelectServerAndPlay(ep)
                        }
                        else
                            initializeVideoServerSelector(ep)
                    }
                }
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    fun startExoplayer(media: Media) {
        if (!isAdded || _binding == null) return
        prevEpisode = null
        dismissAllowingStateLoss()
        if (launch == true) {
            stopAddingToList()
            val intent = Intent(activity, ExoplayerView::class.java)
            ExoplayerView.media = media
            ExoplayerView.initialized = true
            startActivity(intent)
        } else {
            val epKey = media.anime?.selectedEpisode
            val targetEp = media.anime?.episodes?.getEpisode(epKey) ?: episode
            if (targetEp != null) {
                model.setEpisode(targetEp, "startExo no launch")
            }
        }
    }

    private fun stopAddingToList() {
        episode?.extractorCallback = null
        episode?.also {
            it.extractors = it.extractors?.toMutableList()
        }
    }

    private inner class ExtractorAdapter :
        RecyclerView.Adapter<ExtractorAdapter.StreamViewHolder>() {
        val links = mutableListOf<VideoExtractor>()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder =
            StreamViewHolder(
                ItemStreamBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
            val extractor = links.getOrNull(position) ?: return
            holder.binding.streamName.text = ""//extractor.server.name
            holder.binding.streamName.visibility = View.GONE

            holder.binding.streamRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            holder.binding.streamRecyclerView.adapter = VideoAdapter(extractor)
        }

        override fun getItemCount(): Int = links.size

        fun add(videoExtractor: VideoExtractor) {
            if (videoExtractor.videos.isNotEmpty()) {
                val existingIndex = links.indexOfFirst { it.server.name == videoExtractor.server.name }
                if (existingIndex >= 0) {
                    links[existingIndex] = videoExtractor
                    notifyItemChanged(existingIndex)
                } else {
                    links.add(videoExtractor)
                    notifyItemInserted(links.size - 1)
                }
            }
        }

        fun addAll(extractors: List<VideoExtractor>?) {
            links.addAll(extractors ?: return)
            notifyItemRangeInserted(0, extractors.size)
        }

        fun performClick(position: Int) {
            try {
                val extractor = links[position]
                val currentEp = media?.anime?.episodes?.getEpisode(media?.anime?.selectedEpisode) ?: episode
                val epKey = media?.anime?.episodes?.getEpisodeKey(media?.anime?.selectedEpisode) ?: media?.anime?.selectedEpisode
                if (currentEp != null) {
                    currentEp.selectedExtractor = extractor.server.name
                    currentEp.selectedVideo = 0
                }
                if (epKey != null) {
                    media?.anime?.episodes?.get(epKey)?.selectedExtractor = extractor.server.name
                    media?.anime?.episodes?.get(epKey)?.selectedVideo = 0
                }
                startExoplayer(media!!)
            } catch (e: Exception) {
                Injekt.get<CrashlyticsInterface>().logException(e)
            }
        }

        private inner class StreamViewHolder(val binding: ItemStreamBinding) :
            RecyclerView.ViewHolder(binding.root)
    }

    private inner class VideoAdapter(private val extractor: VideoExtractor) :
        RecyclerView.Adapter<VideoAdapter.UrlViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UrlViewHolder {
            return UrlViewHolder(
                ItemUrlBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: UrlViewHolder, position: Int) {
            val binding = holder.binding
            val video = extractor.videos[position]
            val subtitles = extractor.subtitles
            if (subtitles.isNotEmpty()) {
                binding.urlSub.visibility = View.VISIBLE
            } else {
                binding.urlSub.visibility = View.GONE
            }
            binding.urlSub.visibility = View.GONE
            if (video.format == VideoType.CONTAINER) {
                binding.urlSize.isVisible = video.size != null
                // if video size is null or 0, show "Unknown Size" else show the size in MB
                val sizeText = getString(
                    R.string.mb_size, "${if (video.extraNote != null) " : " else ""}${
                        if (video.size == 0.0) getString(R.string.size_unknown) else DecimalFormat("#.##").format(
                            video.size ?: 0
                        )
                    }"
                )
                binding.urlSize.text = sizeText
            }
            binding.urlNote.visibility = View.VISIBLE
            binding.urlNote.text = video.format.name
            binding.urlQuality.text = extractor.server.name
        }

        override fun getItemCount(): Int = extractor.videos.size

        private inner class UrlViewHolder(val binding: ItemUrlBinding) :
            RecyclerView.ViewHolder(binding.root) {
            init {
                itemView.setSafeOnClickListener {
                    if (isDownloadMenu == true) {
                        binding.urlDownload.performClick()
                        return@setSafeOnClickListener
                    }
                    tryWith(true) {
                        val currentEp = media?.anime?.episodes?.getEpisode(media?.anime?.selectedEpisode) ?: episode
                        val epKey = media?.anime?.episodes?.getEpisodeKey(media?.anime?.selectedEpisode) ?: media?.anime?.selectedEpisode
                        if (currentEp != null) {
                            currentEp.selectedExtractor = extractor.server.name
                            currentEp.selectedVideo = bindingAdapterPosition
                        }
                        if (epKey != null) {
                            media?.anime?.episodes?.get(epKey)?.selectedExtractor = extractor.server.name
                            media?.anime?.episodes?.get(epKey)?.selectedVideo = bindingAdapterPosition
                        }
                        if (makeDefault) {
                            media!!.selected!!.server = extractor.server.name
                            media!!.selected!!.video = bindingAdapterPosition
                            model.saveSelected(media!!.id, media!!.selected!!)
                        }
                        Log.d("StreamSelector", "Should start the player")
                        startExoplayer(media!!)
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance(
            server: String? = null,
            la: Boolean = true,
            prev: String? = null,
            episodes: ArrayList<String>
        ): SelectorDialogFragment =
            SelectorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("server", server)
                    putBoolean("launch", la)
                    putString("prev", prev)
                    putStringArrayList("episodes", episodes)
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {}

    override fun onDismiss(dialog: DialogInterface) {
        if (launch == false) {
            activity?.hideSystemBarsExtendView()
            model.epChanged.postValue(true)
            if (prevEpisode != null) {
                media?.anime?.selectedEpisode = prevEpisode
                model.setEpisode(media?.anime?.episodes?.get(prevEpisode) ?: return, "prevEp")
            }
        }
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
