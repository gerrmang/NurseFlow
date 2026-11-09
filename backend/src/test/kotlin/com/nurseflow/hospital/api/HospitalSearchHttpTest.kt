package com.nurseflow.hospital.api

import com.nurseflow.support.ApiTestBase
import com.nurseflow.support.FakeHiraServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLEncoder

class HospitalSearchHttpTest : ApiTestBase() {
    private fun search(query: String?, extra: String = "", token: String? = userToken().second) =
        http.get("/api/v1/hospitals/search?" + (query?.let { "query=" + URLEncoder.encode(it, Charsets.UTF_8) } ?: "") + extra, token)

    @Test
    fun `search requires login`() {
        assertEquals(401, search(FakeHiraServer.TWO_HITS, token = null).status)
    }

    @Test
    fun `same named hospitals are returned with address category and source`() {
        val response = search(FakeHiraServer.TWO_HITS, "&page=2&size=5")

        assertEquals(200, response.status, response.body)
        assertEquals("private, no-store", response.header("Cache-Control"))
        val body = response.json!!
        assertEquals(2, body.get("page").asInt())
        assertEquals(5, body.get("size").asInt())
        assertEquals(2, body.get("totalCount").asInt())
        assertEquals("건강보험심사평가원 병원정보서비스", body.get("attribution").asString())
        val items = body.get("items")
        assertEquals(2, items.size())
        assertEquals("HIRA", items[0].get("source").asString())
        assertEquals("FAKE-YKIHO-0001", items[0].get("externalId").asString())
        assertEquals("가상병원", items[0].get("name").asString())
        assertEquals("서울특별시 가상구 가상로 1", items[0].get("address").asString())
        assertEquals("종합병원", items[0].get("category").asString())
        assertEquals("병원", items[1].get("category").asString())
        assertFalse(items[0].has("telno"), "only the fields a ward needs are returned")

        val sent = FakeHiraServer.lastQuery.get()
        assertEquals("2", sent["pageNo"])
        assertEquals("5", sent["numOfRows"])
        assertEquals("json", sent["_type"])
    }

    @Test
    fun `a single hit object is normalized like a list`() {
        val response = search(FakeHiraServer.ONE_HIT)

        assertEquals(200, response.status, response.body)
        val item = response.json!!.get("items")[0]
        assertEquals("단일가상병원", item.get("name").asString())
        assertTrue(item.get("category").isNull)
    }

    @Test
    fun `no results is an empty list, not an error`() {
        listOf(FakeHiraServer.NO_HIT, FakeHiraServer.NO_DATA_CODE).forEach { query ->
            val response = search(query)
            assertEquals(200, response.status, response.body)
            assertEquals(0, response.json!!.get("items").size())
            assertEquals(0, response.json!!.get("totalCount").asInt())
        }
    }

    @Test
    fun `provider failures are reported as unavailable without leaking the key`() {
        listOf(
            FakeHiraServer.SERVER_ERROR,
            FakeHiraServer.SLOW,
            FakeHiraServer.QUOTA_EXCEEDED,
            FakeHiraServer.GATEWAY_JSON_ERROR,
            FakeHiraServer.NOT_JSON,
            FakeHiraServer.RESULT_ERROR,
        ).forEach { query ->
            val response = search(query)
            assertEquals(503, response.status, "$query: ${response.body}")
            assertEquals("HOSPITAL_SEARCH_UNAVAILABLE", response.code, query)
            assertFalse(response.body.contains(FakeHiraServer.SERVICE_KEY), "$query response must not echo the key")
        }
    }

    @Test
    fun `query is trimmed and special characters reach the provider intact`() {
        assertEquals(200, search("  ${FakeHiraServer.TWO_HITS}  ").status)
        assertEquals(FakeHiraServer.TWO_HITS, FakeHiraServer.lastQuery.get()["yadmNm"])

        val special = "가상&병원=1+%"
        val response = search(special)
        assertEquals(200, response.status, response.body)
        assertEquals(special, FakeHiraServer.lastQuery.get()["yadmNm"])
    }

    @Test
    fun `invalid parameters are rejected before calling the provider`() {
        val invalid = listOf(
            search(null),
            search("가"),
            search("   가   "),
            search("가".repeat(51)),
            search("가상\u0000병원"),
            search(FakeHiraServer.TWO_HITS, "&page=0"),
            search(FakeHiraServer.TWO_HITS, "&page=101"),
            search(FakeHiraServer.TWO_HITS, "&size=21"),
            search(FakeHiraServer.TWO_HITS, "&size=abc"),
        )
        invalid.forEach { response ->
            assertEquals(400, response.status, response.body)
            assertEquals("INVALID_REQUEST", response.code, response.body)
        }
        assertEquals(200, search("가".repeat(50)).status)
    }
}
