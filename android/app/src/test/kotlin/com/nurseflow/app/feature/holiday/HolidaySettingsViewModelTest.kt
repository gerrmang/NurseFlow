package com.nurseflow.app.feature.holiday

import com.nurseflow.app.core.network.*
import kotlinx.coroutines.*
import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

class HolidaySettingsViewModelTest {
    private var revision = 1
    private var admin = true
    private var valid = true
    private var failure: Exception? = null
    private var readFailure = false
    private var mixed = false
    private var changeAfterSave = false
    private var writes = 0
    private var expired = 0
    private var pause = false
    private var gate: Continuation<Any?>? = null
    private var capturedTag: String? = null
    private var days = emptyList<HolidayDay>()
    private var policy = HolidayPolicyInput(480, 480, 240, 480)
    private fun calendar(month: String) = HolidayCalendar(month, days, listOf(HolidayDay("$month-03", "PUBLIC_HOLIDAY", "가상 공휴일")), HolidaySource(true, null), HolidayTarget(month, 30, 9, 1, 0, 0, 10, 20, policy.dailyStandardMinutes, 20 * policy.dailyStandardMinutes), revision)
    private fun policy() = HolidayPolicy(policy.dailyStandardMinutes, policy.leaveCreditMinutes, policy.halfDayCreditMinutes, policy.sofCreditMinutes, policy.nightDedicatedTargetMinutes, policy.nightDedicatedNightCreditMinutes, revision)
    private fun <T> tagged(v: T, tag: Int = revision) = Response.success(v, Headers.headersOf("ETag", "\"p$tag\""))
    private val api = Proxy.newProxyInstance(HolidaySettingsApi::class.java.classLoader, arrayOf(HolidaySettingsApi::class.java)) { _, method, args ->
        try {
        when (method.name) {
            "calendar" -> { if (readFailure) throw ApiException(503, "UNAVAILABLE", "오류"); tagged(calendar(args!![1] as String)) }
            "policy" -> tagged(policy(), if (mixed) revision + 1 else revision)
            else -> {
                writes++; failure?.let { throw it }
                if (pause) { @Suppress("UNCHECKED_CAST")
                    gate = args!!.last() as Continuation<Any?>; COROUTINE_SUSPENDED
                } else {
                    when (method.name) {
                        "saveCalendar" -> { capturedTag = args!![2] as String; days = (args[3] as HolidayCalendarInput).days; revision++; tagged(calendar(args[1] as String)).also { if (changeAfterSave) revision++ } }
                        "savePolicy" -> { capturedTag = args!![1] as String; policy = args[2] as HolidayPolicyInput; revision++; tagged(policy()) }
                        "refresh" -> tagged(calendar(args!![1] as String))
                        else -> error("unexpected")
                    }
                }
            }
        }
        } catch (e: Exception) {
            @Suppress("UNCHECKED_CAST")
            (args!!.last() as Continuation<Any?>).resumeWith(Result.failure(e))
            COROUTINE_SUSPENDED
        }
    } as HolidaySettingsApi
    private val model = HolidaySettingsViewModel(api, "w1", { if (admin) listOf(MembershipDto("w1", "가상 병동", listOf("WARD_ADMIN"), true)) else emptyList() }, { valid }, { expired++ }, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    private fun open() { model.activate(); assertTrue(model.state.value.editable) }
    private fun day() = HolidayDay("${model.state.value.month}-03", "WARD_WORKDAY")
    @Test fun savesWholeWardListWithTagAndRetainsOtherDraft() {
        open(); model.editDay(day()); model.editPolicy(0, "420"); model.save("calendar")
        assertEquals("\"p1\"", capturedTag); assertEquals(listOf(day()), days)
        assertEquals("420", model.state.value.policy[0]); assertTrue(model.state.value.policyDirty)
        model.save("policy"); assertEquals("\"p2\"", capturedTag); assertEquals(420, policy.dailyStandardMinutes)
    }
    @Test fun policySaveAndRefreshKeepUnsavedCalendar() {
        open(); model.editDay(day()); model.editPolicy(4, "0"); model.save("policy")
        assertEquals(0, policy.nightDedicatedTargetMinutes); assertEquals(listOf(day()), model.state.value.days)
        model.save("refresh"); assertEquals(listOf(day()), model.state.value.days); assertTrue(model.state.value.calendarDirty)
    }
    @Test fun postSaveConcurrentChangeDoesNotRebaseUnsavedPolicy() {
        open(); model.editPolicy(0, "420"); changeAfterSave = true; model.save("calendar")
        assertTrue(model.state.value.stale); assertEquals("420", model.state.value.policy[0]); assertEquals("\"p1\"", model.state.value.etag)
        model.save("policy"); assertEquals(1, writes)
    }
    @Test fun refreshFailureKeepsCalendarAndDraftEditable() {
        open(); model.editDay(day()); val before = model.state.value.calendar
        failure = ApiException(503, "HOLIDAY_SOURCE_UNAVAILABLE", "오류"); model.save("refresh")
        assertTrue(model.state.value.editable); assertEquals(before, model.state.value.calendar); assertEquals(listOf(day()), model.state.value.days)
    }
    @Test fun conflictLocksAndKeepsInputWithoutRetry() {
        open(); model.editDay(day()); failure = ApiException(412, "STALE_POLICY_REVISION", "충돌"); model.save("calendar"); model.save("calendar")
        assertEquals(1, writes); assertTrue(model.state.value.stale); assertEquals(listOf(day()), model.state.value.days)
    }
    @Test fun validationRejectionKeepsDraftEditable() {
        open(); model.editPolicy(0, "420"); failure = ApiException(400, "INVALID_TARGET_HOUR_POLICY", "입력 오류"); model.save("policy")
        assertTrue(model.state.value.editable); assertEquals("420", model.state.value.policy[0])
    }
    @Test fun uncertainSaveRequiresReloadThenAcknowledgement() {
        open(); failure = ApiException(0, "NETWORK", "오류"); model.save("calendar"); model.save("calendar")
        assertEquals(1, writes); model.acknowledge(); assertFalse(model.state.value.editable)
        model.reload(); assertFalse(model.state.value.editable); model.acknowledge(); assertTrue(model.state.value.editable)
    }
    @Test fun revokeBeforeWriteClearsProtectedDataAndDoesNotSend() {
        open(); admin = false; model.save("calendar"); assertEquals(0, writes); assertTrue(model.state.value.forbidden); assertNull(model.state.value.calendar); assertTrue(model.state.value.policy.isEmpty())
    }
    @Test fun unauthorizedWriteEndsSessionAndDiscardsInput() {
        open(); failure = ApiException(401, "UNAUTHORIZED", "인증 오류"); model.save("calendar")
        assertEquals(1, expired); assertNull(model.state.value.calendar); assertTrue(model.state.value.days.isEmpty())
    }
    @Test fun rejectsMixedPolicyRevisionAndFailedReload() {
        mixed = true; model.activate(); assertFalse(model.state.value.editable)
        mixed = false; model.reload(); assertTrue(model.state.value.editable)
        readFailure = true; model.reload(); assertFalse(model.state.value.editable)
    }
    @Test fun inFlightSaveIsSerializedAndActivationDoesNotRestartIt() {
        open(); pause = true; model.save("calendar"); model.activate(); model.save("policy")
        assertEquals(1, writes); assertTrue(model.state.value.busy)
        pause = false; gate!!.resumeWith(Result.success(tagged(calendar(model.state.value.month))))
        assertFalse(model.state.value.busy); assertTrue(model.state.value.editable)
    }
    @Test fun lateResponseAfterLeavingCannotRestoreData() {
        open(); pause = true; model.save("calendar"); val oldMonth = model.state.value.month
        model.deactivate(); gate!!.resumeWith(Result.success(tagged(calendar(oldMonth))))
        assertNull(model.state.value.calendar)
    }
    @Test fun invalidPolicyNeverSendsAndNullAndZeroAreDistinct() {
        open(); model.editPolicy(0, ""); model.save("policy"); assertEquals(0, writes)
        assertNull(parseHolidayPolicy(listOf("0", "480", "240", "480", "", "")))
        assertNull(parseHolidayPolicy(listOf("480", "480.5", "240", "480", "", "")))
        assertNull(parseHolidayPolicy(listOf("480", "480", "240", "480", "44641", "")))
        assertEquals(null, parseHolidayPolicy(listOf("480", "480", "240", "480", "", ""))!!.nightDedicatedTargetMinutes)
        assertEquals(0, parseHolidayPolicy(listOf("480", "480", "240", "480", "0", ""))!!.nightDedicatedTargetMinutes)
    }
}
