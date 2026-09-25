package ani.dantotsu.media.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.getThemeColor
import ani.dantotsu.MediaSingleton
import ani.dantotsu.getBitmapFromImageView
import ani.dantotsu.resizeBitmap
import ani.dantotsu.databinding.ItemCalendarScheduleBinding
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaDetailsActivity
import ani.dantotsu.loadImage
import ani.dantotsu.setSafeOnClickListener
import java.text.DateFormat
import java.util.concurrent.TimeUnit
import java.util.Locale

class CalendarScheduleAdapter(
    private val media: List<Media>,
    private val dateLabel: String,
    private val activity: androidx.fragment.app.FragmentActivity
) : RecyclerView.Adapter<CalendarScheduleAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemCalendarScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = media[position]
        val b = holder.binding
        val relation = item.relation.orEmpty()
        val parts = relation.split("\n", limit = 2)
        val episode = parts.firstOrNull().orEmpty()
        val time = parts.getOrNull(1).orEmpty()

        b.scheduleTime.text = time.replace(" ", "\n", limit = 1)
        b.scheduleCover.loadImage(item.cover)
        b.scheduleName.text = item.userPreferredName
        b.scheduleEpisode.text = episode
        b.scheduleMeta.text = listOfNotNull(
            item.format,
            item.meanScore?.let { "★ ${it / 10.0}" },
            item.anime?.episodeDuration?.let { "${it}m" }
        ).joinToString("  •  ")
        b.scheduleGenres.text = when {
            item.genres.isNotEmpty() -> item.genres.take(3).joinToString(", ")
            item.tags.isNotEmpty() -> item.tags.take(3).joinToString(", ")
            else -> ""
        }

        val airingTime = parseAiringTime(time)
        val now = System.currentTimeMillis()
        if (airingTime != null && airingTime > now) {
            b.scheduleStatus.text = "Airing Soon"
            b.scheduleRemaining.text = formatRemaining(airingTime - now)
        } else {
            b.scheduleStatus.text = "Aired"
            b.scheduleRemaining.text = ""
        }

        b.scheduleCard.strokeColor = b.root.context.getThemeColor(
            if (airingTime != null && airingTime > now) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorOutline
        )

        b.root.setSafeOnClickListener {
            openMedia(item, b.scheduleCover)
        }

        b.scheduleCover.setSafeOnClickListener {
            openMedia(item, b.scheduleCover)
        }
    }

    private fun openMedia(item: Media, cover: android.widget.ImageView) {
        MediaSingleton.bitmap = resizeBitmap(getBitmapFromImageView(cover), 100)
        ContextCompat.startActivity(
            activity,
            android.content.Intent(activity, MediaDetailsActivity::class.java)
                .putExtra("media", item as java.io.Serializable),
            ActivityOptionsCompat.makeSceneTransitionAnimation(
                activity,
                cover,
                ViewCompat.getTransitionName(cover)!!
            ).toBundle()
        )
    }

    private fun parseAiringTime(time: String): Long? {
        if (time.isBlank()) return null
        return try {
            val parser = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, Locale.getDefault())
            parser.parse("$dateLabel $time")?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun formatRemaining(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m left"
            minutes > 0 -> "${minutes}m left"
            else -> "<1m left"
        }
    }

    override fun getItemCount(): Int = media.size

    class ViewHolder(val binding: ItemCalendarScheduleBinding) : RecyclerView.ViewHolder(binding.root)
}
