package eu.kanade.tachiyomi.extension.es.hmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class HManga : HttpSource() {

    override val name = "HManga"

    override val baseUrl = "https://hmanga.asia"

    override val lang = "es"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd MMM. yyyy", Locale.ENGLISH)

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept-Language", "es-PE,es;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("filterList")
            addQueryParameter("page", page.toString())
            addQueryParameter("sortBy", "views")
            addQueryParameter("asc", "false")
        }.build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".series-card").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".series-title")?.text() ?: ""
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("li a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div[class*=manga-item]").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3 a")?.text() ?: ""
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("filterList")
            addQueryParameter("page", page.toString())
            addQueryParameter("sortBy", "views")
            addQueryParameter("asc", "false")
            addQueryParameter("alpha", query)
        }.build(),
        headers,
    )

    override fun searchMangaParse(response: Response): MangasPage =
        popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""

            thumbnail_url = document.selectFirst("img")?.absUrl("src")

            description =
                document.selectFirst("p")?.text()

            status = SManga.UNKNOWN

            genre = document.select("a[href*=genre]")
                .joinToString { it.text() }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("a[href*=chapter]").map { element ->
            SChapter.create().apply {
                name = element.text()

                setUrlWithoutDomain(element.absUrl("href"))

                date_upload = 0
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.imgAttr())
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()
}