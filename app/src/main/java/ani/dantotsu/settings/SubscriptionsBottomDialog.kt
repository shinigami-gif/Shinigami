package ani.dantotsu.settings

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.R
import ani.dantotsu.databinding.BottomSheetRecyclerBinding
import ani.dantotsu.databinding.ItemSubscriptionBinding
import ani.dantotsu.loadImage
import ani.dantotsu.notifications.subscription.SubscriptionHelper
import com.xwray.groupie.GroupieAdapter
import com.xwray.groupie.viewbinding.BindableItem
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SubscriptionsBottomDialog : BottomSheetDialogFragment() {
    private var _binding: BottomSheetRecyclerBinding? = null
    private val binding get() = _binding!!
    private val adapter: GroupieAdapter = GroupieAdapter()
    private var subscriptions: Map<Int, SubscriptionHelper.Companion.SubscribeMedia> = mapOf()
    private var groupedSubscriptions: MutableMap<String, MutableList<SubscriptionHelper.Companion.SubscribeMedia>> = mutableMapOf()
    private var currentFilter: String? = null
    private val animeExtension: AnimeExtensionManager = Injekt.get()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BottomSheetRecyclerBinding.inflate(inflater, container, false)
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.repliesRecyclerView.adapter = adapter
        binding.repliesRecyclerView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.VERTICAL,
            false
        )
        val context = requireContext()
        binding.title.text = context.getString(R.string.subscriptions)
        binding.replyButton.apply {
            visibility = View.VISIBLE
            setImageResource(R.drawable.ic_round_filter_list_24)
            contentDescription = context.getString(R.string.filter)
            setOnClickListener { showFilterMenu() }
        }

        groupedSubscriptions = subscriptions.values.groupBy {
            SubscriptionHelper.getAnimeParser(it.id).name
        }.mapValues { it.value.toMutableList() }.toMutableMap()

        updateAdapter()
    }

    private fun updateAdapter() {
        adapter.clear()
        val visibleSubscriptions = if (currentFilter == null) {
            groupedSubscriptions
        } else {
            groupedSubscriptions.filterKeys { it == currentFilter }
        }

        visibleSubscriptions.forEach { (parserName, mediaList) ->
            adapter.add(SubscriptionSource(mediaList) { media ->
                SubscriptionHelper.deleteSubscription(media.id)
                groupedSubscriptions[parserName]?.remove(media)
                updateAdapter()
            })
        }
    }

    private fun showFilterMenu() {
        val context = requireContext()
        val popup = PopupMenu(context, binding.replyButton)
        popup.menu.add(context.getString(R.string.all))
        groupedSubscriptions.keys.sorted().forEach { parserName ->
            popup.menu.add(parserName)
        }
        popup.setOnMenuItemClickListener { item ->
            currentFilter = if (item.title == context.getString(R.string.all)) {
                null
            } else {
                item.title.toString()
            }
            updateAdapter()
            true
        }
        popup.show()
    }

    private fun getParserIcon(parserName: String): Drawable? {
        return animeExtension.installedExtensionsFlow.value
            .find { it.name == parserName }?.icon
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(subscriptions: Map<Int, SubscriptionHelper.Companion.SubscribeMedia>): SubscriptionsBottomDialog {
            val dialog = SubscriptionsBottomDialog()
            dialog.subscriptions = subscriptions
            return dialog
        }
    }
}

private class SubscriptionSource(
    private val mediaList: List<SubscriptionHelper.Companion.SubscribeMedia>,
    private val onDelete: (SubscriptionHelper.Companion.SubscribeMedia) -> Unit
) : BindableItem<ItemSubscriptionBinding>() {
    override fun bind(viewBinding: ItemSubscriptionBinding, position: Int) {
        val media = mediaList[position]
        viewBinding.subscriptionName.text = media.name
        media.image?.let(viewBinding.subscriptionCover::loadImage)
        viewBinding.deleteSubscription.setOnClickListener { onDelete(media) }
    }

    override fun getLayout(): Int = R.layout.item_subscription

    override fun initializeViewBinding(view: View): ItemSubscriptionBinding =
        ItemSubscriptionBinding.bind(view)
}
