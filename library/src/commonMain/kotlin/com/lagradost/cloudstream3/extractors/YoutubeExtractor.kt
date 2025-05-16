package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.schemaStripRegex
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.SubtitlesStream

class YoutubeShortLinkExtractor : YoutubeExtractor() {
    override val mainUrl = "https://youtu.be"

    override fun getExtractorUrl(id: String): String {
        Log.d("YoutubeShortLinkExtractor: Generating URL for ID: $id")
        return "$mainUrl/$id"
    }
}

class YoutubeMobileExtractor : YoutubeExtractor() {
    override val mainUrl = "https://m.youtube.com"
}

class YoutubeNoCookieExtractor : YoutubeExtractor() {
    override val mainUrl = "https://www.youtube-nocookie.com"
}

open class YoutubeExtractor : ExtractorApi() {
    override val mainUrl = "https://www.youtube.com"
    override val requiresReferer = false
    override val name = "YouTube"

    companion object {
        private var ytVideos: MutableMap<String, String> = mutableMapOf()
        private var ytVideosSubtitles: MutableMap<String, List<SubtitlesStream>> = mutableMapOf()
    }

    override fun getExtractorUrl(id: String): String {
        Log.d("YoutubeExtractor: Generating URL for ID: $id")
        return "$mainUrl/watch?v=$id"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("YoutubeExtractor: Starting extraction for URL: $url, Referer: $referer")

        try {
            if (!ytVideos.containsKey(url) || ytVideos[url].isNullOrEmpty()) {
                Log.d("YoutubeExtractor: Cache miss for URL: $url, fetching new data")
                val link = YoutubeStreamLinkHandlerFactory.getInstance().fromUrl(url)
                Log.d("YoutubeExtractor: Parsed link with ID: ${link.id}")

                val s = object : YoutubeStreamExtractor(
                    ServiceList.YouTube,
                    link
                ) {}
                s.fetchPage()
                ytVideos[url] = s.hlsUrl
                Log.d("YoutubeExtractor: Stored HLS URL for $url: ${s.hlsUrl}")

                ytVideosSubtitles[url] = try {
                    val subtitles = s.subtitlesDefault.filterNotNull()
                    Log.d("YoutubeExtractor: Found ${subtitles.size} subtitles for URL: $url")
                    subtitles
                } catch (e: Exception) {
                    logError(e)
                    Log.d("YoutubeExtractor: Error fetching subtitles for URL: $url, Error: ${e.message}")
                    emptyList()
                }
            } else {
                Log.d("YoutubeExtractor: Cache hit for URL: $url, using cached HLS URL")
            }

            ytVideos[url]?.let {
                Log.d("YoutubeExtractor: Invoking callback with HLS URL: $it")
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = it,
                        type = ExtractorLinkType.M3U8
                    )
                )
            } ?: Log.d("YoutubeExtractor: No HLS URL found for $url")

            ytVideosSubtitles[url]?.mapNotNull {
                if (it.languageTag == null || it.content == null) {
                    Log.d("YoutubeExtractor: Skipping invalid subtitle for URL: $url, Language: ${it.languageTag}")
                    null
                } else {
                    Log.d("YoutubeExtractor: Processing subtitle for URL: $url, Language: ${it.languageTag}")
                    SubtitleFile(
                        it.languageTag,
                        it.content
                    )
                }
            }?.forEach {
                Log.d("YoutubeExtractor: Invoking subtitle callback for language: ${it.lang}")
                subtitleCallback(it)
            } ?: Log.d("YoutubeExtractor: No subtitles found for URL: $url")

        } catch (e: Exception) {
            logError(e)
            Log.d("YoutubeExtractor: Error during extraction for URL: $url, Error: ${e.message}")
        }
    }
}
