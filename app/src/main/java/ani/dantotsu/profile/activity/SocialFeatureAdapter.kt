package ani.dantotsu.profile.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.databinding.ItemSocialFeatureBinding
import ani.dantotsu.loadImage

data class SocialFeature(
    val title: String,
    val subtitle: String,
    val image: String? = null,
    val onClick: (() -> Unit)? = null
)

class SocialFeatureAdapter(
    private val items: List<SocialFeature>
) : RecyclerView.Adapter<SocialFeatureAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemSocialFeatureBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.socialFeatureTitle.text = item.title
        holder.binding.socialFeatureSubtitle.text = item.subtitle
        item.image?.let { holder.binding.socialFeatureImage.loadImage(it) }
        holder.binding.root.setOnClickListener { item.onClick?.invoke() }
    }

    override fun getItemCount() = items.size
    class Holder(val binding: ItemSocialFeatureBinding) : RecyclerView.ViewHolder(binding.root)
}
