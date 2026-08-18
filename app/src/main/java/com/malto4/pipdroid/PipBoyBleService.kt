package com.malto4.pipdroid

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.UUID

/**
 * Держит BLE-соединение с PipBoy живым в фоне (протокол PipBoy_BLE_Protocol_v0.2.md,
 * раздел 5) — постоянное уведомление, foreground service, иначе Android рвёт связь при
 * сворачивании приложения. MainActivity привязывается к сервису биндингом и никогда сама
 * не трогает BluetoothGatt — вся физическая связь только здесь.
 */
class PipBoyBleService : Service() {

    companion object {
        private const val TAG = "PipBoyBleService"
        private const val NOTIFICATION_CHANNEL_ID = "pipboy_ble_connection"
        private const val NOTIFICATION_ID = 1
        private const val RECONNECT_DELAY_MS = 3000L

        // TX-характеристика NUS — используется и как read-характеристика по умолчанию,
        // и как UUID дескриптора нотификации (совпадение зафиксировано ещё в исходном
        // коде PipDroid, см. отчёт разведки перед этим шагом — не трогаем).
        private const val NUS_TX_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

        private const val PREFS_NAME = "PipDroid_Preferences"
        private const val KEY_MAC = "bluetoothMAC"
        private const val KEY_SERVICE_UUID = "bluetoothSUUID"
        private const val KEY_READ_UUID = "bluetoothRUUID"
        private const val KEY_WRITE_UUID = "bluetoothWUUID"
    }

    inner class LocalBinder : Binder() {
        fun getService(): PipBoyBleService = this@PipBoyBleService
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    private var deviceAddress: String? = null
    private var serviceUUID: UUID? = null
    private var characteristicReadUUID: UUID? = null
    private var characteristicWriteUUID: UUID? = null

    private var userRequestedDisconnect = false

    /** Последнее известное состояние POWER от ESP32 — источник истины ESP32, не телефон
     * (протокол, раздел 3.1). */
    var powerState: Boolean = false
        private set

    var onConnectionStateChanged: ((String) -> Unit)? = null
    var onCommandReceived: ((String) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Подключение..."))
        // Идемпотентно: повторный старт (например, по клику Connect в настройках) не
        // должен рвать уже живое соединение — для форс-реконнекта есть отдельный метод.
        if (bluetoothGatt == null) {
            userRequestedDisconnect = false
            loadConnectionSettings()
            connect()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        bluetoothGatt?.close()
        bluetoothGatt = null
        super.onDestroy()
    }

    fun isConnected(): Boolean = bluetoothGatt != null

    /** Форс-реконнект — например, после сохранения новых настроек MAC/UUID в Settings. */
    fun reconnectWithCurrentSettings() {
        userRequestedDisconnect = false
        mainHandler.removeCallbacksAndMessages(null)
        bluetoothGatt?.close()
        bluetoothGatt = null
        loadConnectionSettings()
        connect()
    }

    fun disconnect() {
        userRequestedDisconnect = true
        mainHandler.removeCallbacksAndMessages(null)
        bluetoothGatt?.close()
        bluetoothGatt = null
        onConnectionStateChanged?.invoke("DISCONNECTED")
        updateNotification("Отключено")
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(text: String) {
        val gatt = bluetoothGatt ?: return
        val characteristic = gatt.getService(serviceUUID)?.getCharacteristic(characteristicWriteUUID) ?: return
        @Suppress("DEPRECATION")
        characteristic.value = text.toByteArray()
        @Suppress("DEPRECATION")
        gatt.writeCharacteristic(characteristic)
    }

    private fun loadConnectionSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        deviceAddress = prefs.getString(KEY_MAC, "AA:BB:CC:DD:EE:FF")
        serviceUUID = UUID.fromString(prefs.getString(KEY_SERVICE_UUID, "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
        characteristicReadUUID = UUID.fromString(prefs.getString(KEY_READ_UUID, NUS_TX_UUID))
        characteristicWriteUUID = UUID.fromString(prefs.getString(KEY_WRITE_UUID, "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))
    }

    @SuppressLint("MissingPermission")
    private fun connect() {
        val address = deviceAddress ?: return
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled")
            return
        }
        val device = bluetoothAdapter.getRemoteDevice(address)
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private fun scheduleReconnect() {
        if (userRequestedDisconnect) return
        mainHandler.postDelayed({ connect() }, RECONNECT_DELAY_MS)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            mainHandler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server.")
                    gatt.discoverServices()
                    onConnectionStateChanged?.invoke("CONNECTED")
                    updateNotification("Подключено")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server.")
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    onConnectionStateChanged?.invoke("DISCONNECTED")
                    updateNotification(if (userRequestedDisconnect) "Отключено" else "Переподключение...")
                    scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered.")
                val characteristic = gatt.getService(serviceUUID)?.getCharacteristic(characteristicReadUUID)
                characteristic?.let { enableCharacteristicNotification(it) }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val raw = characteristic.value.toString(Charsets.UTF_8)
            mainHandler.post {
                Log.i(TAG, "Characteristic changed: $raw")
                if (raw.startsWith("POWER:")) {
                    powerState = raw.substringAfter(":") == "1"
                }
                onCommandReceived?.invoke(raw)
            }
        }

        @SuppressLint("MissingPermission")
        private fun enableCharacteristicNotification(characteristic: BluetoothGattCharacteristic) {
            bluetoothGatt?.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(UUID.fromString(NUS_TX_UUID))
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeDescriptor(descriptor)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic written successfully")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PipBoy — соединение",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PipBoy")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }
}
