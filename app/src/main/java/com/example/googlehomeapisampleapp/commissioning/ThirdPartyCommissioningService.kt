package com.example.googlehomeapisampleapp.commissioning

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.gms.home.matter.commissioning.CommissioningCompleteMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningRequestMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningService
import com.google.android.gms.home.matter.commissioning.CommissioningService.CommissioningError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.lang.Long.max
import java.security.SecureRandom
import kotlin.math.abs

private const val TAG = "ThirdPartyCommissioningService"

class ThirdPartyCommissioningService : Service(), CommissioningService.Callback {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var commissioningServiceDelegate: CommissioningService
    private lateinit var chipClient: ChipClient

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        commissioningServiceDelegate = CommissioningService.Builder(this).setCallback(this).build()
        chipClient = ChipClient(applicationContext)
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind(): intent [${intent}]")
        return commissioningServiceDelegate.asBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand(): intent [${intent}] flags [${flags}] startId [${startId}]")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
        serviceJob.cancel()
    }

    override fun onCommissioningRequested(metadata: CommissioningRequestMetadata) {
        Log.d(
            TAG,
            "*** onCommissioningRequested ***:\n" +
                    "\tdeviceDescriptor: " +
                    "deviceType [${metadata.deviceDescriptor.deviceType}] " +
                    "vendorId [${metadata.deviceDescriptor.vendorId}] " +
                    "productId [${metadata.deviceDescriptor.productId}]\n" +
                    "\tnetworkLocation: " +
                    "IP address toString() [${metadata.networkLocation.ipAddress}] " +
                    "IP address hostAddress [${metadata.networkLocation.ipAddress.hostAddress}] " +
                    "port [${metadata.networkLocation.port}]\n" +
                    "\tpassCode [${metadata.passcode}]"
        )

        serviceScope.launch {
            val deviceId = getNextDeviceId()
            try {
                Log.d(
                    TAG,
                    "Commissioning: App fabric -> ChipClient.establishPaseConnection(): deviceId [${deviceId}]"
                )
                chipClient.awaitEstablishPaseConnection(
                    deviceId,
                    metadata.networkLocation.ipAddress.hostAddress!!,
                    metadata.networkLocation.port,
                    metadata.passcode
                )

                Log.d(
                    TAG,
                    "Commissioning: App fabric -> ChipClient.commissionDevice(): deviceId [${deviceId}]"
                )
                chipClient.awaitCommissionDevice(deviceId, null)
            } catch (e: Exception) {
                Log.e(TAG, "onCommissioningRequested() failed")
                commissioningServiceDelegate
                    .sendCommissioningError(CommissioningError.OTHER)
                    .addOnSuccessListener {
                        Log.d(
                            TAG,
                            "Commissioning: commissioningServiceDelegate.sendCommissioningError() succeeded"
                        )
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(
                            TAG,
                            "Commissioning: commissioningServiceDelegate.sendCommissioningError() failed, $e2"
                        )
                    }
                return@launch
            }

            Log.d(
                TAG,
                "Commissioning: Calling commissioningServiceDelegate.sendCommissioningComplete()"
            )
            commissioningServiceDelegate
                .sendCommissioningComplete(
                    CommissioningCompleteMetadata.builder().setToken(deviceId.toString()).build()
                )
                .addOnSuccessListener {
                    Log.d(
                        TAG,
                        "Commissioning: commissioningServiceDelegate.sendCommissioningComplete() succeeded"
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(
                        TAG,
                        "Commissioning: commissioningServiceDelegate.sendCommissioningComplete() failed, $e"
                    )
                }
        }
    }

    /**
     * Generates the device id for the device being commissioned
     */
    private fun getNextDeviceId(): Long {
        val secureRandom =
            try {
                SecureRandom.getInstance("SHA1PRNG")
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to instantiate SecureRandom with SHA1PRNG $ex")
                SecureRandom()
            }

        return max(abs(secureRandom.nextLong()), 1)
    }
}