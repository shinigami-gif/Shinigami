package ani.dantotsu.profile.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.connections.shinigami.ShinigamiActivity
import ani.dantotsu.databinding.ItemSocialActivityBinding
import ani.dantotsu.databinding.ItemSocialFriendBinding
import ani.dantotsu.databinding.ItemSocialMessageBinding
import ani.dantotsu.loadImage

class SocialActivityAdapter(private val items:List<ShinigamiActivity>):RecyclerView.Adapter<SocialActivityAdapter.Holder>(){
 override fun onCreateViewHolder(p:ViewGroup,v:Int)=Holder(ItemSocialActivityBinding.inflate(LayoutInflater.from(p.context),p,false))
 override fun onBindViewHolder(h:Holder,pos:Int){val a=items[pos];h.binding.socialActivityAvatar.loadImage(a.author.avatarUrl);h.binding.socialActivityText.text=when(a.typename){"ListActivity"->"${a.author.displayName ?: a.author.username} ${a.type} ${a.mediaTitle ?: ""}";"TextActivity"->"${a.author.displayName ?: a.author.username} shared an update";"MessageActivity"->"${a.author.displayName ?: a.author.username} sent a message";else->"${a.user?.name ?: "User"} was active"};h.binding.socialActivityTime.text=ActivityItemBuilder.getDateTime(a.createdAt)}
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
 override fun onBindViewHolder(h:Holder,pos:Int){val a=items[pos];h.binding.socialMessageAvatar.loadImage(a.author.avatarUrl);h.binding.socialMessageText.text=a.text ?: a.mediaTitle ?: "Anime activity";h.binding.socialMessageTime.text=ActivityItemBuilder.getDateTime(a.createdAt)}
 override fun getItemCount()=items.size
 class Holder(val binding:ItemSocialMessageBinding):RecyclerView.ViewHolder(binding.root)
}