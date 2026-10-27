package com.alvinb.counterpos.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Small Supabase Realtime v1 client used as an invalidation channel.
 * Database writes still go through the authenticated REST/RPC outbox.
 */
class SupabaseRealtimeClient(
    private val baseUrl: String,
    private val publishableKey: String,
    private val accessToken: () -> String?,
    private val onDataChanged: () -> Unit
) {
    private val http = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val scheduler = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "counter-pos-realtime").apply { isDaemon = true }
    }
    private val stopped = AtomicBoolean(true)
    private var socket: WebSocket? = null
    private var heartbeat: ScheduledFuture<*>? = null
    private var reconnect: ScheduledFuture<*>? = null
    private var shopId: String? = null
    private var ref = 0L
    private var retryAttempt = 0

    @Synchronized
    fun start(shopId: String) {
        if (this.shopId == shopId && !stopped.get()) return
        stop()
        this.shopId = shopId
        stopped.set(false)
        retryAttempt = 0
        connect()
    }

    @Synchronized
    fun stop() {
        stopped.set(true)
        shopId = null
        heartbeat?.cancel(false)
        heartbeat = null
        reconnect?.cancel(false)
        reconnect = null
        socket?.close(1000, "switching business")
        socket = null
    }

    private fun connect() {
        val activeShop = shopId ?: return
        if (stopped.get()) return
        val token = accessToken() ?: return
        val wsBase = baseUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val request = Request.Builder()
            .url("$wsBase/realtime/v1/websocket?apikey=$publishableKey&vsn=1.0.0")
            .build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                retryAttempt = 0
                val postgresChanges = JSONArray().apply {
                    listOf(
                        "products", "sales", "stock_batches", "stock_receipts",
                        "dining_tables", "table_orders", "service_counters",
                        "counter_stock_balances", "counter_stock_movements",
                        "table_bill_settlements"
                    ).forEach { table ->
                        put(JSONObject()
                            .put("event", "*")
                            .put("schema", "public")
                            .put("table", table)
                            .put("filter", "shop_id=eq.$activeShop"))
                    }
                }
                val payload = JSONObject()
                    .put("config", JSONObject()
                        .put("broadcast", JSONObject().put("ack", false).put("self", false))
                        .put("presence", JSONObject().put("enabled", false))
                        .put("postgres_changes", postgresChanges))
                    .put("access_token", token)
                send(webSocket, "realtime:counter-pos-$activeShop", "phx_join", payload)
                heartbeat?.cancel(false)
                heartbeat = scheduler.scheduleAtFixedRate({
                    if (!stopped.get()) send(webSocket, "phoenix", "heartbeat", JSONObject())
                }, 25, 25, TimeUnit.SECONDS)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                val event = message.optString("event")
                if (event == "postgres_changes") onDataChanged()
                if (event == "phx_error" || event == "phx_close") scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scheduleReconnect()
            }
        })
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (stopped.get() || shopId == null || reconnect?.isDone == false) return
        heartbeat?.cancel(false)
        heartbeat = null
        val delay = (1L shl retryAttempt.coerceAtMost(5)).coerceAtMost(30L)
        retryAttempt++
        reconnect = scheduler.schedule({
            synchronized(this) {
                reconnect = null
                if (!stopped.get()) connect()
            }
        }, delay, TimeUnit.SECONDS)
    }

    private fun send(socket: WebSocket, topic: String, event: String, payload: JSONObject) {
        ref++
        socket.send(JSONObject()
            .put("topic", topic)
            .put("event", event)
            .put("payload", payload)
            .put("ref", ref.toString())
            .toString())
    }
}
