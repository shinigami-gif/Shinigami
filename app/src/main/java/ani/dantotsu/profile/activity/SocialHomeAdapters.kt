package ani.dantotsu.profile.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.connections.anilist.api.Activity
import ani.dantotsu.databinding.ItemSocialActivityBinding
import ani.dantotsu.databinding.ItemSocialFriendBinding
import ani.dantotsu.databinding.ItemSocialMessageBinding
import ani.dantotsu.loadImage

class SocialActivityAdapter(private val items:List<Activity>):RecyclerView.Adapter<SocialActivityAdapter.Holder>(){
 override fun onCreateViewHolder(p:ViewGroup,v:Int)=Holder(ItemSocialActivityBinding.inflate(LayoutInflater.from(p.context),p,false))
 override fun onBindViewHolder(h:Holder,pos:Int){val a=items[pos];h.binding.socialActivityAvatar.loadImage(a.user?.avatar?.medium ?: a.messenger?.avatar?.medium);h.binding.socialActivityText.text=when(a.typename){"ListActivity"->"${a.user?.name ?: a.messenger?.name ?: "User"} ${a.status ?: "updated"} ${a.media?.title?.userPreferred ?: ""}";"TextActivity"->"${a.user?.name ?: "User"} shared an update";"MessageActivity"->"${a.messenger?.name ?: "User"} sent a message";else->"${a.user?.name ?: "User"} was active"};h.binding.socialActivityTime.text=ActivityItemBuilder.getDateTime(a.createdAt)}
 override fun getItemCount()=items.size
 class Holder(val binding:ItemSocialActivityBinding):RecyclerView.ViewHolder(binding.root)
}
class SocialFriendAdapter(private val users:List<SocialLeaderboardUser>):RecyclerView.Adapter<SocialFriendAdapter.Holder>(){
 override fun onCreateViewHolder(p:ViewGroup,v:Int)=Holder(ItemSocialFriendBinding.inflate(LayoutInflater.from(p.context),p,false))
 override fun onBindViewHolder(h:Holder,pos:Int){val u=users[pos];u.avatar?.let{h.binding.socialFriendAvatar.loadImage(it)};h.binding.socialFriendName.text=u.name;h.binding.socialFriendStatus.text=if(pos%3==0)"Watching" else "Online"}
 override fun getItemCount()=users.size
 class Holder(val binding:ItemSocialFriendBinding):RecyclerView.ViewHolder(binding.root)
}
class SocialMessageAdapter(private val items:List<Activity>):RecyclerView.Adapter<SocialMessageAdapter.Holder>(){
 override fun onCreateViewHolder(p:ViewGroup,v:Int)=Holder(ItemSocialMessageBinding.inflate(LayoutInflater.from(p.context),p,false))
 override fun onBindViewHolder(h:Holder,pos:Int){val a=items[pos];h.binding.socialMessageAvatar.loadImage(a.messenger?.avatar?.medium ?: a.user?.avatar?.medium);h.binding.socialMessageText.text=a.message ?: a.text ?: "Anime activity";h.binding.socialMessageTime.text=ActivityItemBuilder.getDateTime(a.createdAt)}
 override fun getItemCount()=items.size
 class Holder(val binding:ItemSocialMessageBinding):RecyclerView.ViewHolder(binding.root)
}