package expo.modules.screenshare

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.Collections

class SignalingServer(
    private val onMessageReceived: (String) -> Unit,
    private val onNewConnection: () -> Unit
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val sessions = Collections.synchronizedSet(LinkedHashSet<DefaultWebSocketServerSession>())
    
    // Track if the server is in the process of shutting down
    @Volatile
    private var isStopping = false

    fun start(ip: String, htmlContent: String) {
        if (server != null) return
        isStopping = false

        server = embeddedServer(Netty, port = 8080) {
            install(WebSockets) {
                pingPeriodMillis = 15000L
                timeoutMillis = 30000L
            }
            
            routing {
                get("/") {
            // 2. Perform the replacement on it
            val dynamicHtml = htmlContent.replace(
                "WS_URL_PLACEHOLDER", 
                "ws://$ip:8080/signal"
            )
            
            // 3. Send the modified version (dynamicHtml) instead of the original
            call.respondText(dynamicHtml, ContentType.Text.Html)
        }

                webSocket("/signal") {
                    sessions.add(this)
                    onNewConnection()

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                onMessageReceived(frame.readText())
                            }
                        }
                    } catch (e: Exception) {
                        // Socket closure handled by finally block
                    } finally {
                        sessions.remove(this)
                    }
                }
            }
        }.start(wait = false)
    }

    fun broadcast(message: String) {
        // Don't attempt to broadcast if we are shutting down
        if (server == null || isStopping) return

        CoroutineScope(Dispatchers.IO).launch {
            val sessionsSnapshot = synchronized(sessions) { sessions.toList() }
            
            for (session in sessionsSnapshot) {
                try {
                    if (session.isActive && !session.outgoing.isClosedForSend) {
                        session.send(Frame.Text(message))
                    }
                } catch (e: Exception) {
                    // Session might have closed during iteration
                }
            }
        }
    }

    fun stop() {
        if (isStopping || server == null) return
        isStopping = true

        // Cleanly close all active WebSocket sessions before stopping the engine
        CoroutineScope(Dispatchers.IO).launch {
            val sessionsSnapshot = synchronized(sessions) { sessions.toList() }
            sessionsSnapshot.forEach { session ->
                try {
                    // Notify browser the signaling is done (Status 1001: Going Away)
                    session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Signaling completed"))
                } catch (e: Exception) { }
            }
            synchronized(sessions) { sessions.clear() }
            
            // Gracefully stop the Netty engine
            server?.stop(500, 1000)
            server = null
        }
    }
}