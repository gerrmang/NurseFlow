package com.nurseflow.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * In-process stand-in for 건강보험심사평가원 병원정보서비스 (`getHospBasisList`) on data.go.kr.
 * The search name selects the scenario. All institutions, ids and addresses are fictional.
 */
object FakeHiraServer {
    /** Matches the test config. Contains `+`, `/` and `=` like real keys, so an encoding bug breaks the key. */
    const val SERVICE_KEY = "nurseflow+test/hira=key=="

    const val TWO_HITS = "가상병원"
    const val ONE_HIT = "단일가상병원"
    const val NO_HIT = "없는가상병원"
    const val NO_DATA_CODE = "자료없음가상병원"
    const val SERVER_ERROR = "장애가상병원"
    const val SLOW = "지연가상병원"
    const val QUOTA_EXCEEDED = "한도초과가상병원"
    const val GATEWAY_JSON_ERROR = "게이트웨이JSON가상병원"
    const val NOT_JSON = "깨진응답가상병원"
    const val RESULT_ERROR = "결과오류가상병원"

    /** Decoded query parameters of the last request, to check what reached the provider. */
    val lastQuery = AtomicReference<Map<String, String>>(emptyMap())

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = Executors.newCachedThreadPool()
        createContext("/B551182/hospInfoServicev2/getHospBasisList") { exchange ->
            // Decode the raw query like a strict server: a raw `+` becomes a space and breaks the key.
            val params = exchange.requestURI.rawQuery.orEmpty().split("&").filter { it.isNotEmpty() }.associate {
                val (k, v) = it.split("=", limit = 2) + ""
                URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
            }
            lastQuery.set(params)
            // Real data.go.kr answers an unregistered key with 403 and a JSON OpenAPI_ServiceResponse.
            if (params["serviceKey"] != SERVICE_KEY) return@createContext exchange.reply(403, gatewayErrorJson(30))
            when (params["yadmNm"]) {
                TWO_HITS -> exchange.reply(200, normal(
                    """[${item("FAKE-YKIHO-0001", "가상병원", "서울특별시 가상구 가상로 1", "종합병원")},""" +
                        """${item("FAKE-YKIHO-0002", "가상병원", "부산광역시 가상구 바다로 2", "병원")}]""",
                    total = 2,
                ))
                ONE_HIT -> exchange.reply(200, normal(item("FAKE-YKIHO-0003", " 단일가상병원 ", "대구광역시 가상구 3", null), total = 1))
                NO_HIT -> exchange.reply(200, """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},""" +
                    """"body":{"items":"","numOfRows":10,"pageNo":1,"totalCount":0}}}""")
                NO_DATA_CODE -> exchange.reply(200, """{"response":{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"}}}""")
                SERVER_ERROR -> exchange.reply(500, "internal error")
                SLOW -> {
                    Thread.sleep(2_000)
                    exchange.reply(200, normal("[]", total = 0))
                }
                QUOTA_EXCEEDED -> exchange.reply(200, gatewayError(22), xml = true)
                GATEWAY_JSON_ERROR -> exchange.reply(200, gatewayErrorJson(22))
                NOT_JSON -> exchange.reply(200, "this is not json")
                RESULT_ERROR -> exchange.reply(200, """{"response":{"header":{"resultCode":"99","resultMsg":"UNKNOWN_ERROR"}}}""")
                else -> exchange.reply(200, normal("""[${item("FAKE-YKIHO-9999", params["yadmNm"].orEmpty(), "가상 주소", "의원")}]""", total = 1))
            }
        }
        start()
    }

    val baseUri: String get() = "http://127.0.0.1:${server.address.port}/B551182/hospInfoServicev2"

    private fun item(ykiho: String, name: String, address: String, category: String?) =
        """{"ykiho":"$ykiho","yadmNm":"$name","addr":"$address"""" +
            (category?.let { ""","clCdNm":"$it"""" } ?: "") + ""","telno":"02-000-0000"}"""

    private fun normal(items: String, total: Int) =
        """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},""" +
            """"body":{"items":{"item":$items},"numOfRows":10,"pageNo":1,"totalCount":$total}}}"""

    private fun gatewayError(reason: Int) =
        "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>" +
            "<returnReasonCode>$reason</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>"

    private fun gatewayErrorJson(reason: Int) =
        """{
  "OpenAPI_ServiceResponse": {
    "cmmMsgHeader": {
      "errMsg": "SERVICE ERROR",
""" +
            """      "returnReasonCode": "$reason"
    }
  }
}"""

    private fun HttpExchange.reply(status: Int, body: String, xml: Boolean = false) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", if (xml) "text/xml;charset=UTF-8" else "application/json;charset=UTF-8")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
