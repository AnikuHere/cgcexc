package ge.cgc.streamextractor

import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class LocalHlsServer(
    private val port: Int = 8080
) {
    private val executor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private val segments = ConcurrentHashMap<String, ByteArray>()
    private val playlistLock = Any()

    @Volatile
    private var playlist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:0\n"

    fun start() {
        if (serverSocket != null) return
        serverSocket = ServerSocket(null, 50, InetAddress.getByName("0.0.0.0")).also {
            it.reuseAddress = true
            it.bind(InetSocketAddress(port))
        }

        executor.submit {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    executor.submit { handle(socket) }
                } catch (_: IOException) {
                    break
                }
            }
        }
    }

    fun stop() {
        try { serverSocket?.close() } catch (_: Throwable) {}
        serverSocket = null
        executor.shutdownNow()
    }

    fun setPlaylist(value: String) {
        synchronized(playlistLock) {
            playlist = value
        }
    }

    fun addSegment(name: String, bytes: ByteArray) {
        segments[name] = bytes
    }

    fun removeSegment(name: String) {
        segments.remove(name)
    }

    fun localUrl(): String = "http://127.0.0.1:$port/stream/index.m3u8"

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 5000
            val reader = BufferedReader(
                InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1)
            )

            val request = reader.readLine() ?: return
            val parts = request.split(" ")
            if (parts.size < 2) return

            val rawPath = parts[1].substringBefore("?")
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            when {
                rawPath == "/" || rawPath == "/stream/" || rawPath == "/stream/index.m3u8" ->
                    respond(s, 200, "application/vnd.apple.mpegurl",
                        synchronized(playlistLock) { playlist }.toByteArray(StandardCharsets.UTF_8))

                rawPath.startsWith("/stream/") && rawPath.endsWith(".ts") -> {
                    val name = rawPath.substringAfterLast("/")
                    val data = segments[name]
                    if (data == null) {
                        respond(s, 404, "text/plain", "Not ready\n".toByteArray())
                    } else {
                        respond(s, 200, "video/mp2t", data)
                    }
                }

                else ->
                    respond(s, 404, "text/plain", "Not found\n".toByteArray())
            }
        }
    }

    private fun respond(
        socket: Socket,
        status: Int,
        contentType: String,
        body: ByteArray
    ) {
        val out = socket.getOutputStream()
        val header = buildString {
            append("HTTP/1.1 $status ${if (status == 200) "OK" else "Not Found"}\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-cache, no-store, must-revalidate\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.ISO_8859_1)

        out.write(header)
        out.write(body)
        out.flush()
    }
}
