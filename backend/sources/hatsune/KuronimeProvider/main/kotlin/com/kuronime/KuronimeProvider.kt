package com.kuronime
import com.baseprovider.streamix.ProviderExtractorsNative
import com.baseprovider.streamix.StreamixLegacyAes
import com.baseprovider.streamix.StreamixRuntime
import streamix.core.*
import java.net.URLEncoder
class KuronimeProvider: StreamixProvider {
 override val id="kuronime"; private val base="https://kuronime.sbs"; private val animeku="https://animeku.org"
 private suspend fun get(u:String,r:String=base)=StreamixRuntime.http?.get(u,headers=mapOf("User-Agent" to "Mozilla/5.0"),referer=r)
 override suspend fun search(q:String,page:Int)=get(base+"/?s="+URLEncoder.encode(q,"UTF-8"))?.document?.select(".listupd article")?.mapNotNull{e->val a=e.selectFirst("a[href]")?:return@mapNotNull null;ProviderAnime(id,a.absUrl("href"),e.selectFirst("h2,.bsuxtt,.tt>h4,.entry-title")?.text()?.trim().orEmpty(),a.absUrl("href"),e.selectFirst("img")?.absUrl("src"))}?:emptyList()
 override suspend fun detail(a:ProviderAnime):ProviderAnime?{val d=get(a.url)?.document?:return null;return a.copy(title=d.selectFirst(".entry-title")?.text()?.trim()?:a.title,poster=d.selectFirst("div.l[itemprop=image] img,.l img")?.absUrl("src")?:a.poster,description=d.select("span.const>p").text().trim())}
 override suspend fun episodes(a:ProviderAnime):List<ProviderEpisode>{val d=get(a.url)?.document?:return emptyList();return d.select("div.bixbox.bxcl>ul>li a").mapNotNull{e->val n=Regex("\\d+").find(e.text())?.value?.toIntOrNull()?:return@mapNotNull null;ProviderEpisode(id+"-"+n,n,e.absUrl("href"),e.text().trim(),e.absUrl("href"))}.reversed()}
 override suspend fun streams(e:ProviderEpisode):List<ProviderStream>{
  val d=get(e.url,e.url)?.document?:return emptyList(); val script=d.select("script").map{it.data()}.firstOrNull{it.contains("_0xa100d42aa")}?:return emptyList(); val sid=script.substringAfter("_0xa100d42aa = \"").substringBefore("\";")
  val payload=org.json.JSONObject().put("id",sid).toString(); val body=StreamixRuntime.http?.post(animeku+"/api/v9/sources",body=payload,headers=mapOf("User-Agent" to "Mozilla/5.0","Content-Type" to "application/json"),referer=e.url) ?: return emptyList()
  val obj=runCatching{StreamixRuntime.json?.parseObject(body.text)}.getOrNull()?:return emptyList(); val out=mutableListOf<ProviderStream>()
  fun dec(v:Any?):String? { val c=StreamixRuntime.crypto?:return null; val j=StreamixRuntime.json?:return null; return v?.toString()?.let{StreamixLegacyAes.decryptBase64(it,"3&!Z0M,VIZ;dZW==".toByteArray(),c,j)} }
  dec(obj["src"])?.let{src->runCatching{val so=StreamixRuntime.json?.parseObject(src);val u=(so?.get("src") as? String)?.replace("\\","");if(!u.isNullOrBlank())out+=ProviderStream(id+"-src",u,null,"m3u8",headers=mapOf("Origin" to animeku),referer=animeku+"/")}}
  dec(obj["mirror"])?.let{mir->runCatching{val mo=StreamixRuntime.json?.parseObject(mir);(mo?.get("embed") as? Map<*,*>)?.forEach{(_,vs)->(vs as? Map<*,*>)?.values?.forEach{u->val url=u?.toString();if(!url.isNullOrBlank())ProviderExtractorsNative.resolve(url,e.url){x->out+=ProviderStream(id+"-"+out.size,x.url,x.quality,x.type.name.lowercase(),headers=x.headers,referer=x.referer)}}}}}
  return out.distinctBy{it.url}
 }
}