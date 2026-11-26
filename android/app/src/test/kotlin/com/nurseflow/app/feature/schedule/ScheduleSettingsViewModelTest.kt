package com.nurseflow.app.feature.schedule

import com.nurseflow.app.core.network.*
import kotlinx.coroutines.*
import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

private val shift = ScheduleShift("s1", "D", "가상 Day", "DAY", "07:00", "15:00", false, 0, 480, 480, 480, true)
private val input = ScheduleShiftInput("D", "가상 Day", "DAY", "07:00", "15:00", false, 0, 480, 420)
private val nurse = AdminStaff("n1", "가상 간호사", emptyList(), true, false, AdminCareerProfile("MISSING"), seniorEligible = true)

class ScheduleSettingsViewModelTest {
    private var revision = 1
    private var rosterReads = 0
    private var drafts = true
    private var assigned = 0
    private var admin = true
    private var valid = true
    private var expired = 0
    private var writes = 0
    private var pattern = "GENERAL"
    private var patternFailure = false
    private var shiftedSnapshot = false
    private var noTag = false
    private var failure: Exception? = null
    private var gate: Continuation<Any?>? = null
    private var pauseWrite = false
    private var captured: Any? = null
    private var draftWard = "w1"
    private fun roster() = ScheduleRoster("r1", draftWard, "2026-11", "DRAFT", revision, "Asia/Seoul", ScheduleStaffing(1, assigned, 1 - assigned, 0, 1), listOf(ScheduleSlot("slot", "2026-11-01", "s1", "D", if (assigned == 0) "UNFILLED" else "FILLED", "t1", true)))
    private fun <T> tagged(value: T) = Response.success(value, Headers.headersOf(*if (noTag) emptyArray() else arrayOf("ETag", "\"$revision\"")))
    private val api = Proxy.newProxyInstance(ScheduleSettingsApi::class.java.classLoader, arrayOf(ScheduleSettingsApi::class.java)) { _, method, args ->
        try {
            when (method.name) {
                "shifts" -> tagged(ScheduleShifts(listOf(shift), 1))
                "teams" -> Response.success(Items(listOf(AdminTeam("t1", "가상 A팀", 1))))
                "staff" -> Response.success(Items(listOf(nurse)))
                "rosters" -> Response.success(Items(if (drafts) listOf(roster()) else emptyList()))
                "roster" -> { rosterReads++; if (shiftedSnapshot && rosterReads % 2 == 0) revision++; tagged(roster()) }
                "absences" -> Response.success(Items<ScheduleAbsence>(emptyList()))
                "pattern" -> { if (patternFailure) throw ApiException(503, "UNAVAILABLE", "일시 오류"); Response.success(SchedulePattern(pattern)) }
                "preview" -> Response.success(SchedulePreview(shift, 1, true, true, listOf(ScheduleAffectedDraft("2026-11", 1, 0)), 0))
                else -> {
                    writes++; captured = args!!.dropLast(1).last()
                    failure?.let { throw it }
                    if (pauseWrite) {
                        @Suppress("UNCHECKED_CAST")
                        gate = args.last() as Continuation<Any?>
                        COROUTINE_SUSPENDED
                    } else {
                        revision++
                        when (method.name) {
                            "createShift", "updateShift", "deactivateShift" -> tagged(shift)
                            "createRoster", "requirements" -> { drafts = true; tagged(roster()) }
                            "savePattern" -> { pattern = (captured as SchedulePattern).workPattern; Response.success(SchedulePattern(pattern)) }
                            "createAbsence", "updateAbsence" -> { val i = captured as ScheduleAbsenceInput; tagged(ScheduleAbsence("a1", i.nurseId, i.kind, i.startsAt, i.endsAt)) }
                            "deleteAbsence" -> Response.success<Unit>(204, null)
                            else -> error("unexpected ${method.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            @Suppress("UNCHECKED_CAST")
            (args!!.last() as Continuation<Any?>).resumeWith(Result.failure(e))
            COROUTINE_SUSPENDED
        }
    } as ScheduleSettingsApi
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val model = ScheduleSettingsViewModel(api, "w1", { if (admin) listOf(MembershipDto("w1", "가상 병동", listOf("WARD_ADMIN"), true)) else emptyList() }, { valid }, { expired++ }, scope)
    private fun open() { model.setMonth("2026-11"); model.activate(); assertTrue(model.state.value.editable) }
    @Test fun loadsCoherentDraftAndPolicyWithSeniorFields() {
        open(); assertEquals("\"1\"", model.state.value.data!!.draft!!.etag); assertEquals(2, rosterReads)
        assertTrue(model.state.value.data!!.draft!!.value.slots!!.single().seniorRequired)
    }
    @Test fun invalidMonthKeepsCurrentSnapshotAndShowsInputError() {
        open(); val reads = rosterReads
        model.setMonth("2026-13")
        assertEquals("2026-11", model.state.value.month); assertEquals(reads, rosterReads)
        assertTrue(model.state.value.notice!!.contains("YYYY-MM")); assertTrue(model.state.value.editable)
    }
    @Test fun rejectsMixedRevisionAbsenceSnapshot() {
        shiftedSnapshot = true; model.setMonth("2026-11"); model.activate()
        assertTrue(model.state.value.stale); assertFalse(model.state.value.editable)
    }
    @Test fun missingEtagAndWrongWardNeverEnableWriting() {
        noTag = true; model.activate(); assertFalse(model.state.value.editable)
        noTag = false; draftWard = "other"; model.reload(); assertFalse(model.state.value.editable)
    }
    @Test fun requiresPreviewForExactInputAndClearsItAfterSave() {
        open(); model.saveShift("s1", input); assertEquals(0, writes)
        model.preview("s1", input); assertNotNull(model.state.value.preview)
        model.saveShift("s1", input.copy(targetCreditMinutes = 400)); assertEquals(0, writes)
        model.saveShift("s1", input); assertEquals(1, writes); assertNull(model.state.value.preview)
        assertTrue(model.state.value.editable)
    }
    @Test fun uncertainWriteBlocksReplayUntilExplicitResultAcknowledgement() {
        open(); failure = ApiException(0, "NETWORK", "연결 오류")
        model.saveShift(null, input); assertEquals(1, writes); assertTrue(model.state.value.uncertain)
        model.saveShift(null, input); assertEquals(1, writes)
        model.acknowledge(); assertTrue(model.state.value.editable)
    }
    @Test fun staleWriteReloadsAndDoesNotAutomaticallyRetry() {
        open(); failure = ApiException(412, "STALE_POLICY_REVISION", "충돌")
        model.saveShift(null, input); assertEquals(1, writes); assertTrue(model.state.value.editable)
        assertTrue(model.state.value.notice!!.contains("다른 화면"))
    }
    @Test fun adminLossBeforeMutationDiscardsAllDataAndSendsNothing() {
        open(); admin = false; model.createDraft() // Existing draft: no-op.
        model.saveShift(null, input); assertEquals(0, writes); assertTrue(model.state.value.forbidden); assertNull(model.state.value.data)
    }
    @Test fun unauthorizedMutationEndsSessionAndDiscardsData() {
        open(); failure = ApiException(401, "UNAUTHORIZED", "로그인 필요")
        model.saveShift(null, input); assertEquals(1, expired); assertNull(model.state.value.data)
    }
    @Test fun serializationAndServerFailuresAreUncertain() {
        open(); failure = kotlinx.serialization.SerializationException("가상 응답 오류")
        model.saveShift(null, input); assertTrue(model.state.value.uncertain)
    }
    @Test fun simultaneousWritesAndMonthChangesCannotRace() {
        open(); pauseWrite = true; model.saveShift(null, input)
        assertTrue(model.state.value.busy); model.setMonth("2026-12"); model.saveShift(null, input)
        assertEquals("2026-11", model.state.value.month); assertEquals(1, writes)
        gate!!.resumeWith(Result.success(tagged(shift))); assertFalse(model.state.value.busy)
    }
    @Test fun leavingDuringWriteRetainsUncertaintyAndIgnoresLateResponse() {
        open(); pauseWrite = true; model.saveShift(null, input); model.deactivate()
        gate!!.resumeWith(Result.success(tagged(shift)))
        assertNull(model.state.value.data); assertTrue(model.state.value.uncertain)
        model.activate(); assertFalse(model.state.value.editable); model.acknowledge(); assertTrue(model.state.value.editable)
    }
    @Test fun sessionChangeSuppressesLateResponse() {
        open(); pauseWrite = true; model.saveShift(null, input); valid = false
        model.deactivate(); gate!!.resumeWith(Result.success(tagged(shift))); assertNull(model.state.value.data)
    }
    @Test fun assignedDraftCannotReplaceRequirements() {
        assigned = 1; open(); model.replaceRequirements(emptyList()); assertEquals(0, writes)
    }
    @Test fun replacesEntireDemandIncludingExplicitEmptyList() {
        open(); model.replaceRequirements(emptyList()); assertEquals(ScheduleRequirements(emptyList()), captured)
        assertEquals(1, writes); assertTrue(model.state.value.editable)
    }
    @Test fun missingDraftCanBeExplicitlyCreatedAndReloaded() {
        drafts = false; open(); assertNull(model.state.value.data!!.draft)
        model.createDraft(); assertNotNull(model.state.value.data!!.draft); assertEquals(1, writes)
    }
    @Test fun patternReadFailureNeverAssumesGeneralAndCanRecover() {
        open(); patternFailure = true; model.selectNurse("n1"); assertNull(model.state.value.pattern)
        model.savePattern("NIGHT_ONLY"); assertEquals(0, writes)
        patternFailure = false; model.selectNurse("n1"); assertEquals("GENERAL", model.state.value.pattern)
        model.savePattern("NIGHT_ONLY"); assertEquals("NIGHT_ONLY", model.state.value.pattern); assertTrue(model.state.value.editable)
    }
    @Test fun absenceWritePreservesOriginalNanosecondsAndOffsets() {
        open(); val i = ScheduleAbsenceInput("n1", "APPROVED_HALF_DAY", "2026-11-01T09:00:30.123456789+09:00", "2026-11-01T04:00:30.987654321Z")
        model.saveAbsence(null, i); assertEquals(i, captured); assertEquals(1, writes)
    }
}
