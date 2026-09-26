package ani.dantotsu.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.R
import ani.dantotsu.databinding.FragmentStatisticsBinding
import ani.dantotsu.setBaseline

class ShinigamiStatsFragment : Fragment() {
    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    private val activity: ProfileActivity get() = requireActivity() as ProfileActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val profile = activity.shinigamiProfile
        binding.root.setBaseline(activity.binding.profileNavBarContainer)
        binding.filterContainer.visibility = View.GONE
        binding.compare.visibility = View.GONE
        binding.statisticProgressBar.visibility = View.GONE
        binding.statisticList.layoutManager = LinearLayoutManager(requireContext())
        binding.statisticList.adapter = null

        val values = listOf(
            "Anime total" to profile.stats.animeTotal,
            "Watching" to profile.stats.animeWatching,
            "Completed" to profile.stats.animeCompleted,
            "Planned" to profile.stats.animePlanned,
            "Paused" to profile.stats.animePaused,
            "Dropped" to profile.stats.animeDropped,
            "Episodes watched" to profile.stats.episodesWatched,
            "Mean score" to (profile.stats.meanScore?.toString() ?: "—")
        )

        binding.statisticList.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<RowHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
                val view = LayoutInflater.from(parent.context).inflate(
                    android.R.layout.simple_list_item_2, parent, false
                )
                return RowHolder(view)
            }

            override fun onBindViewHolder(holder: RowHolder, position: Int) {
                val (label, value) = values[position]
                holder.title.text = label
                holder.value.text = value.toString()
            }

            override fun getItemCount(): Int = values.size
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class RowHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val value: TextView = view.findViewById(android.R.id.text2)
    }
}
