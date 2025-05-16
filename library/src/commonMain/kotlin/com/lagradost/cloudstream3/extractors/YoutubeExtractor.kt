package com.lagradost.cloudstream3.extractors

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
        logError(Exception("YoutubeShortLinkExtractor: Generating URL for ID: $id"))
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
        logError(Exception("YoutubeExtractor: Generating URL for ID: $id"))
        return "$mainUrl/watch?v=$id"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        logError(Exception("YoutubeExtractor: Starting extraction for URL: $url, Referer: $referer"))

        try {
            if (!ytVideos.containsKey(url) || ytVideos[url].isNullOrEmpty()) {
                logError(Exception("YoutubeExtractor: Cache miss for URL: $url, fetching new data"))
                val link = YoutubeStreamLinkHandlerFactory.getInstance().fromUrl(url)
                logError(Exception("YoutubeExtractor: Parsed link with ID: ${link.id}"))

                val s = object : YoutubeStreamExtractor(
                    ServiceList.YouTube,
                    link
                ) {}
                s.fetchPage()
        // HLS URL boşsa, dash manifest URL'yi kullan
        val streamUrl = s.hlsUrl.takeIf { !it.isNullOrEmpty() }
            ?: s.dashMpdUrl.takeIf { !it.isNullOrEmpty() }
            ?: s.videoStreams?.firstOrNull()?.url

        if (!streamUrl.isNullOrEmpty()) {
            ytVideos[url] = streamUrl
        }

                ytVideosSubtitles[url] = try {
                    val subtitles = s.subtitlesDefault.filterNotNull()
                    logError(Exception("YoutubeExtractor: Found ${subtitles.size} subtitles for URL: $url"))
                    subtitles
                } catch (e: Exception) {
                    logError(e)
                    logError(Exception("YoutubeExtractor: Error fetching subtitles for URL: $url, Error: ${e.message}"))
                    emptyList()
                }
            } else {
                logError(Exception("YoutubeExtractor: Cache hit for URL: $url, using cached HLS URL"))
            }

            ytVideos[url]?.let {
                logError(Exception("YoutubeExtractor: Invoking callback with HLS URL: $it"))
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = it,
                        type = INFER_TYPE
                    )
                )
            } ?: logError(Exception("YoutubeExtractor: No HLS URL found for $url"))

            ytVideosSubtitles[url]?.mapNotNull {
                if (it.languageTag == null || it.content == null) {
                    logError(Exception("YoutubeExtractor: Skipping invalid subtitle for URL: $url, Language: ${it.languageTag}"))
                    null
                } else {
                    logError(Exception("YoutubeExtractor: Processing subtitle for URL: $url, Language: ${it.languageTag}"))
                    SubtitleFile(
                        it.languageTag,
                        it.content
                    )
                }
            }?.forEach {
                logError(Exception("YoutubeExtractor: Invoking subtitle callback for language: ${it.lang}"))
                subtitleCallback(it)
            } ?: logError(Exception("YoutubeExtractor: No subtitles found for URL: $url"))

        } catch (e: Exception) {
            logError(e)
            logError(Exception("YoutubeExtractor: Error during extraction for URL: $url, Error: ${e.message}"))
        }
    }
}
