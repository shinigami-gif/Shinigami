package ani.dantotsu.profile.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.databinding.ItemSocialLeaderboardBinding
import ani.dantotsu.databinding.ItemSocialLeaderboardRowBinding
import ani.dantotsu.loadImage

data class SocialLeaderboardUser(val id:Int,val name:String,val avatar:String?,val points:Int)
data class SocialLeaderboardPage(val title:String,val users:List<SocialLeaderboardUser>)

class SocialLeaderboardAdapter(private val pages:List<SocialLeaderboardPage>) : RecyclerView.Adapter<SocialLeaderboardAdapter.Holder>() {
 override fun onCreateViewHolder(parent:ViewGroup,viewType:Int)=Holder(ItemSocialLeaderboardBinding.inflate(LayoutInflater.from(parent.context),parent,false))
 override fun onBindViewHolder(holder:Holder,position:Int){
  val users=pages[position].users.sortedByDescending{it.points}.take(8)
  listOf(1,2,3).forEachIndexed{idx,rank->
   val u=users.getOrNull(rank-1)
   val avatar=when(rank){1->holder.binding.lbAvatar1;2->holder.binding.lbAvatar2;else->holder.binding.lbAvatar3}
   val name=when(rank){1->holder.binding.lbName1;2->holder.binding.lbName2;else->holder.binding.lbName3}
   val pts=when(rank){1->holder.binding.lbPoints1;2->holder.binding.lbPoints2;else->holder.binding.lbPoints3}
   val label=when(rank){1->holder.binding.lbRank1;2->holder.binding.lbRank2;else->holder.binding.lbRank3}
   label.text=rank.toString()
   name.text=u?.name ?: "—"; pts.text=u?.let{"${it.points} pts"} ?: "—"
   u?.avatar?.let{avatar.loadImage(it)}
  }
  holder.binding.lbOthers.layoutManager=LinearLayoutManager(holder.itemView.context)
  holder.binding.lbOthers.adapter=LeaderboardRowAdapter(users.drop(3))
 }
 override fun getItemCount()=pages.size
 class Holder(val binding:ItemSocialLeaderboardBinding):RecyclerView.ViewHolder(binding.root)
}
class LeaderboardRowAdapter(private val users:List<SocialLeaderboardUser>):RecyclerView.Adapter<LeaderboardRowAdapter.Holder>(){
 override fun onCreateViewHolder(p:ViewGroup,v:Int)=Holder(ItemSocialLeaderboardRowBinding.inflate(LayoutInflater.from(p.context),p,false))
 override fun onBindViewHolder(h:Holder,pos:Int){val u=users[pos];h.binding.lbOtherRank.text="#${pos+4}";u.avatar?.let{h.binding.lbOtherAvatar.loadImage(it)};h.binding.lbOtherName.text=u.name;h.binding.lbOtherPoints.text="${u.points} pts"}
 override fun getItemCount()=users.size
 class Holder(val binding:ItemSocialLeaderboardRowBinding):RecyclerView.ViewHolder(binding.root)
}