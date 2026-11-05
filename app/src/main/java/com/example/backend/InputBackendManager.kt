package com.example.backend

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import com.example.BuildConfig
import com.example.IInputService
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class BackendType {
    SHIZUKU,
    ROOT
}

enum class BackendStatus {
    DISCONNECTED,
    CONNECTING,
    READY,
    PERMISSION_REQUIRED,
    NOT_RUNNING,
    ERROR
}

data class BackendState(
    val type: BackendType = BackendType.SHIZUKU,
    val status: BackendStatus = BackendStatus.DISCONNECTED,
    val message: String = "Inisialisasi...",
    val isReady: Boolean = false
)

class InputBackendManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "RightClickDash-Backend"
        private const val PREFS_NAME = "right_click_dash_prefs"
        private const val KEY_BACKEND = "pref_backend_type"
        private const val KEY_HOLD_DURATION = "pref_hold_duration"
        const val SHIZUKU_REQ_CODE = 1001

        const val BUTTON_LEFT = MotionEvent.BUTTON_PRIMARY // 1
        const val BUTTON_RIGHT = MotionEvent.BUTTON_SECONDARY // 2

        @Volatile
        private var instance: InputBackendManager? = null

        fun getInstance(context: Context): InputBackendManager {
            return instance ?: synchronized(this) {
                instance ?: InputBackendManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connectJob: Job? = null

    private val _backendState = MutableStateFlow(
        BackendState(
            type = loadSavedBackend(),
            status = BackendStatus.DISCONNECTED,
            message = "Belum terhubung"
        )
    )
    val backendState: StateFlow<BackendState> = _backendState.asStateFlow()

    @Volatile
    private var inputService: IInputService? = null

    private var shizukuListenerRegistered = false

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQ_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Shizuku permission granted")
                    connectShizuku()
                } else {
                    Log.w(TAG, "Shizuku permission denied")
                    _backendState.value = BackendState(
                        type = BackendType.SHIZUKU,
                        status = BackendStatus.PERMISSION_REQUIRED,
                        message = "Izin Shizuku ditolak oleh pengguna",
                        isReady = false
                    )
                }
            }
        }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received")
        if (backendState.value.type == BackendType.SHIZUKU) {
            connectShizuku()
        }
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder dead")
        if (backendState.value.type == BackendType.SHIZUKU) {
            inputService = null
            _backendState.value = BackendState(
                type = BackendType.SHIZUKU,
                status = BackendStatus.NOT_RUNNING,
                message = "Layanan Shizuku berhenti",
                isReady = false
            )
        }
    }

    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                inputService = IInputService.Stub.asInterface(binder)
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.READY,
                    message = "Shizuku siap & terhubung",
                    isReady = true
                )
                Log.i(TAG, "Connected to Shizuku UserService")
            } else {
                inputService = null
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.ERROR,
                    message = "Binder Shizuku tidak valid",
                    isReady = false
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            inputService = null
            _backendState.value = BackendState(
                type = BackendType.SHIZUKU,
                status = BackendStatus.DISCONNECTED,
                message = "Shizuku terputus",
                isReady = false
            )
            Log.w(TAG, "Shizuku UserService disconnected")
        }
    }

    private val rootConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                inputService = IInputService.Stub.asInterface(binder)
                _backendState.value = BackendState(
                    type = BackendType.ROOT,
                    status = BackendStatus.READY,
                    message = "Root siap & terhubung",
                    isReady = true
                )
                Log.i(TAG, "Connected to RootService")
            } else {
                inputService = null
                _backendState.value = BackendState(
                    type = BackendType.ROOT,
                    status = BackendStatus.ERROR,
                    message = "Binder Root tidak valid",
                    isReady = false
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            inputService = null
            _backendState.value = BackendState(
                type = BackendType.ROOT,
                status = BackendStatus.DISCONNECTED,
                message = "Root terputus",
                isReady = false
            )
            Log.w(TAG, "RootService disconnected")
        }
    }

    init {
        registerShizukuListeners()
        connectCurrentBackend()
    }

    private fun registerShizukuListeners() {
        if (!shizukuListenerRegistered) {
            try {
                Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
                Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
                Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
                shizukuListenerRegistered = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed registering Shizuku listeners: ${t.message}")
            }
        }
    }

    fun loadSavedBackend(): BackendType {
        val saved = prefs.getString(KEY_BACKEND, BackendType.SHIZUKU.name)
        return try {
            BackendType.valueOf(saved ?: BackendType.SHIZUKU.name)
        } catch (_: Exception) {
            BackendType.SHIZUKU
        }
    }

    fun getHoldDuration(): Long {
        return prefs.getLong(KEY_HOLD_DURATION, 800L)
    }

    fun setHoldDuration(durationMs: Long) {
        prefs.edit().putLong(KEY_HOLD_DURATION, durationMs.coerceIn(300L, 3000L)).apply()
    }

    fun switchBackend(targetType: BackendType) {
        if (_backendState.value.type == targetType && _backendState.value.isReady) {
            return
        }

        prefs.edit().putString(KEY_BACKEND, targetType.name).apply()
        disconnectCurrent()
        _backendState.value = BackendState(
            type = targetType,
            status = BackendStatus.CONNECTING,
            message = "Beralih ke $targetType...",
            isReady = false
        )
        connectCurrentBackend()
    }

    fun reconnect() {
        disconnectCurrent()
        connectCurrentBackend()
    }

    private fun disconnectCurrent() {
        connectJob?.cancel()
        inputService = null

        try {
            Shizuku.unbindUserService(
                Shizuku.UserServiceArgs(
                    ComponentName(appContext.packageName, ShizukuUserService::class.java.name)
                ).processNameSuffix("input_service"),
                shizukuConnection,
                true
            )
        } catch (_: Throwable) {}

        try {
            RootService.unbind(rootConnection)
        } catch (_: Throwable) {}
    }

    private fun connectCurrentBackend() {
        val type = loadSavedBackend()
        when (type) {
            BackendType.SHIZUKU -> connectShizuku()
            BackendType.ROOT -> connectRoot()
        }
    }

    private fun connectShizuku() {
        connectJob?.cancel()
        connectJob = scope.launch {
            _backendState.value = BackendState(
                type = BackendType.SHIZUKU,
                status = BackendStatus.CONNECTING,
                message = "Memeriksa Shizuku...",
                isReady = false
            )

            val ping = try {
                Shizuku.pingBinder()
            } catch (t: Throwable) {
                false
            }

            if (!ping) {
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.NOT_RUNNING,
                    message = "Shizuku belum berjalan",
                    isReady = false
                )
                return@launch
            }

            val hasPermission = try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (t: Throwable) {
                false
            }

            if (!hasPermission) {
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.PERMISSION_REQUIRED,
                    message = "Izin Shizuku belum diberikan",
                    isReady = false
                )
                return@launch
            }

            // Bind Shizuku UserService
            try {
                val args = Shizuku.UserServiceArgs(
                    ComponentName(appContext.packageName, ShizukuUserService::class.java.name)
                )
                    .daemon(false)
                    .processNameSuffix("input_service")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE)

                Shizuku.bindUserService(args, shizukuConnection)
            } catch (t: Throwable) {
                Log.e(TAG, "Error binding Shizuku: ${t.message}", t)
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.ERROR,
                    message = "Gagal menghubungkan: ${t.localizedMessage ?: "Error"}",
                    isReady = false
                )
            }
        }
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(SHIZUKU_REQ_CODE)
                }
            } else {
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.NOT_RUNNING,
                    message = "Buka app Shizuku dan aktifkan terlebih dahulu",
                    isReady = false
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Request permission failed: ${t.message}")
        }
    }

    private fun connectRoot() {
        connectJob?.cancel()
        connectJob = scope.launch {
            _backendState.value = BackendState(
                type = BackendType.ROOT,
                status = BackendStatus.CONNECTING,
                message = "Mengecek akses Root...",
                isReady = false
            )

            val isRoot = withContext(Dispatchers.IO) {
                try {
                    Shell.getShell().isRoot
                } catch (t: Throwable) {
                    false
                }
            }

            if (!isRoot) {
                _backendState.value = BackendState(
                    type = BackendType.ROOT,
                    status = BackendStatus.NOT_RUNNING,
                    message = "Akses Root (su) tidak terdeteksi",
                    isReady = false
                )
                return@launch
            }

            try {
                val intent = Intent(appContext, RootInputService::class.java)
                RootService.bind(intent, rootConnection)
            } catch (t: Throwable) {
                Log.e(TAG, "Error binding RootService: ${t.message}", t)
                _backendState.value = BackendState(
                    type = BackendType.ROOT,
                    status = BackendStatus.ERROR,
                    message = "Gagal memulai RootService: ${t.localizedMessage}",
                    isReady = false
                )
            }
        }
    }

    // Input actions
    fun click(x: Float, y: Float, button: Int = BUTTON_LEFT): Boolean {
        val service = inputService
        return if (service != null) {
            try {
                service.click(x, y, button)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "IPC click error: ${t.message}")
                false
            }
        } else {
            Log.w(TAG, "InputService not connected for click($x, $y, $button)")
            false
        }
    }

    fun hold(x: Float, y: Float, button: Int = BUTTON_RIGHT, durationMs: Long = getHoldDuration()): Boolean {
        val service = inputService
        return if (service != null) {
            try {
                service.hold(x, y, button, durationMs)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "IPC hold error: ${t.message}")
                false
            }
        } else {
            Log.w(TAG, "InputService not connected for hold($x, $y, $button, $durationMs)")
            false
        }
    }

    fun move(x: Float, y: Float): Boolean {
        val service = inputService
        return if (service != null) {
            try {
                service.move(x, y)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "IPC move error: ${t.message}")
                false
            }
        } else {
            false
        }
    }
}
