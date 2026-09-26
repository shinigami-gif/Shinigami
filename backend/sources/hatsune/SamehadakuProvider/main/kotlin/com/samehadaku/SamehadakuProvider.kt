package com.samehadaku
import com.baseprovider.streamix.ProviderExtractorsNative
import com.baseprovider.streamix.StreamixRuntime
import streamix.core.*
class SamehadakuProvider: StreamixProvider {
 override val id="samehadaku"; private val base="https://v2.samehadaku.how"
 private suspend fun get(u:String,r:String=base)=StreamixRuntime.http?.get(u,headers=mapOf("User-Agent" to "Mozilla/5.0"),referer=r)
 override suspend fun search(q:String,page:Int)=get(base+"/?s="+java.net.URLEncoder.encode(q,"UTF-8"))?.document?.select("div.animepost,article.animpost")?.mapNotNull{e->val a=e.selectFirst("a[href]")?:return@mapNotNull null;ProviderAnime(id,a.absUrl("href"),e.selectFirst("div.title h2,div.tt h4")?.text()?.trim()?:a.attr("title"),a.absUrl("href"),e.selectFirst("img")?.absUrl("src"))}?:emptyList()
 override suspend fun detail(a:ProviderAnime):ProviderAnime?{val d=get(a.url)?.document?:return null;val t=d.selectFirst("h1.entry-title")?.text()?.replace(Regex("(?i)(Nonton|Anime|Subtitle\\s+Indonesia|Sub\\s+Indo|Lengkap|Batch)"),"")?.trim()?:a.title;return a.copy(title=t,poster=d.selectFirst("div.thumb>img")?.absUrl("src")?:a.poster,description=d.select("div.desc p,div.entry-content p").text().trim())}
 override suspend fun episodes(a:ProviderAnime)=get(a.url)?.document?.select("div.lstepsiode.listeps ul li a")?.mapNotNull{e->val n=Regex("(?i)Episode\\s*(\\d+)").find(e.text())?.groupValues?.get(1)?.toIntOrNull()?:return@mapNotNull null;ProviderEpisode(id+"-"+n,n,e.absUrl("href"),e.text(),e.absUrl("href"))}?.sortedBy{it.number}?:emptyList()
 override suspend fun streams(e:ProviderEpisode):List<ProviderStream>{val d=get(e.url,e.url)?.document?:return emptyList();val out=mutableListOf<ProviderStream>();d.select("div#downloadb li a").forEach{a->val u=a.absUrl("href");if(u.isBlank())return@forEach;ProviderExtractorsNative.resolve(u,e.url){x->out+=ProviderStream(id+"-"+out.size,x.url,x.quality,x.type.name.lowercase(),headers=x.headers,referer=x.referer)}};return out.distinctBy{it.url}}
}