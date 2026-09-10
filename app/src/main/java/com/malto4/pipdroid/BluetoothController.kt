package com.malto4.pipdroid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.malto4.pipdroid.databinding.ActivityMainBinding

/** Связь с корпусом по BLE целиком: биндинг фонового сервиса, индикатор соединения и сканер пейринга. */
/** Сканер один на два экрана — шаг PAIRING мастера и раздел Settings → Bluetooth, — поэтому он живёт
 * здесь, а не внутри мастера: иначе экран Settings зависел бы от мастера. Разбор пришедших команд
 * контроллеру не принадлежит: handleBleCommand раздаёт их по всем разделам сразу и остаётся в
 * активности, сюда протянут колбэком. */
internal class BluetoothController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val prefs: SharedPreferences,
    private val scanPermissionRequestCode: Int,
    private val accentColor: () -> Int,
    private val playButton: () -> Unit,
    private val requestEnableBluetooth: () -> Unit,
    private val onCommand: (String) -> Unit,
) {
    private val bluetoothMAC_SPKey = "bluetoothMAC"
    private val bluetoothSUUID_SPKey = "bluetoothSUUID"
    private val handler = Handler(Looper.getMainLooper())

    private var bleService: PipBoyBleService? = null
    private var bleServiceBound = false
    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val bound = (service as PipBoyBleService.LocalBinder).getService()
            bleService = bound
            bleServiceBound = true
            bound.onConnectionStateChanged = { status -> activity.runOnUiThread { updateConnected(status) } }
            bound.onCommandReceived = { raw -> activity.runOnUiThread { onCommand(raw) } }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            bleServiceBound = false
        }
    }

    private var pairingScanCallback: ScanCallback? = null
    private val pairingFoundAddresses = mutableSetOf<String>()
    private val pairingScanTimeoutRunnable = Runnable { stopPairingScan() }
    private val pairingScanDurationMs = 15000L
    private var pairingDevicesContainer: LinearLayout? = null
    private var pairingStatusView: TextView? = null
    private var pairingOnSelect: ((String) -> Unit)? = null

    // ===== ПУБЛИЧНАЯ ПОВЕРХНОСТЬ =====

    /** Раздел Settings → Bluetooth: текущий адрес и кнопка повторного скана. */
    fun setup() {
        // Интерфейс мастера заменяет старый ручной ввод MAC и UUID целиком: тап по найденному
        // устройству сам сохраняет адрес и переподключается.
        refreshCurrentDevice()
        binding.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.btnBluetoothRescan.setOnClickListener {
            playButton()
            startSettingsPairingScan()
        }
    }

    /** Поднимает связь после выдачи разрешений: адаптер, при необходимости — системный запрос на включение. */
    fun start() {
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            Log.e(TAG, "Bluetooth is not supported")
            return
        }
        if (!adapter.isEnabled) {
            requestEnableBluetooth()
            return
        }
        requestIgnoreBatteryOptimizations()
        startAndBindBleService()
    }

    /** Полная остановка вместе с сервисом — режим Телефон работает без корпуса. */
    fun stop() {
        disconnect()
        unbind()
        activity.stopService(Intent(activity, PipBoyBleService::class.java))
    }

    /** Отвязка от локального биндинга без остановки самого сервиса — тот держит связь в фоне. */
    fun unbind() {
        if (bleServiceBound) {
            activity.unbindService(bleServiceConnection)
            bleServiceBound = false
        }
    }

    fun send(bleText: String) {
        if (bleService?.isConnected() == true) {
            bleService?.sendCommand(bleText)
            Log.i(TAG, "Sending text to BLE device")
        } else {
            Log.e(TAG, "BluetoothGatt is not connected")
        }
    }

    /** Стартовое состояние индикатора: сервис мог остаться подключённым с прошлого запуска активности. */
    fun refreshConnectionIndicator() {
        updateConnected(if (bleService?.isConnected() == true) "CONNECTED" else "DISCONNECTED")
    }

    // ===== ФОНОВЫЙ СЕРВИС И ИНДИКАТОР =====

    private fun startAndBindBleService() {
        val intent = Intent(activity, PipBoyBleService::class.java)
        ContextCompat.startForegroundService(activity, intent)
        activity.bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
    }

    /** Не обязательное разрешение, а рекомендация системы. */
    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(activity.packageName)) {
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Battery optimization settings not available", e)
            }
        }
    }

    private fun updateConnected(status: String) {
        // status — внутренний токен состояния, подпись для показа берётся из строкового ресурса.
        val displayText = if (status == "CONNECTED") activity.getString(R.string.bluetooth_status_connected) else activity.getString(R.string.bluetooth_status_disconnected)
        binding.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.textViewBLUETOOTHConnection.text = displayText
        // Индикатор BLE в углу row1: состояние передаётся альфой, не сменой drawable.
        binding.incLayoutHeaderToplevel.imgHeaderBleStatus.alpha = if (status == "CONNECTED") 1.0f else 0.35f
    }

    private fun disconnect() {
        updateConnected("DISCONNECTED")
        bleService?.disconnect()
    }

    // ===== ПЕЙРИНГ =====

    @SuppressLint("MissingPermission")
    fun startPairingScan(devicesContainer: LinearLayout, statusView: TextView, onSelect: (String) -> Unit) {
        stopPairingScan()
        pairingDevicesContainer = devicesContainer
        pairingStatusView = statusView
        pairingOnSelect = onSelect
        devicesContainer.removeAllViews()
        pairingFoundAddresses.clear()
        statusView.text = activity.getString(R.string.wizard_pairing_scanning)

        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            statusView.text = activity.getString(R.string.wizard_pairing_bluetooth_off)
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            statusView.text = activity.getString(R.string.wizard_pairing_scan_failed)
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(prefs.getString(bluetoothSUUID_SPKey, "6E400001-B5A3-F393-E0A9-E50E24DCCA9E")))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                addPairingDevice(result.device.address, result.device.name ?: result.scanRecord?.deviceName)
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed: $errorCode")
                pairingStatusView?.text = activity.getString(R.string.wizard_pairing_scan_failed)
            }
        }
        pairingScanCallback = callback
        scanner.startScan(listOf(filter), settings, callback)
        handler.postDelayed(pairingScanTimeoutRunnable, pairingScanDurationMs)
    }

    @SuppressLint("MissingPermission")
    fun stopPairingScan() {
        handler.removeCallbacks(pairingScanTimeoutRunnable)
        val callback = pairingScanCallback ?: return
        pairingScanCallback = null
        val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter?.isEnabled == true) {
            adapter.bluetoothLeScanner?.stopScan(callback)
        }
        if (pairingFoundAddresses.isEmpty()) {
            pairingStatusView?.text = activity.getString(R.string.wizard_pairing_none_found)
        }
    }

    private fun addPairingDevice(address: String, name: String?) {
        if (!pairingFoundAddresses.add(address)) return
        val container = pairingDevicesContainer ?: return
        val statusView = pairingStatusView ?: return
        statusView.text = activity.getString(R.string.wizard_pairing_found, pairingFoundAddresses.size)
        val button = Button(activity, null, 0, R.style.PipWizardButtonStyle).apply {
            text = name ?: address
            backgroundTintList = ColorStateList.valueOf(accentColor())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
            setOnClickListener {
                playButton()
                pairingOnSelect?.invoke(address)
            }
        }
        GlobalTextScale.register(button)
        container.addView(button)
    }

    fun applyPairedDevice(address: String) {
        stopPairingScan()
        prefs.edit().putString(bluetoothMAC_SPKey, address).apply()
        val service = bleService
        if (service != null) {
            service.reconnectWithCurrentSettings()
        } else {
            startAndBindBleService()
        }
    }

    private fun selectSettingsPairingDevice(address: String) {
        applyPairedDevice(address)
        refreshCurrentDevice()
    }

    fun startSettingsPairingScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                scanPermissionRequestCode
            )
            return
        }
        val bt = binding.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth
        startPairingScan(bt.layoutBluetoothPairingDevices, bt.tvBluetoothPairingStatus) { address ->
            selectSettingsPairingDevice(address)
        }
    }

    /** Показывает сохранённый сейчас MAC (или "не выбрано", если пейринга ещё не было). */
    private fun refreshCurrentDevice() {
        val value = prefs.getString(bluetoothMAC_SPKey, null)
        binding.incLayoutSettingsGlobal.incLayoutTabSettingsBluetooth.tvBluetoothCurrentMac.text =
            value ?: activity.getString(R.string.bluetooth_mac_not_set)
    }

    private companion object {
        private const val TAG = "BluetoothController"
    }
}
