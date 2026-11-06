package com.example.ui.reports

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.countryatv.core.reports.HistoryEvent
import com.countryatv.core.reports.HistoryPage
import com.countryatv.core.reports.ReportDto
import com.countryatv.core.reports.ReportKeys
import com.countryatv.core.reports.ReportSummary
import com.countryatv.core.reports.UnitFilter
import com.example.shop.RangeQuery
import com.example.shop.ShopResult
import com.example.shop.UnitDto
import java.io.File

// Each screen on the More stack keeps its own state object, so Back returns to the same range,
// breakdown and scroll (reports spec §14 "App"). The stack is rebuilt whenever the signed-in
// person changes (the wipe, §9), so nothing of one person's reports survives for the next.

// A call's outcome while it isn't a success: words for the screen.
sealed interface Problem {
    // No permission (an old link, or the permission was removed): §2.
    data object NotAllowed : Problem
    // No answer: offline, or the shop system is down.
    data class Offline(val message: String) : Problem
    // The shop system said no for another reason; the reason in words.
    data class Failed(val message: String) : Problem
}

fun problemOf(r: ShopResult<*>): Problem? = when (r) {
    is ShopResult.Ok -> null
    is ShopResult.Refused -> if (r.code == "NOT_ALLOWED") Problem.NotAllowed else Problem.Failed(r.message)
    is ShopResult.NotSignedIn -> Problem.Failed(r.message)
    is ShopResult.Unreachable -> Problem.Offline(r.message)
}

class ReportsListState {
    var summary by mutableStateOf<ReportSummary?>(null)
    var problem by mutableStateOf<Problem?>(null)
    var loading by mutableStateOf(true)
    // Bumped by Try again (and on every open), to fetch again.
    var reload by mutableIntStateOf(0)
    val scroll = ScrollState(0)
}

sealed interface ReportSheet {
    data object Range : ReportSheet
    data class Export(
        val rows: String = "each",
        val preparing: Boolean = false,
        val failed: Boolean = false,
        // A file that finished while the app was in the background: shared by a tap, not by itself.
        val ready: File? = null,
    ) : ReportSheet
}

class ReportPageState(val key: String) {
    val snapshot: Boolean = key in setOf(ReportKeys.UNITS_BY_STATUS, ReportKeys.ASKING_PRICE_TOTAL, ReportKeys.TAGS_NOT_CONFIRMED)
    // The range asked for. The app sends the preset; the shop system sends back the dates.
    var range by mutableStateOf(RangeQuery.preset(Presets.THIS_WEEK))
    // The range the figures on screen are for (reverts if a new range fails to load).
    var shownRange by mutableStateOf<RangeQuery?>(null)
    var data by mutableStateOf<ReportDto?>(null)
    var problem by mutableStateOf<Problem?>(null)
    var loading by mutableStateOf(true)
    // The live region's words: "Updating to last week…", "Showing last week", "Couldn't load last week. Try again."
    var live by mutableStateOf<String?>(null)
    var refetchFailed by mutableStateOf(false)
    var by by mutableStateOf<String?>(null)
    var openColumn by mutableStateOf<Int?>(null)
    var showAll by mutableStateOf(false)
    var sheet by mutableStateOf<ReportSheet?>(null)
    var reload by mutableIntStateOf(0)
    val scroll = ScrollState(0)
}

// A drill-down list (§7): exactly the units the row counted, by its filter, sent back unchanged.
class DrillState(
    val report: String,
    val title: String,
    val filter: UnitFilter,
    // The report's range (null for snapshots), restated in the meta line.
    val range: RangeShown?,
) {
    var items by mutableStateOf<List<UnitDto>>(emptyList())
    var total by mutableStateOf<Long?>(null)
    var next by mutableStateOf<String?>(null)
    var problem by mutableStateOf<Problem?>(null)
    var loadingMore by mutableStateOf(false)
    var reload by mutableIntStateOf(0)
    val scroll = ScrollState(0)
}

// My history, for the signed-in person (personId null) or one person from Work by person.
class HistoryState(val personId: String?, val personName: String?, range: RangeQuery = RangeQuery.preset(Presets.THIS_WEEK)) {
    var range by mutableStateOf(range)
    var shownRange by mutableStateOf<RangeQuery?>(null)
    var page by mutableStateOf<HistoryPage?>(null)
    var events by mutableStateOf<List<HistoryEvent>>(emptyList())
    var problem by mutableStateOf<Problem?>(null)
    var loading by mutableStateOf(true)
    var loadingMore by mutableStateOf(false)
    var live by mutableStateOf<String?>(null)
    var refetchFailed by mutableStateOf(false)
    var rangeSheet by mutableStateOf(false)
    var reload by mutableIntStateOf(0)
    val scroll = ScrollState(0)
}

// Exported files live only in cacheDir/exports (reports spec §8): deleted after 24 hours at app
// start, and all of them whenever the signed-in person changes. Never in Downloads.
object ExportFiles {
    fun dir(context: Context) = File(context.cacheDir, "exports")

    fun pruneOld(context: Context, now: Long = System.currentTimeMillis()) {
        dir(context).listFiles()?.filter { now - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
    }

    fun wipe(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    // Called with the signed-in person whenever the shell starts or the person changes. The owner is
    // kept on disk, because signing out closes the shell: a different person finds no files left.
    fun claimFor(context: Context, person: String) {
        val owner = File(context.cacheDir, "exports-owner")
        val last = runCatching { owner.readText() }.getOrNull()
        if (last != person) { wipe(context); owner.writeText(person) } else pruneOld(context)
    }
}
