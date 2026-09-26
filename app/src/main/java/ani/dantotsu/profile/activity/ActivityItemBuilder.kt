package ani.dantotsu.profile.activity

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class ActivityItemBuilder {
    companion object {
        fun getDateTime(timestamp: Int): String = format(Date(timestamp * 1000L))

        fun getDateTime(timestamp: String): String {
            val date = runCatching { Date.from(Instant.parse(timestamp)) }.getOrNull() ?: return timestamp
            return format(date)
        }

        private fun format(targetDate: Date): String {
            if (targetDate < Date(946684800000L)) return ""
            val difference = Date().time - targetDate.time
            return when (val daysDifference = difference / (1000 * 60 * 60 * 24)) {
                0L -> {
                    val hoursDifference = difference / (1000 * 60 * 60)
                    val minutesDifference = (difference / (1000 * 60)) % 60
                    when {
                        hoursDifference > 0 -> "$hoursDifference hour${if (hoursDifference > 1) "s" else ""} ago"
                        minutesDifference > 0 -> "$minutesDifference minute${if (minutesDifference > 1) "s" else ""} ago"
                        else -> "Just now"
                    }
                }
                1L -> "1 day ago"
                in 2..6 -> "$daysDifference days ago"
                else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(targetDate)
            }
        }
    }
}
