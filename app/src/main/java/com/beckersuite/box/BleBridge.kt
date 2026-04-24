package com.beckersuite.box

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Must match your C# BlePeripheral.exe exactly
// ─────────────────────────────────────────────────────────────────────────────
private val SERVICE_UUID = UUID.fromString("a07498ca-ad5b-474e-940d-16f1fbe7e8cd")
private val CHAR_UUID    = UUID.fromString("51ff12bb-3ed8-46e5-b4f9-d64e2fec021b")
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
class BleBridge(
    private val context: Context,
    private val webView: WebView,
) {
    private val adapter  = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanner  = adapter.bluetoothLeScanner

    private var gatt : BluetoothGatt? = null
    private var characteristic : BluetoothGattCharacteristic? = null
    private var scanning = false

    // ── Called from JavaScript ───────────────────────────────────────────────

    /**
     * Scans for the BLE peripheral and connects automatically.
     * No picker — finds the first device advertising SERVICE_UUID.
     */
    @JavascriptInterface
    fun connect() {
        if (gatt != null) {
            jsCallback("__bleOnError", "'Already connected'")
            return
        }
        startScan()
    }

    /**
     * Write a string to the GATT characteristic.
     */
    @JavascriptInterface
    fun write(data: String) {
        val ch   = characteristic ?: return jsCallback("__bleOnError", "'Not connected'")
        val g    = gatt           ?: return jsCallback("__bleOnError", "'Not connected'")
        val bytes = data.toByteArray(Charsets.UTF_8)

        @Suppress("DEPRECATION")
        ch.value     = bytes
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        @Suppress("DEPRECATION")
        g.writeCharacteristic(ch)
    }

    /**
     * Disconnect and clean up.
     */
    @JavascriptInterface
    fun disconnect() {
        gatt?.disconnect()
    }

    // ── BLE Scan ─────────────────────────────────────────────────────────────

    private fun startScan() {
        if (scanning) return
        scanning = true

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        scanner.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            jsCallback("__bleOnError", "'Scan failed with code $errorCode'")
        }
    }

    // ── GATT Callbacks ────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt = g
                    // Request minimum latency connection interval (~7.5ms)
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val deviceId = g.device.address
                    gatt           = null
                    characteristic = null
                    g.close()
                    jsCallback("__bleOnDisconnected", "'$deviceId'")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                jsCallback("__bleOnError", "'Service discovery failed: $status'")
                g.disconnect()
                return
            }

            val ch = g.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            if (ch == null) {
                jsCallback("__bleOnError", "'Characteristic not found'")
                g.disconnect()
                return
            }

            characteristic = ch
            jsCallback("__bleOnConnected", "'${g.device.address}'")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Call a global JS function on the page's main thread.
     * argsJs is already-formatted JS (e.g. "'some string'" or "42")
     */
    private fun jsCallback(fn: String, argsJs: String) {
        Log.d("BleBridge", "Calling JS callback $fn with args: ($argsJs)")
        webView.post {
            webView.evaluateJavascript("if(typeof $fn==='function')$fn($argsJs);", null)
        }
    }
}