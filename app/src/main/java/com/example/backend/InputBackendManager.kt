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
        const val TAG = "RCD"
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
    private var checkJob: Job? = null

    private val _backendState = MutableStateFlow(
        BackendState(
            type = loadSavedBackend(),
            status = BackendStatus.DISCONNECTED,
            message = "Inisialisasi backend...",
            isReady = false
        )
    )
    val backendState: StateFlow<BackendState> = _backendState.asStateFlow()

    @Volatile
    private var inputService: IInputService? = null

    @Volatile
    var activeDragButton: Int = 0
        private set

    private var shizukuListenerRegistered = false

    // 1. Shizuku Listeners
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            Log.d(TAG, "OnRequestPermissionResultListener: requestCode=$requestCode, grantResult=$grantResult")
            if (requestCode == SHIZUKU_REQ_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Izin Shizuku DIBERIKAN oleh pengguna! Memulai koneksi service...")
                    checkShizukuStatusAndConnect()
                } else {
                    Log.w(TAG, "Izin Shizuku DITOLAK oleh pengguna")
                    _backendState.value = BackendState(
                        type = BackendType.SHIZUKU,
                        status = BackendStatus.PERMISSION_REQUIRED,
                        message = "Izin belum diberikan",
                        isReady = false
                    )
                }
            }
        }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "OnBinderReceivedListener terpanggil! Shizuku binder aktif, cek ulang status...")
        if (backendState.value.type == BackendType.SHIZUKU) {
            checkShizukuStatusAndConnect()
        }
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "OnBinderDeadListener terpanggil! Layanan Shizuku mati/berhenti.")
        if (activeDragButton != 0) {
            activeDragButton = 0
        }
        inputService = null
        if (backendState.value.type == BackendType.SHIZUKU) {
            _backendState.value = BackendState(
                type = BackendType.SHIZUKU,
                status = BackendStatus.NOT_RUNNING,
                message = "Shizuku belum berjalan",
                isReady = false
            )
        }
    }

    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val ping = binder?.pingBinder() == true
            Log.d(TAG, "shizukuConnection.onServiceConnected: component=$name, pingBinder=$ping")
            if (binder != null && ping) {
                inputService = IInputService.Stub.asInterface(binder)
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.READY,
                    message = "READY (shizuku is connect)",
                    isReady = true
                )
                Log.i(TAG, "Shizuku UserService terhubung sukses! Status: Siap (READY)")
            } else {
                inputService = null
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.ERROR,
                    message = "Binder Shizuku tidak valid",
                    isReady = false
                )
                Log.e(TAG, "shizukuConnection.onServiceConnected: binder null atau pingBinder=false")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "shizukuConnection.onServiceDisconnected: $name")
            if (activeDragButton != 0) {
                activeDragButton = 0
            }
            inputService = null
            if (backendState.value.type == BackendType.SHIZUKU) {
                checkShizukuStatusAndConnect()
            }
        }
    }

    private val rootConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val ping = binder?.pingBinder() == true
            Log.d(TAG, "rootConnection.onServiceConnected: component=$name, pingBinder=$ping")
            if (binder != null && ping) {
                inputService = IInputService.Stub.asInterface(binder)
                _backendState.value = BackendState(
                    type = BackendType.ROOT,
                    status = BackendStatus.READY,
                    message = "READY (root is connect)",
                    isReady = true
                )
                Log.i(TAG, "RootService terhubung sukses! Status: Siap (READY)")
            } else {
                inputService = null
                _backendState.value = BackendState(
                    type = BackendType.ROOT,
                    status = BackendStatus.ERROR,
                    message = "Binder Root tidak valid",
                    isReady = false
                )
                Log.e(TAG, "rootConnection.onServiceConnected: binder null atau pingBinder=false")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "rootConnection.onServiceDisconnected: $name")
            if (activeDragButton != 0) {
                activeDragButton = 0
            }
            inputService = null
            if (backendState.value.type == BackendType.ROOT) {
                connectRoot()
            }
        }
    }

    init {
        registerShizukuListeners()
        recheckStatus()
    }

    fun registerShizukuListeners() {
        if (!shizukuListenerRegistered) {
            try {
                Log.d(TAG, "Mendaftarkan Shizuku listeners (Sticky binder, dead listener, permission listener)...")
                Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
                Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
                Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
                shizukuListenerRegistered = true
                Log.i(TAG, "Shizuku listeners berhasil didaftarkan secara sticky.")
            } catch (t: Throwable) {
                Log.e(TAG, "Gagal mendaftarkan Shizuku listeners: ${t.message}", t)
            }
        }
    }

    fun unregisterShizukuListeners() {
        if (shizukuListenerRegistered) {
            try {
                Log.d(TAG, "Melepas Shizuku listeners...")
                Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
                Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
                Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
                shizukuListenerRegistered = false
                Log.i(TAG, "Shizuku listeners berhasil dilepas.")
            } catch (t: Throwable) {
                Log.e(TAG, "Gagal melepas Shizuku listeners: ${t.message}", t)
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
        Log.i(TAG, "switchBackend: beralih ke $targetType")
        prefs.edit().putString(KEY_BACKEND, targetType.name).apply()
        disconnectCurrent()
        _backendState.value = BackendState(
            type = targetType,
            status = BackendStatus.CONNECTING,
            message = "MENGHUBUNGKAN...",
            isReady = false
        )
        if (targetType == BackendType.SHIZUKU) {
            registerShizukuListeners()
            checkShizukuStatusAndConnect()
        } else {
            connectRoot()
        }
    }

    fun reconnect() {
        recheckStatus()
    }

    /**
     * Tombol "Cek Ulang" memanggil fungsi ini untuk mengevaluasi ulang status backend aktif.
     */
    fun recheckStatus() {
        val type = loadSavedBackend()
        Log.i(TAG, "recheckStatus() dipanggil untuk backend: $type")
        when (type) {
            BackendType.SHIZUKU -> {
                registerShizukuListeners()
                checkShizukuStatusAndConnect()
            }
            BackendType.ROOT -> {
                connectRoot()
            }
        }
    }

    private fun disconnectCurrent() {
        checkJob?.cancel()
        if (activeDragButton != 0) {
            emergencyRelease(0f, 0f)
        }
        inputService = null

        try {
            Log.d(TAG, "Memutus UserService Shizuku jika terikat...")
            val args = Shizuku.UserServiceArgs(
                ComponentName(appContext.packageName, ShizukuUserService::class.java.name)
            ).processNameSuffix("input_service")
            Shizuku.unbindUserService(args, shizukuConnection, true)
        } catch (t: Throwable) {
            Log.d(TAG, "unbindUserService note: ${t.message}")
        }

        try {
            Log.d(TAG, "Memutus RootService jika terikat...")
            RootService.unbind(rootConnection)
        } catch (t: Throwable) {
            Log.d(TAG, "unbind RootService note: ${t.message}")
        }
    }

    /**
     * Menghitung status Shizuku dengan urutan:
     * a) Shizuku.pingBinder() == false -> "SHIZUKU BELUM BERJALAN"
     * b) Shizuku.checkSelfPermission() != PERMISSION_GRANTED -> "IZIN SHIZUKU BELUM DIBERIKAN"
     * c) Izin ada tetapi UserService belum terhubung -> "MENGHUBUNGKAN..." lalu bindUserService
     * d) Semua siap -> "READY (shizuku is connect)" (hijau)
     */
    private fun checkShizukuStatusAndConnect() {
        checkJob?.cancel()
        checkJob = scope.launch {
            Log.d(TAG, "[Shizuku Check] Langkah 1: Memeriksa Shizuku.pingBinder()...")
            val isPingAlive = try {
                val ping = Shizuku.pingBinder()
                Log.d(TAG, "[Shizuku Check] pingBinder() = $ping")
                ping
            } catch (t: Throwable) {
                Log.e(TAG, "[Shizuku Check] pingBinder() throw exception: ${t.message}", t)
                false
            }

            // a) Shizuku.pingBinder() == false
            if (!isPingAlive) {
                Log.w(TAG, "[Shizuku Check] Hasil: Shizuku belum berjalan")
                inputService = null
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.NOT_RUNNING,
                    message = "SHIZUKU BELUM BERJALAN",
                    isReady = false
                )
                return@launch
            }

            // b) Shizuku.checkSelfPermission()
            Log.d(TAG, "[Shizuku Check] Langkah 2: Memeriksa Shizuku.checkSelfPermission()...")
            val hasPermission = try {
                val checkResult = Shizuku.checkSelfPermission()
                val granted = checkResult == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "[Shizuku Check] checkSelfPermission() = $checkResult (granted=$granted)")
                granted
            } catch (t: Throwable) {
                Log.e(TAG, "[Shizuku Check] checkSelfPermission() throw exception: ${t.message}", t)
                false
            }

            if (!hasPermission) {
                Log.w(TAG, "[Shizuku Check] Hasil: Izin belum diberikan")
                inputService = null
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.PERMISSION_REQUIRED,
                    message = "IZIN SHIZUKU BELUM DIBERIKAN",
                    isReady = false
                )
                return@launch
            }

            // c & d) Izin ada: cek apakah service sudah terhubung
            val service = inputService
            if (service != null && service.asBinder().pingBinder()) {
                Log.i(TAG, "[Shizuku Check] Hasil: Semua siap! (READY)")
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.READY,
                    message = "READY (shizuku is connect)",
                    isReady = true
                )
                return@launch
            }

            // c) UserService belum terhubung -> "MENGHUBUNGKAN..." lalu bindUserService
            Log.i(TAG, "[Shizuku Check] Langkah 3: Menghubungkan service via Shizuku.bindUserService...")
            _backendState.value = BackendState(
                type = BackendType.SHIZUKU,
                status = BackendStatus.CONNECTING,
                message = "MENGHUBUNGKAN...",
                isReady = false
            )

            try {
                val args = Shizuku.UserServiceArgs(
                    ComponentName(appContext.packageName, ShizukuUserService::class.java.name)
                )
                    .daemon(false)
                    .processNameSuffix("input_service")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE)

                Log.d(TAG, "Menjalankan Shizuku.bindUserService untuk ShizukuUserService...")
                Shizuku.bindUserService(args, shizukuConnection)
                Log.d(TAG, "Shizuku.bindUserService selesai dieksekusi, menunggu onServiceConnected...")
            } catch (t: Throwable) {
                Log.e(TAG, "[Shizuku Check] bindUserService error: ${t.message}", t)
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.ERROR,
                    message = "ERROR: ${t.localizedMessage ?: "Gagal konek"}",
                    isReady = false
                )
            }
        }
    }

    /**
     * Meminta izin Shizuku
     */
    fun requestShizukuPermission() {
        Log.i(TAG, "requestShizukuPermission() dipanggil...")
        try {
            val ping = Shizuku.pingBinder()
            Log.d(TAG, "requestShizukuPermission: pingBinder = $ping")
            if (ping) {
                val check = try {
                    Shizuku.checkSelfPermission()
                } catch (t: Throwable) {
                    Log.e(TAG, "checkSelfPermission error: ${t.message}", t)
                    -1
                }
                Log.d(TAG, "requestShizukuPermission: checkSelfPermission = $check")
                if (check != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Memanggil Shizuku.requestPermission(reqCode=$SHIZUKU_REQ_CODE)...")
                    Shizuku.requestPermission(SHIZUKU_REQ_CODE)
                } else {
                    Log.i(TAG, "Izin sudah diberikan! Langsung menghubungkan service...")
                    checkShizukuStatusAndConnect()
                }
            } else {
                Log.w(TAG, "requestShizukuPermission gagal karena pingBinder = false")
                _backendState.value = BackendState(
                    type = BackendType.SHIZUKU,
                    status = BackendStatus.NOT_RUNNING,
                    message = "SHIZUKU BELUM BERJALAN",
                    isReady = false
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal meminta izin Shizuku: ${t.message}", t)
            _backendState.value = BackendState(
                type = BackendType.SHIZUKU,
                status = BackendStatus.ERROR,
                message = "ERROR",
                isReady = false
            )
        }
    }

    private fun connectRoot() {
        checkJob?.cancel()
        checkJob = scope.launch {
            Log.i(TAG, "[Root Check] Mengecek akses Root (su)...")
            _backendState.value = BackendState(
                type = BackendType.ROOT,
                status = BackendStatus.CONNECTING,
                message = "MENGHUBUNGKAN...",
                isReady = false
            )

            val isRoot = withContext(Dispatchers.IO) {
                try {
                    val root = Shell.getShell().isRoot
                    Log.d(TAG, "[Root Check] Shell.getShell().isRoot = $root")
                    root
                } catch (t: Throwable) {
                    Log.e(TAG, "[Root Check] Shell error: ${t.message}", t)
                    false
                }
            }

            if (!isRoot) {
                Log.w(TAG, "[Root Check] Akses root tidak terdeteksi")
                inputService = null
                _backendState.value = BackendState(
                    type = BackendType.ROOT,
                    status = BackendStatus.NOT_RUNNING,
                    message = "ROOT TIDAK TERDETEKSI",
                    isReady = false
                )
                return@launch
            }

            val service = inputService
            if (service != null && service.asBinder().pingBinder()) {
                Log.i(TAG, "[Root Check] RootService sudah terhubung! Status: READY")
                _backendState.value = BackendState(
                    type = BackendType.ROOT,
                    status = BackendStatus.READY,
                    message = "READY (root is connect)",
                    isReady = true
                )
                return@launch
            }

            Log.i(TAG, "[Root Check] Menghubungkan RootService...")
            _backendState.value = BackendState(
                type = BackendType.ROOT,
                status = BackendStatus.CONNECTING,
                message = "MENGHUBUNGKAN...",
                isReady = false
            )

            try {
                val intent = Intent(appContext, RootInputService::class.java)
                Log.d(TAG, "Memanggil RootService.bind...")
                RootService.bind(intent, rootConnection)
            } catch (t: Throwable) {
                Log.e(TAG, "[Root Check] Error binding RootService: ${t.message}", t)
                _backendState.value = BackendState(
                    type = BackendType.ROOT,
                    status = BackendStatus.ERROR,
                    message = "ERROR",
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
                Log.d(TAG, "click($x, $y, button=$button) dikirim ke InputService")
                service.click(x, y, button)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "IPC click error: ${t.message}", t)
                false
            }
        } else {
            Log.w(TAG, "InputService belum terhubung saat click($x, $y, $button)")
            false
        }
    }

    fun hold(x: Float, y: Float, button: Int = BUTTON_RIGHT, durationMs: Long = getHoldDuration()): Boolean {
        val service = inputService
        return if (service != null) {
            try {
                Log.d(TAG, "hold($x, $y, button=$button, dur=${durationMs}ms) dikirim ke InputService")
                service.hold(x, y, button, durationMs)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "IPC hold error: ${t.message}", t)
                false
            }
        } else {
            Log.w(TAG, "InputService belum terhubung saat hold($x, $y, $button, $durationMs)")
            false
        }
    }

    fun pressDown(x: Float, y: Float, button: Int): Boolean {
        activeDragButton = button
        val service = inputService
        return if (service != null) {
            try {
                Log.d(TAG, "pressDown($x, $y, button=$button) dikirim ke InputService")
                service.pressDown(x, y, button)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "IPC pressDown error: ${t.message}", t)
                false
            }
        } else {
            Log.w(TAG, "InputService belum terhubung saat pressDown($x, $y, $button)")
            false
        }
    }

    fun moveHeld(x: Float, y: Float, button: Int = activeDragButton): Boolean {
        val targetButton = if (button != 0) button else activeDragButton
        val service = inputService
        return if (service != null) {
            try {
                service.moveHeld(x, y, targetButton)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "IPC moveHeld error: ${t.message}", t)
                false
            }
        } else {
            false
        }
    }

    fun release(x: Float, y: Float, button: Int = activeDragButton): Boolean {
        val targetButton = if (button != 0) button else activeDragButton
        activeDragButton = 0
        val service = inputService
        return if (service != null) {
            try {
                Log.d(TAG, "release($x, $y, button=$targetButton) dikirim ke InputService")
                service.release(x, y, targetButton)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "IPC release error: ${t.message}", t)
                false
            }
        } else {
            Log.w(TAG, "InputService belum terhubung saat release($x, $y, $targetButton)")
            false
        }
    }

    fun emergencyRelease(x: Float = 0f, y: Float = 0f): Boolean {
        val btn = activeDragButton
        Log.i(TAG, "emergencyRelease dipanggil (held button was $btn)")
        val targetBtn = if (btn != 0) btn else BUTTON_LEFT
        return release(x, y, targetBtn)
    }

    fun move(x: Float, y: Float): Boolean {
        if (activeDragButton != 0) {
            return moveHeld(x, y, activeDragButton)
        }
        val service = inputService
        return if (service != null) {
            try {
                service.move(x, y)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "IPC move error: ${t.message}", t)
                false
            }
        } else {
            false
        }
    }

    fun scroll(x: Float, y: Float, notches: Int): Boolean {
        val service = inputService
        return if (service != null) {
            try {
                Log.d(TAG, "scroll($x, $y, notches=$notches) dikirim ke InputService")
                service.scroll(x, y, notches)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "IPC scroll error: ${t.message}", t)
                false
            }
        } else {
            Log.w(TAG, "InputService belum terhubung saat scroll($x, $y, $notches)")
            false
        }
    }
}
