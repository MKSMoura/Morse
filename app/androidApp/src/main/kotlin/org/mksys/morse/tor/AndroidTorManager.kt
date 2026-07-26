package org.mksys.morse.tor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.freehaven.tor.control.TorControlCommands
import net.freehaven.tor.control.TorControlConnection
import org.mksys.morse.core.tor.TorManager
import org.torproject.jni.TorService
import java.io.File
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

class AndroidTorManager(
    private val context: Context
) : TorManager {

    companion object {
        private const val TAG = "AndroidTorManager"
        private const val HS_PORT = 9999
        private const val HS_VIRTUAL_PORT = 9995
        private const val BIND_TIMEOUT_MS = 15_000L
        private const val CONTROL_POLL_MS = 200L
    }

    private var torService: TorService? = null
    private var torControlConnection: TorControlConnection? = null
    private var bound = false
    private var onionAddress: String? = null
    private var serverSocket: ServerSocket? = null
    private var localPort = 0

    private val _bootstrapProgress = MutableStateFlow(0)
    override val bootstrapProgress: StateFlow<Int> = _bootstrapProgress.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var eventListenerJob: Job? = null

    private val hsDir: File by lazy {
        File(context.getDir("TorService", Context.MODE_PRIVATE), "hs_morse").also { it.mkdirs() }
    }

    private val torRcFile: File by lazy {
        File(context.getDir("TorService", Context.MODE_PRIVATE), "torrc")
    }

    private val torConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as TorService.LocalBinder
            torService = localBinder.getService()
            bound = true
            Log.i(TAG, "TorService connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            torService = null
            bound = false
            Log.i(TAG, "TorService disconnected")
        }
    }

    override suspend fun start() {
        _bootstrapProgress.value = 0
        onionAddress = null

        writeTorRc()

        val intent = Intent(context, TorService::class.java)
        context.bindService(intent, torConnection, Context.BIND_AUTO_CREATE)

        waitForTorService()

        waitForControlConnection()

        startEventListener()
    }

    override suspend fun stop() {
        eventListenerJob?.cancel()
        eventListenerJob = null

        try {
            torControlConnection?.shutdownTor(TorControlCommands.SIGNAL_SHUTDOWN)
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down Tor: ${e.message}")
        }

        if (bound) {
            context.unbindService(torConnection)
            bound = false
        }

        torService = null
        torControlConnection = null
        _bootstrapProgress.value = 0
        onionAddress = null
    }

    override suspend fun isReady(): Boolean {
        return _bootstrapProgress.value >= 100
    }

    override suspend fun getOnionAddress(): String? {
        if (onionAddress != null) return onionAddress
        val hostnameFile = File(hsDir, "hostname")
        if (hostnameFile.exists()) {
            onionAddress = hostnameFile.readText().trim()
            Log.i(TAG, "Onion address: $onionAddress")
        }
        return onionAddress
    }

    override fun getPort(): Int = HS_PORT

    val socksHost: String = "127.0.0.1"

    val socksPort: Int
        get() = torService?.socksPort ?: 9050

    fun startLocalServer(preferredPort: Int = 0): Int {
        if (serverSocket?.isBound == true) return localPort
        stopLocalServer()
        val portsToTry = if (preferredPort > 0) listOf(preferredPort, 0) else listOf(0)
        var lastError: Exception? = null
        for (p in portsToTry) {
            var ss: ServerSocket? = null
            try {
                ss = ServerSocket(p, 50, InetAddress.getByName("127.0.0.1"))
                ss.reuseAddress = true
                ss.soTimeout = 10000
                localPort = ss.localPort
                serverSocket = ss
                return localPort
            } catch (e: Exception) {
                ss?.close()
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("No available port")
    }

    fun stopLocalServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao fechar ServerSocket: ${e.message}")
        }
        serverSocket = null
    }

    fun restartLocalServer(): Int {
        stopLocalServer()
        return startLocalServer(HS_PORT)
    }

    fun getLocalPort(): Int = localPort

    suspend fun acceptConnection(): Socket? {
        val ss = serverSocket ?: return null
        return withContext(Dispatchers.IO) {
            try {
                ss.accept()
            } catch (e: SocketTimeoutException) {
                null
            } catch (e: Exception) {
                Log.w(TAG, "acceptConnection falhou: ${e.message}")
                null
            }
        }
    }

    suspend fun connectToOnion(onionAddress: String, port: Int): Socket {
        return withContext(Dispatchers.IO) {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
            val socket = Socket(proxy)
            socket.connect(InetSocketAddress.createUnresolved(onionAddress, port), 60000)
            socket
        }
    }

    private fun writeTorRc() {
        val hsDirPath = hsDir.absolutePath
        val defaults = StringBuilder().apply {
            append("HiddenServiceDir $hsDirPath\n")
            append("HiddenServicePort $HS_VIRTUAL_PORT 127.0.0.1:$HS_PORT\n")
        }
        PrintWriter(torRcFile).use { pw ->
            pw.append(defaults)
            pw.flush()
        }
        Log.i(TAG, "torrc written: $hsDirPath")
    }

    private suspend fun waitForTorService() {
        val start = System.currentTimeMillis()
        while (!bound) {
            if (System.currentTimeMillis() - start > BIND_TIMEOUT_MS) {
                throw Exception("Timeout aguardando TorService bind")
            }
            delay(CONTROL_POLL_MS)
        }
    }

    private suspend fun waitForControlConnection() {
        val start = System.currentTimeMillis()
        while (torService?.torControlConnection == null) {
            if (System.currentTimeMillis() - start > BIND_TIMEOUT_MS) {
                throw Exception("Timeout aguardando TorControlConnection")
            }
            delay(CONTROL_POLL_MS)
        }
        torControlConnection = torService?.torControlConnection
        Log.i(TAG, "TorControlConnection obtained")
    }

    private fun startEventListener() {
        eventListenerJob?.cancel()
        eventListenerJob = scope.launch {
            torControlConnection?.addRawEventListener { keyword, data ->
                if (keyword == TorControlCommands.EVENT_STATUS_CLIENT && data != null) {
                    val progress = parseBootstrapProgress(data)
                    if (progress >= 0) {
                        _bootstrapProgress.value = progress
                        Log.d(TAG, "Bootstrap: $progress% - $data")
                    }
                }
            }
        }
    }

    private fun parseBootstrapProgress(data: String): Int {
        val match = Regex("PROGRESS=(\\d+)").find(data)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }
}
