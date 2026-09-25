package ani.dantotsu.media.user

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

class ListViewPagerAdapter(
    private val size: Int,
    private val calendar: Boolean,
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {
    override fun getItemCount(): Int = size
    override fun createFragment(position: Int): Fragment =
        ListFragment.newInstance(position, calendar)
}