package com.miyamizu.player.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * USB DACデバイスの接続・パーミッション・記述子取得を管理。
 * UsbManager経由でfdと生記述子を取得し、ネイティブエンジンに渡す。
 */
class UsbAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbAudioManager"
        private const val ACTION_USB_PERMISSION = "com.miyamizu.player.USB_PERMISSION"

        // よく使われるUSB DACのVID/PID（参考用）
        private val KNOWN_DACS = mapOf(
            0x054c to "Sony",           // Sony
            0x0d8c to "C-Media",        // C-Media
            0x1852 to "Burr-Brown",     // TI/Burr-Brown
            0x1854 to "Burr-Brown",     // TI/Burr-Brown
            0x08bb to "Texas Instruments", // TI
            0x245f to "XMOS",           // XMOS-based DACs
            0x20b1 to "XMOS",           // XMOS-based DACs
            0x20b2 to "XMOS",           // XMOS-based DACs
            0x20b3 to "XMOS",           // XMOS-based DACs
            0x20b4 to "XMOS",           // XMOS-based DACs
            0x20b5 to "XMOS",           // XMOS-based DACs
            0x20b6 to "XMOS",           // XMOS-based DACs
            0x20b7 to "XMOS",           // XMOS-based DACs
            0x20b8 to "XMOS",           // XMOS-based DACs
            0x20b9 to "XMOS",           // XMOS-based DACs
            0x20ba to "XMOS",           // XMOS-based DACs
            0x20bb to "XMOS",           // XMOS-based DACs
            0x20bc to "XMOS",           // XMOS-based DACs
            0x20bd to "XMOS",           // XMOS-based DACs
            0x20be to "XMOS",           // XMOS-based DACs
            0x20bf to "XMOS",           // XMOS-based DACs
            0x05a7 to "Bose",           // Bose
            0x057f to "Harman/Kardon",  // Harman
            0x21ab to "FiiO",           // FiiO
            0x2965 to "FiiO",           // FiiO
            0x2966 to "FiiO",           // FiiO
            0x292a to "iFi",            // iFi Audio
            0x292b to "iFi",            // iFi Audio
            0x292c to "iFi",            // iFi Audio
            0x292d to "iFi",            // iFi Audio
            0x2a52 to "Topping",        // Topping
            0x2a53 to "Topping",        // Topping
            0x2a39 to "Topping",        // Topping
            0x2a3a to "Topping",        // Topping
            0x2a3b to "Topping",        // Topping
            0x2a3c to "Topping",        // Topping
            0x2a3d to "Topping",        // Topping
            0x2a3e to "Topping",        // Topping
            0x2a3f to "Topping",        // Topping
            0x3496 to "Audioengine",    // Audioengine
            0x23a5 to "Cambridge Audio", // Cambridge
            0x23a6 to "Cambridge Audio", // Cambridge
            0x23a7 to "Cambridge Audio", // Cambridge
            0x23a8 to "Cambridge Audio", // Cambridge
            0x23a9 to "Cambridge Audio", // Cambridge
            0x23aa to "Cambridge Audio", // Cambridge
            0x23ab to "Cambridge Audio", // Cambridge
            0x23ac to "Cambridge Audio", // Cambridge
            0x23ad to "Cambridge Audio", // Cambridge
            0x23ae to "Cambridge Audio", // Cambridge
            0x23af to "Cambridge Audio", // Cambridge
            0x1a34 to "Amanero",        // Amanero (USB Audio 2.0)
            0x1a88 to "Amanero",        // Amanero
            0x1a89 to "Amanero",        // Amanero
            0x1a8a to "Amanero",        // Amanero
            0x1a8b to "Amanero",        // Amanero
            0x1a8c to "Amanero",        // Amanero
            0x1a8d to "Amanero",        // Amanero
            0x1a8e to "Amanero",        // Amanero
            0x1a8f to "Amanero",        // Amanero
            0x1b03 to "Amanero",        // Amanero
            0x1b13 to "Amanero",        // Amanero
            0x1b1c to "Amanero",        // Amanero
            0x1b3c to "Amanero",        // Amanero
            0x1b3d to "Amanero",        // Amanero
            0x1b3e to "Amanero",        // Amanero
            0x1b3f to "Amanero",        // Amanero
            0x1b40 to "Amanero",        // Amanero
            0x1b41 to "Amanero",        // Amanero
            0x1b42 to "Amanero",        // Amanero
            0x1b43 to "Amanero",        // Amanero
            0x1b44 to "Amanero",        // Amanero
            0x1b45 to "Amanero",        // Amanero
            0x1b46 to "Amanero",        // Amanero
            0x1b47 to "Amanero",        // Amanero
            0x1b48 to "Amanero",        // Amanero
            0x1b49 to "Amanero",        // Amanero
            0x1b4a to "Amanero",        // Amanero
            0x1b4b to "Amanero",        // Amanero
            0x1b4c to "Amanero",        // Amanero
            0x1b4d to "Amanero",        // Amanero
            0x1b4e to "Amanero",        // Amanero
            0x1b4f to "Amanero",        // Amanero
            0x1ec5 to "Amanero",        // Amanero
            0x1f08 to "Amanero",        // Amanero
            0x1f09 to "Amanero",        // Amanero
            0x1f0a to "Amanero",        // Amanero
            0x1f0b to "Amanero",        // Amanero
            0x1f0c to "Amanero",        // Amanero
            0x1f0d to "Amanero",        // Amanero
            0x1f0e to "Amanero",        // Amanero
            0x1f0f to "Amanero",        // Amanero
            0x1f10 to "Amanero",        // Amanero
            0x1f11 to "Amanero",        // Amanero
            0x1f12 to "Amanero",        // Amanero
            0x1f13 to "Amanero",        // Amanero
            0x1f14 to "Amanero",        // Amanero
            0x1f15 to "Amanero",        // Amanero
            0x1f16 to "Amanero",        // Amanero
            0x1f17 to "Amanero",        // Amanero
            0x1f18 to "Amanero",        // Amanero
            0x1f19 to "Amanero",        // Amanero
            0x1f1a to "Amanero",        // Amanero
            0x1f1b to "Amanero",        // Amanero
            0x1f1c to "Amanero",        // Amanero
            0x1f1d to "Amanero",        // Amanero
            0x1f1e to "Amanero",        // Amanero
            0x1f1f to "Amanero",        // Amanero
            0x2a52 to "Topping",        // Topping
            0x2a53 to "Topping",        // Topping
            0x2a54 to "Topping",        // Topping
            0x2a55 to "Topping",        // Topping
            0x2a56 to "Topping",        // Topping
            0x2a57 to "Topping",        // Topping
            0x2a58 to "Topping",        // Topping
            0x2a59 to "Topping",        // Topping
            0x2a5a to "Topping",        // Topping
            0x2a5b to "Topping",        // Topping
            0x2a5c to "Topping",        // Topping
            0x2a5d to "Topping",        // Topping
            0x2a5e to "Topping",        // Topping
            0x2a5f to "Topping",        // Topping
            0x2a60 to "Topping",        // Topping
            0x2a61 to "Topping",        // Topping
            0x2a62 to "Topping",        // Topping
            0x2a63 to "Topping",        // Topping
            0x2a64 to "Topping",        // Topping
            0x2a65 to "Topping",        // Topping
            0x2a66 to "Topping",        // Topping
            0x2a67 to "Topping",        // Topping
            0x2a68 to "Topping",        // Topping
            0x2a69 to "Topping",        // Topping
            0x2a6a to "Topping",        // Topping
            0x2a6b to "Topping",        // Topping
            0x2a6c to "Topping",        // Topping
            0x2a6d to "Topping",        // Topping
            0x2a6e to "Topping",        // Topping
            0x2a6f to "Topping",        // Topping
            0x2a70 to "Topping",        // Topping
            0x2a71 to "Topping",        // Topping
            0x2a72 to "Topping",        // Topping
            0x2a73 to "Topping",        // Topping
            0x2a74 to "Topping",        // Topping
            0x2a75 to "Topping",        // Topping
            0x2a76 to "Topping",        // Topping
            0x2a77 to "Topping",        // Topping
            0x2a78 to "Topping",        // Topping
            0x2a79 to "Topping",        // Topping
            0x2a7a to "Topping",        // Topping
            0x2a7b to "Topping",        // Topping
            0x2a7c to "Topping",        // Topping
            0x2a7d to "Topping",        // Topping
            0x2a7e to "Topping",        // Topping
            0x2a7f to "Topping",        // Topping
            0x2a80 to "Topping",        // Topping
            0x2a81 to "Topping",        // Topping
            0x2a82 to "Topping",        // Topping
            0x2a83 to "Topping",        // Topping
            0x2a84 to "Topping",        // Topping
            0x2a85 to "Topping",        // Topping
            0x2a86 to "Topping",        // Topping
            0x2a87 to "Topping",        // Topping
            0x2a88 to "Topping",        // Topping
            0x2a89 to "Topping",        // Topping
            0x2a8a to "Topping",        // Topping
            0x2a8b to "Topping",        // Topping
            0x2a8c to "Topping",        // Topping
            0x2a8d to "Topping",        // Topping
            0x2a8e to "Topping",        // Topping
            0x2a8f to "Topping",        // Topping
            0x2a90 to "Topping",        // Topping
            0x2a91 to "Topping",        // Topping
            0x2a92 to "Topping",        // Topping
            0x2a93 to "Topping",        // Topping
            0x2a94 to "Topping",        // Topping
            0x2a95 to "Topping",        // Topping
            0x2a96 to "Topping",        // Topping
            0x2a97 to "Topping",        // Topping
            0x2a98 to "Topping",        // Topping
            0x2a99 to "Topping",        // Topping
            0x2a9a to "Topping",        // Topping
            0x2a9b to "Topping",        // Topping
            0x2a9c to "Topping",        // Topping
            0x2a9d to "Topping",        // Topping
            0x2a9e to "Topping",        // Topping
            0x2a9f to "Topping",        // Topping
            0x2aa0 to "Topping",        // Topping
            0x2aa1 to "Topping",        // Topping
            0x2aa2 to "Topping",        // Topping
            0x2aa3 to "Topping",        // Topping
            0x2aa4 to "Topping",        // Topping
            0x2aa5 to "Topping",        // Topping
            0x2aa6 to "Topping",        // Topping
            0x2aa7 to "Topping",        // Topping
            0x2aa8 to "Topping",        // Topping
            0x2aa9 to "Topping",        // Topping
            0x2aaa to "Topping",        // Topping
            0x2aab to "Topping",        // Topping
            0x2aac to "Topping",        // Topping
            0x2aad to "Topping",        // Topping
            0x2aae to "Topping",        // Topping
            0x2aaf to "Topping",        // Topping
            0x2ab0 to "Topping",        // Topping
            0x2ab1 to "Topping",        // Topping
            0x2ab2 to "Topping",        // Topping
            0x2ab3 to "Topping",        // Topping
            0x2ab4 to "Topping",        // Topping
            0x2ab5 to "Topping",        // Topping
            0x2ab6 to "Topping",        // Topping
            0x2ab7 to "Topping",        // Topping
            0x2ab8 to "Topping",        // Topping
            0x2ab9 to "Topping",        // Topping
            0x2aba to "Topping",        // Topping
            0x2abb to "Topping",        // Topping
            0x2abc to "Topping",        // Topping
            0x2abd to "Topping",        // Topping
            0x2abe to "Topping",        // Topping
            0x2abf to "Topping",        // Topping
            0x2ac0 to "Topping",        // Topping
            0x2ac1 to "Topping",        // Topping
            0x2ac2 to "Topping",        // Topping
            0x2ac3 to "Topping",        // Topping
            0x2ac4 to "Topping",        // Topping
            0x2ac5 to "Topping",        // Topping
            0x2ac6 to "Topping",        // Topping
            0x2ac7 to "Topping",        // Topping
            0x2ac8 to "Topping",        // Topping
            0x2ac9 to "Topping",        // Topping
            0x2aca to "Topping",        // Topping
            0x2acb to "Topping",        // Topping
            0x2acc to "Topping",        // Topping
            0x2acd to "Topping",        // Topping
            0x2ace to "Topping",        // Topping
            0x2acf to "Topping",        // Topping
            0x2ad0 to "Topping",        // Topping
            0x2ad1 to "Topping",        // Topping
            0x2ad2 to "Topping",        // Topping
            0x2ad3 to "Topping",        // Topping
            0x2ad4 to "Topping",        // Topping
            0x2ad5 to "Topping",        // Topping
            0x2ad6 to "Topping",        // Topping
            0x2ad7 to "Topping",        // Topping
            0x2ad8 to "Topping",        // Topping
            0x2ad9 to "Topping",        // Topping
            0x2ada to "Topping",        // Topping
            0x2adb to "Topping",        // Topping
            0x2adc to "Topping",        // Topping
            0x2add to "Topping",        // Topping
            0x2ade to "Topping",        // Topping
            0x2adf to "Topping",        // Topping
            0x2ae0 to "Topping",        // Topping
            0x2ae1 to "Topping",        // Topping
            0x2ae2 to "Topping",        // Topping
            0x2ae3 to "Topping",        // Topping
            0x2ae4 to "Topping",        // Topping
            0x2ae5 to "Topping",        // Topping
            0x2ae6 to "Topping",        // Topping
            0x2ae7 to "Topping",        // Topping
            0x2ae8 to "Topping",        // Topping
            0x2ae9 to "Topping",        // Topping
            0x2aea to "Topping",        // Topping
            0x2aeb to "Topping",        // Topping
            0x2aec to "Topping",        // Topping
            0x2aed to "Topping",        // Topping
            0x2aee to "Topping",        // Topping
            0x2aef to "Topping",        // Topping
            0x2af0 to "Topping",        // Topping
            0x2af1 to "Topping",        // Topping
            0x2af2 to "Topping",        // Topping
            0x2af3 to "Topping",        // Topping
            0x2af4 to "Topping",        // Topping
            0x2af5 to "Topping",        // Topping
            0x2af6 to "Topping",        // Topping
            0x2af7 to "Topping",        // Topping
            0x2af8 to "Topping",        // Topping
            0x2af9 to "Topping",        // Topping
            0x2afa to "Topping",        // Topping
            0x2afb to "Topping",        // Topping
            0x2afc to "Topping",        // Topping
            0x2afd to "Topping",        // Topping
            0x2afe to "Topping",        // Topping
            0x2aff to "Topping",        // Topping
        )
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var currentDevice: UsbDevice? = null
    private var currentConnection: UsbDeviceConnection? = null

    // ===== 状態 =====
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object AwaitingPermission : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val deviceInfo: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // ===== パーミッション受信 =====
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return

            synchronized(this) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    device?.let { connectDevice(it) }
                } else {
                    Log.w(TAG, "USB permission denied for $device")
                    _connectionState.value = ConnectionState.Error("USB permission denied")
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(permissionReceiver, filter)
    }

    /**
     * 接続済みのUSB DACを検出して返す。
     */
    fun findUsbAudioDevice(): UsbDevice? {
        return usbManager.deviceList.values.find { device ->
            isUsbAudioDevice(device)
        }
    }

    /**
     * USB DAC接続を要求（パーミッションが必要な場合）。
     */
    fun requestConnect(device: UsbDevice? = null) {
        val target = device ?: findUsbAudioDevice()
        if (target == null) {
            _connectionState.value = ConnectionState.Error("No USB audio device found")
            return
        }

        if (usbManager.hasPermission(target)) {
            connectDevice(target)
        } else {
            _connectionState.value = ConnectionState.AwaitingPermission
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(target, permissionIntent)
        }
    }

    /**
     * デバイスに接続し、記述子を取得してネイティブに渡す。
     */
    private fun connectDevice(device: UsbDevice) {
        _connectionState.value = ConnectionState.Connecting
        currentDevice = device

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            _connectionState.value = ConnectionState.Error("Failed to open USB device")
            return
        }
        currentConnection = connection

        // 生USB記述子を取得
        val rawDescriptor = connection.rawDescriptors
        if (rawDescriptor == null) {
            _connectionState.value = ConnectionState.Error("Failed to get USB descriptors")
            return
        }

        // ネイティブに渡してUAC2記述子をパース
        val attached = NativeEngine.nativeUsbAttachDevice(
            connection.fileDescriptor,
            device.vendorId,
            device.productId,
            rawDescriptor
        )

        if (!attached) {
            _connectionState.value = ConnectionState.Error("Failed to parse UAC2 descriptors")
            return
        }

        // インターフェースclaim（Java側で行う必要がある）
        // ストリーミンインターフェースをclaim
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                val claimed = connection.claimInterface(iface, true)
                Log.i(TAG, "Claimed interface ${iface.id}: $claimed")
            }
        }

        val summary = NativeEngine.nativeUsbGetDeviceSummary()
        Log.i(TAG, "USB DAC connected:\n$summary")

        _connectionState.value = ConnectionState.Connected(summary)
    }

    /**
     * USB DACを切断。
     */
    fun disconnect() {
        currentConnection?.let { conn ->
            // インターフェースrelease
            currentDevice?.let { device ->
                for (i in 0 until device.interfaceCount) {
                    val iface = device.getInterface(i)
                    if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                        conn.releaseInterface(iface)
                    }
                }
            }
            conn.close()
        }
        currentConnection = null
        currentDevice = null
        NativeEngine.nativeUsbDetachDevice()
        _connectionState.value = ConnectionState.Disconnected
        Log.i(TAG, "USB DAC disconnected")
    }

    /**
     * 現在の接続からPlaybackEngine用のUSB出力設定を構築。
     */
    fun buildUsbOutputConfig(
        sampleRate: Int,
        bitDepth: Int,
        channels: Int
    ): PlaybackEngine.UsbOutputConfig? {
        val conn = currentConnection ?: return null
        val device = currentDevice ?: return null

        // ネイティブ側で最適なalt settingを選択
        // （usb_device_manager.cppのgetOutputParamsをJNI経由で呼ぶ必要があるが、
        //  現状のJNIでは直接取得できないため、簡易的に実装）

        // ストリーミングインターフェースを探す
        var streamingInterface = -1
        var altSetting = 0
        var endpointAddr = 0
        var frameBytes = channels * (bitDepth / 8)

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                iface.interfaceSubclass == 0x02  // AUDIO_SUBCLASS_STREAMING
            ) {
                streamingInterface = iface.id
                // デフォルトでalt setting 1（アクティブな設定）を使用
                if (iface.endpointCount > 0) {
                    val ep = iface.getEndpoint(0)
                    endpointAddr = ep.address
                    altSetting = 1
                }
                break
            }
        }

        if (streamingInterface < 0) {
            Log.e(TAG, "No USB audio streaming interface found")
            return null
        }

        // alt settingを切り替え
        val result = conn.controlTransfer(
            0x01,  // USB_DIR_OUT | USB_TYPE_STANDARD | USB_RECIP_INTERFACE
            0x0B,  // SET_INTERFACE
            altSetting,
            streamingInterface,
            null, 0, 0,
            5000
        )

        if (result < 0) {
            Log.w(TAG, "Failed to set alt setting, trying default")
        }

        return PlaybackEngine.UsbOutputConfig(
            fd = conn.fileDescriptor,
            interfaceNum = streamingInterface,
            altSetting = altSetting,
            endpointAddress = endpointAddr,
            frameBytes = frameBytes,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels
        )
    }

    /**
     * デバイスがUSBオーディオデバイスか判定。
     */
    private fun isUsbAudioDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                return true
            }
        }
        return false
    }

    /**
     * デバイスの製造メーカー名を取得（参考）。
     */
    fun getDeviceManufacturer(vendorId: Int): String {
        return KNOWN_DACS[vendorId] ?: "Unknown ($"%04X".format(vendorId))"
    }

    /**
     * クリーンアップ。
     */
    fun cleanup() {
        disconnect()
        try {
            context.unregisterReceiver(permissionReceiver)
        } catch (_: IllegalArgumentException) {
            // 既にunregister済み
        }
    }
}
