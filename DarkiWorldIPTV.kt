package com.darkiworld

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

class DarkiWorldIPTV : MainAPI() {
    override var lang = "fr"
    override var name = "DarkiWorld IPTV"
    override val usesWebView = false
    override val hasMainPage = true
    override val hasDownloadSupport = false

    override val supportedTypes = setOf(TvType.Live)

    private val mainUrl = "https://darkiworld3.com"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categories = scrapeCategories()
        val streams = scrapeLiveStreams()

        val cats = mutableListOf<HomePageList>()

        if (page <= 1) {
            categories.forEach { cat ->
                val tempStreamList = streams.filter { it.category == cat.name }.map { stream ->
                    LiveSearchResponse(
                        name = stream.name,
                        url = stream.toJson(),
                        apiName = this@DarkiWorldIPTV.name,
                        type = TvType.Live,
                        posterUrl = stream.icon
                    )
                }
                cats.add(HomePageList(cat.name, tempStreamList, false))
            }
        }
        return newHomePageResponse(cats)
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<StreamData>(url)

        return newMovieLoadResponse(
            name = data.name,
            url = url,
            dataUrl = url,
            type = TvType.Live
        ) {
            this.posterUrl = data.icon
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsedData = parseJson<StreamData>(data)

        callback.invoke(
            ExtractorLink(
                source = parsedData.name,
                name = parsedData.name,
                url = parsedData.streamUrl,
                referer = mainUrl,
                quality = Qualities.Unknown.value,
                type = INFER_TYPE,
            )
        )
        return true
    }

    /** Scrape les catégories IPTV **/
    private suspend fun scrapeCategories(): List<CategoryData> {
        val html = app.get(mainUrl).text
        val doc = Jsoup.parse(html)
        return doc.select("div.category-item").map {
            CategoryData(
                name = it.text(),
                id = it.attr("data-id")
            )
        }
    }

    /** Scrape les streams IPTV **/
    private suspend fun scrapeLiveStreams(): List<StreamData> {
        val html = app.get("$mainUrl/live").text
        val doc = Jsoup.parse(html)
        return doc.select("div.live-stream").map {
            StreamData(
                name = it.selectFirst(".stream-title")?.text() ?: "Inconnu",
                streamUrl = it.selectFirst("a")?.attr("href") ?: "",
                icon = it.selectFirst("img")?.attr("src") ?: "",
                category = it.selectFirst(".category")?.text() ?: "Divers"
            )
        }
    }

    data class CategoryData(val name: String, val id: String)
    data class StreamData(val name: String, val streamUrl: String, val icon: String, val category: String)
}
