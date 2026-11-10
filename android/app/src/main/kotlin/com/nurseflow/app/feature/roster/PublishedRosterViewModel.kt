package com.nurseflow.app.feature.roster

import androidx.lifecycle.ViewModel
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class RosterMode { MINE, WARD }

data class MineDay(val date: LocalDate, val shiftCode: String, val timeLabel: String)
data class WardShiftGroup(val timeLabel: String, val names: List<String>, val unfilled: Int)
data class WardDay(val date: LocalDate, val groups: List<WardShiftGroup>)

sealed interface RosterContent {
    data object Loading : RosterContent
    /** Published roster exists; [days] may be empty when nothing is assigned to the user. */
    data class Mine(val days: List<MineDay>) : RosterContent
    data class Ward(val days: List<WardDay>) : RosterContent
    /** PUBLISHED_ROSTER_NOT_FOUND: nothing confirmed for the month yet. */
    data object NotPublished : RosterContent
    data object NurseIdentityRequired : RosterContent
    data object Forbidden : RosterContent
    data class Retryable(val message: String) : RosterContent
    data class Failed(val message: String) : RosterContent
}

data class RosterScreenState(val month: YearMonth, val mode: RosterMode, val content: RosterContent)

class PublishedRosterViewModel(
    private val api: NurseFlowApi,
    private val wardId: String,
    val canViewWard: Boolean,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    initialMonth: YearMonth = YearMonth.now(),
) : ViewModel(scope) {
    private val scope = scope
    private val mutable = MutableStateFlow(RosterScreenState(initialMonth, RosterMode.MINE, RosterContent.Loading))
    val state: StateFlow<RosterScreenState> = mutable.asStateFlow()
    private var request: Job? = null
    private var ticket = 0

    init { load() }

    fun selectMode(mode: RosterMode) {
        if (mode == RosterMode.WARD && !canViewWard) return
        if (mode == mutable.value.mode) return
        mutable.value = mutable.value.copy(mode = mode)
        load()
    }

    fun changeMonth(delta: Long) {
        mutable.value = mutable.value.copy(month = mutable.value.month.plusMonths(delta))
        load()
    }

    fun retry() = load()

    private var handledEntry: Int? = null

    /** Called once per user entry into this ward. The first entry is served by init; a re-entry refetches. */
    fun enter(token: Int) {
        if (token == handledEntry) return // recomposition or Activity recreation of the same entry
        val first = handledEntry == null
        handledEntry = token
        if (!first) load()
    }

    private fun load() {
        request?.cancel()
        val mine = ++ticket
        val target = mutable.value
        mutable.value = target.copy(content = RosterContent.Loading)
        request = scope.launch {
            val content = try {
                val month = target.month.toString()
                when (target.mode) {
                    RosterMode.MINE -> apiCall { api.publishedMine(wardId, month) }.toContent()
                    RosterMode.WARD -> apiCall { api.publishedWard(wardId, month) }.toContent()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                e.toContent()
            } catch (_: Exception) {
                RosterContent.Failed("근무표를 표시하지 못했어요.")
            }
            // A newer month/mode request owns the screen; drop this late result.
            if (mine == ticket) mutable.value = mutable.value.copy(content = content)
        }
    }

    private fun ApiException.toContent(): RosterContent = when {
        code == "PUBLISHED_ROSTER_NOT_FOUND" -> RosterContent.NotPublished
        code == "NURSE_IDENTITY_REQUIRED" -> RosterContent.NurseIdentityRequired
        status == 403 || code == "WARD_CALENDAR_VIEW_REQUIRED" -> RosterContent.Forbidden
        status == 0 || status in 500..599 -> RosterContent.Retryable(message)
        else -> RosterContent.Failed(message)
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun timeRange(start: String, end: String, zone: ZoneId): String =
    "${Instant.parse(start).atZone(zone).format(timeFormat)}–${Instant.parse(end).atZone(zone).format(timeFormat)}"

internal fun PublishedPersonalCalendarDto.toContent(): RosterContent {
    val zone = ZoneId.of(timeZone)
    return RosterContent.Mine(assignments.sortedBy { it.startsAt }.map {
        MineDay(LocalDate.parse(it.date), it.shiftType, timeRange(it.startsAt, it.endsAt, zone))
    })
}

/** Groups by date, then by shift type; shows time ranges only (no D/E/N guessing). */
internal fun PublishedWardCalendarDto.toContent(): RosterContent {
    val zone = ZoneId.of(timeZone)
    val days = slots.groupBy { LocalDate.parse(it.date) }.toSortedMap().map { (date, daySlots) ->
        val groups = daySlots.groupBy { it.shiftTypeId }.values
            .sortedBy { Instant.parse(it.first().startAt) }
            .map { group ->
                WardShiftGroup(
                    timeRange(group.first().startAt, group.first().endAt, zone),
                    group.mapNotNull { it.assignment?.displayName }.sorted(),
                    group.count { it.assignment == null },
                )
            }
        WardDay(date, groups)
    }
    return RosterContent.Ward(days)
}
