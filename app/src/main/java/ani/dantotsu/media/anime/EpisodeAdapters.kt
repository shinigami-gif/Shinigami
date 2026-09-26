package ani.dantotsu.media.anime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.updateProgress
import ani.dantotsu.databinding.ItemEpisodeCompactBinding
import ani.dantotsu.databinding.ItemEpisodeGridBinding
import ani.dantotsu.databinding.ItemEpisodeListBinding
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaNameAdapter
import ani.dantotsu.setAnimation
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.util.customAlertDialog
import ani.dantotsu.util.SizeFormatter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl

fun handleProgress(cont: LinearLayout, bar: View, empty: View, mediaId: Int, ep: String) {
    val cleanNum = MediaNameAdapter.findChapterNumber(ep)?.let {
        if (it % 1 == 0f) it.toInt().toString() else it.toString()
    } ?: MediaNameAdapter.findEpisodeNumber(ep)?.let {
        if (it % 1 == 0f) it.toInt().toString() else it.toString()
    }
    val curr = PrefManager.getNullableCustomVal("${mediaId}_${ep}", null, Long::class.java)
        ?: cleanNum?.let { PrefManager.getNullableCustomVal("${mediaId}_${it}", null, Long::class.java) }
    val max = PrefManager.getNullableCustomVal("${mediaId}_${ep}_max", null, Long::class.java)
        ?: cleanNum?.let { PrefManager.getNullableCustomVal("${mediaId}_${it}_max", null, Long::class.java) }
    if (curr != null && max != null && max > 0L) {
        cont.visibility = View.VISIBLE
        val div = (curr.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        val barParams = bar.layoutParams as LinearLayout.LayoutParams
        barParams.weight = div
        bar.layoutParams = barParams
        val params = empty.layoutParams as LinearLayout.LayoutParams
        params.weight = 1f - div
        empty.layoutParams = params
    } else {
        cont.visibility = View.GONE
    }
}

@OptIn(UnstableApi::class)
class EpisodeAdapter(
    private var type: Int,
    private val media: Media,
    private val fragment: AnimeWatchFragment,
    var arr: List<Episode> = arrayListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val context = fragment.requireContext()

    companion object {
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return (when (viewType) {
            0 -> EpisodeListViewHolder(
                ItemEpisodeListBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            1 -> EpisodeGridViewHolder(
                ItemEpisodeGridBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            2 -> EpisodeCompactViewHolder(
                ItemEpisodeCompactBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> throw IllegalArgumentException()
        })
    }

    override fun getItemViewType(position: Int): Int {
        return type
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val ep = arr[position]
        val title = if (!ep.title.isNullOrEmpty() && ep.title != "null") {
            ep.title?.let { MediaNameAdapter.removeEpisodeNumber(it) }
        } else {
            ep.number
        } ?: ""

        when (holder) {
            is EpisodeListViewHolder -> {
                val binding = holder.binding
                setAnimation(fragment.requireContext(), holder.binding.root)

                val thumb = ep.thumb?.let {
                    if (it.url.isNotEmpty()) {
                        if (it.url.startsWith("content://") || it.url.startsWith("file://")) {
                            it.url
                        } else {
                            GlideUrl(it.url) { it.headers }
                        }
                    } else null
                }
                Glide.with(binding.itemMediaImage).load(thumb ?: media.cover).override(400, 0)
                    .into(binding.itemMediaImage)
                binding.itemEpisodeNumber.text = ep.number
                binding.itemEpisodeTitle.text = if (ep.number == title) "Episode $title" else title

                if (ep.filler) {
                    binding.itemEpisodeFiller.visibility = View.VISIBLE
                    binding.itemEpisodeFillerView.visibility = View.VISIBLE
                } else {
                    binding.itemEpisodeFiller.visibility = View.GONE
                    binding.itemEpisodeFillerView.visibility = View.GONE
                }

                if (ep.rating != null) {
                    binding.itemEpisodeRating.visibility = View.VISIBLE
                    binding.itemEpisodeRating.text = "★ ${ep.rating}"
                } else {
                    binding.itemEpisodeRating.visibility = View.GONE
                }

                if (ep.date != null) {
                    binding.itemEpisodeDate.visibility = View.VISIBLE
                    binding.itemEpisodeDate.text = ep.date
                } else {
                    binding.itemEpisodeDate.visibility = View.GONE
                }

                binding.itemEpisodeDesc.isVisible = !ep.desc.isNullOrBlank()
                binding.itemEpisodeDesc.text = ep.desc ?: ""
                holder.bind(ep.desc)

                val epNum = MediaNameAdapter.findEpisodeNumber(ep.number) ?: ep.number.toFloatOrNull() ?: 9999f
                if (media.userProgress != null) {
                    if (epNum <= media.userProgress!!.toFloat()) {
                        binding.itemEpisodeViewedCover.visibility = View.VISIBLE
                        binding.itemEpisodeViewed.visibility = View.VISIBLE
                    } else {
                        binding.itemEpisodeViewedCover.visibility = View.GONE
                        binding.itemEpisodeViewed.visibility = View.GONE
                        binding.itemEpisodeCont.setOnLongClickListener {
                            updateProgress(media, ep.number)
                            true
                        }
                    }
                } else {
                    binding.itemEpisodeViewedCover.visibility = View.GONE
                    binding.itemEpisodeViewed.visibility = View.GONE
                }

                handleProgress(
                    binding.itemMediaProgressCont,
                    binding.itemMediaProgress,
                    binding.itemMediaProgressEmpty,
                    media.id,
                    ep.number
                )
            }

            is EpisodeGridViewHolder -> {
                val binding = holder.binding
                setAnimation(fragment.requireContext(), holder.binding.root)

                val thumb = ep.thumb?.let {
                    if (it.url.isNotEmpty()) {
                        if (it.url.startsWith("content://") || it.url.startsWith("file://")) {
                            it.url
                        } else {
                            GlideUrl(it.url) { it.headers }
                        }
                    } else null
                }
                Glide.with(binding.itemMediaImage).load(thumb ?: media.cover).override(400, 0)
                    .into(binding.itemMediaImage)

                binding.itemEpisodeNumber.text = ep.number
                binding.itemEpisodeTitle.text = title

                if (ep.rating != null) {
                    binding.itemEpisodeRating.visibility = View.VISIBLE
                    binding.itemEpisodeRating.text = "★ ${ep.rating}"
                } else {
                    binding.itemEpisodeRating.visibility = View.GONE
                }

                if (ep.date != null) {
                    binding.itemEpisodeDate.visibility = View.VISIBLE
                    binding.itemEpisodeDate.text = ep.date
                } else {
                    binding.itemEpisodeDate.visibility = View.GONE
                }

                if (ep.filler) {
                    binding.itemEpisodeFiller.visibility = View.VISIBLE
                    binding.itemEpisodeFillerView.visibility = View.VISIBLE
                } else {
                    binding.itemEpisodeFiller.visibility = View.GONE
                    binding.itemEpisodeFillerView.visibility = View.GONE
                }
                val epNum = MediaNameAdapter.findEpisodeNumber(ep.number) ?: ep.number.toFloatOrNull() ?: 9999f
                if (media.userProgress != null) {
                    if (epNum <= media.userProgress!!.toFloat()) {
                        binding.itemEpisodeViewedCover.visibility = View.VISIBLE
                        binding.itemEpisodeViewed.visibility = View.VISIBLE
                    } else {
                        binding.itemEpisodeViewedCover.visibility = View.GONE
                        binding.itemEpisodeViewed.visibility = View.GONE
                        binding.itemEpisodeCont.setOnLongClickListener {
                            updateProgress(media, ep.number)
                            true
                        }
                    }
                } else {
                    binding.itemEpisodeViewedCover.visibility = View.GONE
                    binding.itemEpisodeViewed.visibility = View.GONE
                }
                handleProgress(
                    binding.itemMediaProgressCont,
                    binding.itemMediaProgress,
                    binding.itemMediaProgressEmpty,
                    media.id,
                    ep.number
                )
            }

            is EpisodeCompactViewHolder -> {
                val binding = holder.binding
                setAnimation(fragment.requireContext(), holder.binding.root)
                binding.itemEpisodeNumber.text = ep.number
                binding.itemEpisodeFillerView.isVisible = ep.filler
                val epNum = MediaNameAdapter.findEpisodeNumber(ep.number) ?: ep.number.toFloatOrNull() ?: 9999f
                if (media.userProgress != null) {
                    if (epNum <= media.userProgress!!.toFloat())
                        binding.itemEpisodeViewedCover.visibility = View.VISIBLE
                    else {
                        binding.itemEpisodeViewedCover.visibility = View.GONE
                        binding.itemEpisodeCont.setOnLongClickListener {
                            updateProgress(media, ep.number)
                            true
                        }
                    }
                }
                handleProgress(
                    binding.itemMediaProgressCont,
                    binding.itemMediaProgress,
                    binding.itemMediaProgressEmpty,
                    media.id,
                    ep.number
                )
            }
        }
    }

    override fun getItemCount(): Int = arr.size

    inner class EpisodeListViewHolder(val binding: ItemEpisodeListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                if (bindingAdapterPosition in arr.indices) {
                    fragment.onEpisodeClick(arr[bindingAdapterPosition].number)
                }
            }
            binding.itemEpisodeDesc.setOnClickListener {
                binding.itemEpisodeDesc.maxLines = if (binding.itemEpisodeDesc.maxLines == 3) 100 else 3
            }
        }

        fun bind(desc: String?) {
            binding.itemEpisodeDesc.visibility = if (!desc.isNullOrBlank()) View.VISIBLE else View.GONE
            binding.itemEpisodeDesc.text = desc ?: ""
        }
    }

    fun updateType(t: Int) {
        type = t
    }
}
