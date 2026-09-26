package ani.dantotsu.others

import ani.dantotsu.client
import ani.dantotsu.parsers.ShowResponse
import ani.dantotsu.tryWithSuspend
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object MalSyncBackup {
    @Serializable data class MalBackUpSync(@SerialName("Pages") val pages: Map<String, Map<String, Page>>? = null)
    @Serializable data class Page(val identifier:String,val title:String,val url:String?=null,val image:String?=null,val active:Boolean?=null)
    suspend fun get(id:Int,name:String,dub:Boolean=false):ShowResponse?=tryWithSuspend {
        val json=client.get("https://raw.githubusercontent.com/MALSync/MAL-Sync-Backup/master/data/anilist/anime/$id.json")
        if(json.text=="404: Not Found") return@tryWithSuspend null
        json.parsed<MalBackUpSync>().pages?.get(name)?.forEach { val p=it.value; val isDub=p.title.lowercase().replace(" ","").endsWith("(dub)"); val slug=if(dub==isDub)p.identifier else null; if(slug!=null&&p.active==true&&p.url!=null) return@tryWithSuspend ShowResponse(p.title,if(name=="Gogoanime"||name=="Tenshi")slug else p.url,p.image?:"") }
        null
    }
}