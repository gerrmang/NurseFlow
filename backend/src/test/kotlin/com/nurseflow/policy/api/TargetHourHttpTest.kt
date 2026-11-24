package com.nurseflow.policy.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class TargetHourHttpTest : ApiTestBase() {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `policy defaults to 8 hours, is admin only and refuses stale or non-integer writes`() {
        val ward = insertWard()
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val path = "/api/v1/wards/$ward/target-hour-policy"
        val initial = http.get(path, token)
        assertEquals(200, initial.status, initial.body)
        assertEquals(480, initial.json!!.get("dailyStandardMinutes").asInt())
        assertEquals(240, initial.json!!.get("halfDayCreditMinutes").asInt())
        assertTrue(initial.json!!.get("nightDedicatedTargetMinutes").isNull)
        assertEquals("\"policy-0\"", initial.header("ETag"))
        val tag = mapOf("If-Match" to initial.header("ETag")!!)
        val defaults = mapOf("dailyStandardMinutes" to 480, "leaveCreditMinutes" to 480, "halfDayCreditMinutes" to 240, "sofCreditMinutes" to 480)
        assertEquals("\"policy-0\"", http.put(path, defaults, token, tag).header("ETag"))
        assertEquals("INVALID_TARGET_HOUR_POLICY", http.put(path, defaults - "sofCreditMinutes", token, tag).code)
        val body = mapOf(
            "dailyStandardMinutes" to 450, "leaveCreditMinutes" to 480, "halfDayCreditMinutes" to 240,
            "sofCreditMinutes" to 480, "nightDedicatedTargetMinutes" to 9000,
        )
        assertEquals(403, http.get(path, memberToken).status)
        assertEquals(403, http.put(path, body, memberToken, tag).status)
        assertEquals("INVALID_TARGET_HOUR_POLICY", http.put(path, body + ("dailyStandardMinutes" to 0), token, tag).code)
        assertEquals("INVALID_TARGET_HOUR_POLICY", http.put(path, body + ("dailyStandardMinutes" to 450.5), token, tag).code)
        assertEquals("INVALID_TARGET_HOUR_POLICY", http.put(path, body + ("leaveCreditMinutes" to "480"), token, tag).code)
        assertEquals("INVALID_TARGET_HOUR_POLICY", http.put(path, body + ("nightDedicatedTargetMinutes" to -1), token, tag).code)
        val saved = http.put(path, body, token, tag)
        assertEquals(200, saved.status, saved.body)
        assertEquals("\"policy-1\"", saved.header("ETag"))
        assertEquals(9000, saved.json!!.get("nightDedicatedTargetMinutes").asInt())
        assertEquals(412, http.put(path, body + ("dailyStandardMinutes" to 480), token, tag).status)
        val next = mapOf("If-Match" to saved.header("ETag")!!)
        assertEquals(saved.header("ETag"), http.put(path, body, token, next).header("ETag"))
        assertEquals(450, http.get(path, token).json!!.get("dailyStandardMinutes").asInt())
    }

    @Test
    fun `calendar replaces one month, counts weekend holidays once and validates entries`() {
        val ward = insertWard()
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val path = "/api/v1/wards/$ward/holiday-calendar?month=2026-11"
        val initial = http.get(path, token)
        assertEquals(200, initial.status, initial.body)
        val target = initial.json!!.get("target")
        assertEquals(30, target.get("daysInMonth").asInt())
        assertEquals(9, target.get("restDays").asInt())
        assertEquals(168 * 60, target.get("generalTargetMinutes").asInt())
        val tag = mapOf("If-Match" to initial.header("ETag")!!)
        fun day(date: String, kind: String, name: String? = null) = mapOf("date" to date, "kind" to kind, "name" to name)
        fun body(vararg days: Map<String, String?>) = mapOf("days" to days.toList())
        assertEquals(403, http.get(path, memberToken).status)
        assertEquals("INVALID_MONTH", http.get("/api/v1/wards/$ward/holiday-calendar?month=2026-13", token).code)
        assertEquals("INVALID_HOLIDAY_CALENDAR", http.put(path, body(day("2026-12-01", "PUBLIC_HOLIDAY")), token, tag).code)
        assertEquals("INVALID_HOLIDAY_CALENDAR",
            http.put(path, body(day("2026-11-09", "PUBLIC_HOLIDAY"), day("2026-11-09", "WARD_REST_DAY")), token, tag).code)
        assertEquals("INVALID_HOLIDAY_CALENDAR", http.put(path, body(day("2026-11-09", "HOLIDAY")), token, tag).code)
        val saved = http.put(path, body(day("2026-11-07", "PUBLIC_HOLIDAY", "가상 공휴일"), day("2026-11-09", "SUBSTITUTE_HOLIDAY")), token, tag)
        assertEquals(200, saved.status, saved.body)
        assertEquals(10, saved.json!!.get("target").get("restDays").asInt())
        assertEquals(20 * 480, saved.json!!.get("target").get("generalTargetMinutes").asInt())
        assertEquals(2, saved.json!!.get("days").size())
        assertEquals(412, http.put(path, body(), token, tag).status)
        val december = http.get("/api/v1/wards/$ward/holiday-calendar?month=2026-12", token)
        assertEquals(0, december.json!!.get("days").size())
        assertEquals(saved.header("ETag"), december.header("ETag"))
    }

    @Test
    fun `imported public holidays count in the target, a ward entry wins and a failed fetch keeps what is stored`() {
        val ward = insertWard()
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val path = "/api/v1/wards/$ward/holiday-calendar"
        val before = http.get("$path?month=2026-09", token).json!!
        assertTrue(before.get("holidaySource").get("configured").asBoolean())
        assertTrue(before.get("holidaySource").get("fetchedAt").isNull)
        assertEquals(8, before.get("target").get("restDays").asInt())
        assertEquals(403, http.post("$path/refresh?month=2026-09", null, memberToken).status)

        val refreshed = http.post("$path/refresh?month=2026-09", null, token)
        assertEquals(200, refreshed.status, refreshed.body)
        val holidays = refreshed.json!!.get("publicHolidays")
        // Four days off: Sep 24, 25 (two names), 26 (Saturday) and the substitute on Sep 28. Sep 29 is not a day off.
        assertEquals(listOf("2026-09-24", "2026-09-25", "2026-09-26", "2026-09-28"), holidays.toList().map { it.get("date").asString() })
        assertEquals("가상 명절, 가상 기념일", holidays[1].get("name").asString())
        assertEquals("SUBSTITUTE_HOLIDAY", holidays[3].get("kind").asString())
        assertFalse(refreshed.json!!.get("holidaySource").get("fetchedAt").isNull)
        // Weekdays Sep 24, 25 and 28 add rest days; Sep 26 is a Saturday and counts once.
        assertEquals(11, refreshed.json!!.get("target").get("restDays").asInt())
        assertEquals(3, refreshed.json!!.get("target").get("holidayDays").asInt())

        // The ward works the substitute holiday: its entry replaces the imported one for this ward only.
        val tag = mapOf("If-Match" to refreshed.header("ETag")!!)
        val own = http.put("$path?month=2026-09", mapOf("days" to listOf(mapOf("date" to "2026-09-28", "kind" to "WARD_WORKDAY"))), token, tag)
        assertEquals(200, own.status, own.body)
        assertEquals(10, own.json!!.get("target").get("restDays").asInt())
        assertEquals(1, own.json!!.get("target").get("wardWorkdays").asInt())
        val otherWard = insertWard()
        grant(otherWard, admin, "WARD_ADMIN")
        assertEquals(11, http.get("/api/v1/wards/$otherWard/holiday-calendar?month=2026-09", token).json!!.get("target").get("restDays").asInt())

        val single = http.post("$path/refresh?month=2026-10", null, token).json!!
        assertEquals("2026-10-05", single.get("publicHolidays").single().get("date").asString())

        jdbc.update("INSERT INTO public_holidays(holiday_date,kind,name) VALUES(DATE '2026-12-25','PUBLIC_HOLIDAY','가상 성탄')")
        val failed = http.post("$path/refresh?month=2026-12", null, token)
        assertEquals(503, failed.status, failed.body)
        assertEquals("HOLIDAY_SOURCE_UNAVAILABLE", failed.code)
        assertEquals(1, http.get("$path?month=2026-12", token).json!!.get("publicHolidays").size())
    }

    @Test
    fun `diagnostics report each nurse's hours with month-end nights counted in the next month`() {
        val ward = insertWard()
        jdbc.update("UPDATE wards SET demo_mode=TRUE WHERE ward_id=?", ward)
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val general = insertNurse(ward, "가상 간호사 1")
        val dedicated = insertNurse(ward, "가상 간호사 2")
        val idle = insertNurse(ward, "가상 간호사 3")
        insertNurse(ward, "가상 간호사 4", active = false)
        jdbc.update("UPDATE nurses SET work_pattern='NIGHT_ONLY' WHERE nurse_id=?", dedicated)
        val day = shiftType(ward, "D", "DAY", LocalTime.of(7, 0), LocalTime.of(15, 0), false, breakMinutes = 30, credit = 480)
        val night = shiftType(ward, "N", "NIGHT", LocalTime.of(23, 0), LocalTime.of(7, 0), true, breakMinutes = 0, credit = 540)
        val october = roster(ward, "2026-10", "PUBLISHED")
        val november = roster(ward, "2026-11", "DRAFT")
        slot(october, night, LocalDate.of(2026, 10, 31), LocalTime.of(23, 0), 8, dedicated)
        val weekdays = (1..30).map { LocalDate.of(2026, 11, it) }.filter { it.dayOfWeek.value <= 5 }
        weekdays.forEach { slot(november, day, it, LocalTime.of(7, 0), 8, general) }
        (1..9).forEach { slot(november, night, LocalDate.of(2026, 11, it), LocalTime.of(23, 0), 8, dedicated) }
        slot(november, night, LocalDate.of(2026, 11, 30), LocalTime.of(23, 0), 8, dedicated)

        // Approved leave stored on the published October roster runs into Monday Nov 2 (Nov 1 is a Sunday).
        jdbc.update(
            "INSERT INTO approved_absences(absence_id,roster_id,nurse_id,kind,starts_at,ends_at) VALUES(?,?,?,'APPROVED_LEAVE',?,?)",
            UUID.randomUUID(), october, idle, LocalDate.of(2026, 10, 30).atStartOfDay(zone).toInstant().toString(),
            LocalDate.of(2026, 11, 3).atStartOfDay(zone).toInstant().toString(),
        )
        val path = "/api/v1/rosters/$november/hour-diagnostics"
        assertEquals(403, http.get(path, memberToken).status)
        val response = http.get(path, token)
        assertEquals(200, response.status, response.body)
        val json = response.json!!
        assertEquals(168 * 60, json.get("target").get("generalTargetMinutes").asInt())
        assertEquals("END_DATE_MONTH", json.get("nightAttribution").asString())
        assertTrue(json.get("previousMonthPublished").asBoolean())
        val items = json.get("items").associateBy { it.get("nurseId").asString() }
        assertEquals(3, items.size)
        val g = items.getValue(general.toString())
        assertEquals(21, g.get("shiftCount").asInt())
        assertEquals(21 * 450, g.get("actualMinutes").asInt())
        assertEquals(21 * 480, g.get("creditMinutes").asInt())
        assertEquals("MET", g.get("status").asString())
        val d = items.getValue(dedicated.toString())
        assertEquals(10, d.get("shiftCount").asInt())
        assertEquals(1, d.get("carriedInShifts").asInt())
        assertEquals(1, d.get("carriedOutShifts").asInt())
        assertEquals(10 * 540, d.get("creditMinutes").asInt())
        assertEquals(168 * 60 - 10 * 540, d.get("shortfallMinutes").asInt())
        assertEquals("SHORTFALL", d.get("status").asString())
        val i = items.getValue(idle.toString())
        assertEquals(1, i.get("leaveDays").asInt())
        assertEquals(480, i.get("creditMinutes").asInt())
        assertEquals(480 - 168 * 60, i.get("remainingMinutes").asInt())
        assertFalse(items.containsKey(UUID.randomUUID().toString()))

        val tag = mapOf("If-Match" to http.get("/api/v1/wards/$ward/target-hour-policy", token).header("ETag")!!)
        http.put("/api/v1/wards/$ward/target-hour-policy", mapOf(
            "dailyStandardMinutes" to 480, "leaveCreditMinutes" to 480, "halfDayCreditMinutes" to 240,
            "sofCreditMinutes" to 480, "nightDedicatedTargetMinutes" to 10 * 600, "nightDedicatedNightCreditMinutes" to 600,
        ), token, tag)
        val after = http.get(path, token).json!!.get("items").associateBy { it.get("nurseId").asString() }.getValue(dedicated.toString())
        assertEquals(10 * 600, after.get("creditMinutes").asInt())
        assertEquals(10 * 600, after.get("targetMinutes").asInt())
        assertEquals("MET", after.get("status").asString())
        assertEquals(404, http.get("/api/v1/rosters/${UUID.randomUUID()}/hour-diagnostics", token).status)
    }

    private fun shiftType(
        ward: UUID, code: String, kind: String, start: LocalTime, end: LocalTime, spans: Boolean, breakMinutes: Int, credit: Int,
    ): UUID = UUID.randomUUID().also {
        jdbc.update(
            """INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight,break_minutes,
                target_credit_minutes) VALUES(?,?,?,?,?,?,?,?,?,?)""",
            it, ward, code, code, kind, start, end, spans, breakMinutes, credit,
        )
    }

    private fun roster(ward: UUID, month: String, status: String): UUID = UUID.randomUUID().also {
        jdbc.update(
            "INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,?,?,1)",
            it, ward, month, status, status == "PUBLISHED",
        )
    }

    private fun slot(roster: UUID, type: UUID, date: LocalDate, start: LocalTime, hours: Long, nurse: UUID) {
        val startsAt = date.atTime(start).atZone(zone).toInstant()
        jdbc.update(
            "INSERT INTO roster_slots(slot_id,roster_id,shift_date,shift_type_id,starts_at,ends_at,nurse_id) VALUES(?,?,?,?,?,?,?)",
            UUID.randomUUID(), roster, date, type, startsAt.toString(), startsAt.plusSeconds(hours * 3600).toString(), nurse,
        )
    }
}
