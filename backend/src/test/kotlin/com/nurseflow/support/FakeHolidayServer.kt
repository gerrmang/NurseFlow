package com.nurseflow.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-process stand-in for 한국천문연구원 특일 정보 `getRestDeInfo` on data.go.kr. The month selects the scenario:
 * 2026-09 several holidays (one not a day off, one substitute, two names on one date), 2026-10 a single object
 * with a numeric date, 2026-12 a server error, anything else no holidays. The dates are test data, not a real calendar.
 */
object FakeHolidayServer {
    const val SERVICE_KEY = "nurseflow+test/holiday=key=="

    /** Requests received, to check that a recent fetch is reused. */
    val calls = AtomicInteger()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = Executors.newCachedThreadPool()
        createContext("/B090041/openapi/service/SpcdeInfoService/getRestDeInfo") { exchange ->
            val params = exchange.requestURI.rawQuery.orEmpty().split("&").filter { it.isNotEmpty() }.associate {
                val (k, v) = it.split("=", limit = 2) + ""
                URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
            }
            calls.incrementAndGet()
            if (params["serviceKey"] != SERVICE_KEY) {
                return@createContext exchange.reply(403, """{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"returnReasonCode":"30"}}}""")
            }
            when (params["solYear"] + "-" + params["solMonth"]) {
                "2026-09" -> exchange.reply(200, normal(
                    """[{"dateKind":"01","dateName":"가상 명절","isHoliday":"Y","locdate":20260924,"seq":1},""" +
                        """{"dateKind":"01","dateName":"대체공휴일(가상 기념일)","isHoliday":"Y","locdate":20260925,"seq":1},""" +
                        """{"dateKind":"01","dateName":"가상 명절","isHoliday":"Y","locdate":20260925,"seq":2},""" +
                        """{"dateKind":"01","dateName":"가상 명절","isHoliday":"Y","locdate":20260926,"seq":1},""" +
                        """{"dateKind":"01","dateName":"대체공휴일(가상 명절)","isHoliday":"Y","locdate":20260928,"seq":1},""" +
                        """{"dateKind":"01","dateName":"쉬지 않는 날","isHoliday":"N","locdate":20260929,"seq":1}]""",
                    count = 6,
                ))
                "2026-10" -> exchange.reply(200, normal("""{"dateKind":"01","dateName":"가상 국경일","isHoliday":"Y","locdate":20261005,"seq":1}"""))
                "2026-12" -> exchange.reply(500, "internal error")
                else -> exchange.reply(200, """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},""" +
                    """"body":{"items":"","numOfRows":50,"pageNo":1,"totalCount":0}}}""")
            }
        }
        start()
    }

    val baseUri: String get() = "http://127.0.0.1:${server.address.port}/B090041/openapi/service/SpcdeInfoService"

    private fun normal(item: String, count: Int = 1) = """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},""" +
        """"body":{"items":{"item":$item},"numOfRows":50,"pageNo":1,"totalCount":$count}}}"""

    private fun HttpExchange.reply(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json;charset=UTF-8")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
