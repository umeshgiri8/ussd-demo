package com.example.telephony

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.data.model.ExecutionType
import com.example.data.model.SimAccountInfo
import com.example.data.model.UssdResult
import kotlinx.coroutines.delay

class UssdManager(private val context: Context) {

    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val subscriptionManager: SubscriptionManager? =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

    fun hasCallPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    data class TelephonyInfo(
        val carrierName: String,
        val isSimReady: Boolean,
        val simStateDescription: String,
        val isEmulatorOrSimless: Boolean,
        val simCount: Int
    )

    /**
     * Retrieves all detected active SIM cards, or returns simulated dual SIMs
     * in test/simulation environments.
     */
    fun getAvailableSimCards(isSimulationMode: Boolean): List<SimAccountInfo> {
        if (!isSimulationMode && hasReadPhoneStatePermission() && subscriptionManager != null) {
            try {
                val subList = subscriptionManager.activeSubscriptionInfoList
                if (!subList.isNullOrEmpty()) {
                    return subList.map { subInfo ->
                        val name = subInfo.displayName?.toString()?.ifBlank { null }
                            ?: subInfo.carrierName?.toString()?.ifBlank { null }
                            ?: "SIM ${subInfo.simSlotIndex + 1}"
                        SimAccountInfo(
                            subscriptionId = subInfo.subscriptionId,
                            slotIndex = subInfo.simSlotIndex,
                            displayName = name,
                            carrierName = subInfo.carrierName?.toString() ?: "Carrier",
                            isSimReady = true
                        )
                    }
                }
            } catch (e: SecurityException) {
                // Falls through to fallback or single SIM
            }
        }

        // If in simulation mode or on emulator/no-SIM device, provide simulated Dual SIMs
        if (isSimulationMode || getTelephonyInfo().isEmulatorOrSimless) {
            return listOf(
                SimAccountInfo(
                    subscriptionId = 101,
                    slotIndex = 0,
                    displayName = "SIM 1 (Primary - Telecom 5G)",
                    carrierName = "Telecom 5G",
                    isSimReady = true
                ),
                SimAccountInfo(
                    subscriptionId = 102,
                    slotIndex = 1,
                    displayName = "SIM 2 (Secondary - Global Roam)",
                    carrierName = "Global Roam",
                    isSimReady = true
                )
            )
        }

        // Single default SIM fallback
        val carrier = telephonyManager?.networkOperatorName?.ifBlank { null } ?: "Default SIM"
        return listOf(
            SimAccountInfo(
                subscriptionId = -1,
                slotIndex = 0,
                displayName = "SIM 1 ($carrier)",
                carrierName = carrier,
                isSimReady = telephonyManager?.simState == TelephonyManager.SIM_STATE_READY
            )
        )
    }

    fun getTelephonyInfo(): TelephonyInfo {
        val tm = telephonyManager
        val simState = tm?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN
        val carrier = tm?.networkOperatorName?.ifBlank { tm.simOperatorName } ?: "Unknown Carrier"
        val isReady = simState == TelephonyManager.SIM_STATE_READY

        val simDescription = when (simState) {
            TelephonyManager.SIM_STATE_READY -> "SIM Ready ($carrier)"
            TelephonyManager.SIM_STATE_ABSENT -> "No SIM Card Inserted"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "SIM PIN Required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "SIM PUK Required"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
            else -> "SIM Inactive or Virtual"
        }

        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                !isReady

        var detectedCount = 1
        if (hasReadPhoneStatePermission() && subscriptionManager != null) {
            try {
                detectedCount = subscriptionManager.activeSubscriptionInfoCount.coerceAtLeast(1)
            } catch (e: Exception) {
                detectedCount = 1
            }
        }

        return TelephonyInfo(
            carrierName = if (carrier.isBlank() || carrier == "Android") "Virtual/Emulator Carrier" else carrier,
            isSimReady = isReady,
            simStateDescription = simDescription,
            isEmulatorOrSimless = isEmulator,
            simCount = detectedCount
        )
    }

    /**
     * Executes the USSD code on the selected SIM card via TelephonyManager.
     * Uses createForSubscriptionId(subscriptionId) to bind to that specific SIM card.
     */
    fun sendUssdRequest(
        code: String,
        selectedSim: SimAccountInfo? = null,
        onResult: (UssdResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val simLabel = selectedSim?.displayName ?: "SIM 1"

        if (!hasCallPhonePermission()) {
            onResult(
                UssdResult(
                    code = code,
                    responseMessage = "Permission android.permission.CALL_PHONE is required to execute USSD requests.",
                    isSuccess = false,
                    failureReason = "Permission Denied",
                    executionType = ExecutionType.AUTOMATIC_IN_APP,
                    timestamp = System.currentTimeMillis(),
                    simName = simLabel
                )
            )
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onResult(
                UssdResult(
                    code = code,
                    responseMessage = "In-app USSD response interception requires Android 8.0 (API 26) or higher. Please use Direct Dial fallback.",
                    isSuccess = false,
                    failureReason = "Unsupported Android Version (< API 26)",
                    executionType = ExecutionType.AUTOMATIC_IN_APP,
                    timestamp = System.currentTimeMillis(),
                    simName = simLabel
                )
            )
            return
        }

        val tm = telephonyManager
        if (tm == null) {
            onResult(
                UssdResult(
                    code = code,
                    responseMessage = "TelephonyManager service unavailable on this device.",
                    isSuccess = false,
                    failureReason = "Telephony Service Null",
                    executionType = ExecutionType.AUTOMATIC_IN_APP,
                    timestamp = System.currentTimeMillis(),
                    simName = simLabel
                )
            )
            return
        }

        // Bind TelephonyManager to the specific selected SIM card if multi-SIM
        val targetTm = if (selectedSim != null && selectedSim.subscriptionId > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tm.createForSubscriptionId(selectedSim.subscriptionId)
        } else {
            tm
        }

        try {
            val mainHandler = Handler(Looper.getMainLooper())
            targetTm.sendUssdRequest(
                code,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        returnMessage: CharSequence
                    ) {
                        val duration = System.currentTimeMillis() - startTime
                        val cleanMessage = returnMessage.toString().trim()
                        onResult(
                            UssdResult(
                                code = request,
                                responseMessage = if (cleanMessage.isBlank()) "[Empty response from carrier]" else cleanMessage,
                                isSuccess = true,
                                failureReason = null,
                                executionType = ExecutionType.AUTOMATIC_IN_APP,
                                timestamp = System.currentTimeMillis(),
                                durationMs = duration,
                                simName = simLabel
                            )
                        )
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) {
                        val duration = System.currentTimeMillis() - startTime
                        val reason = when (failureCode) {
                            TelephonyManager.USSD_RETURN_FAILURE ->
                                "USSD Session Failed (-1): The cellular carrier on $simLabel returned a failure or terminated the session."
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL ->
                                "Service Unavailable (-2): SIM $simLabel is unavailable, in airplane mode, or does not support 3rd party app USSD interception."
                            else ->
                                "USSD Request Failed on $simLabel with code ($failureCode)."
                        }

                        onResult(
                            UssdResult(
                                code = request,
                                responseMessage = "$reason\n\nTip: You can use the 'Dial via Phone App' button below to execute this USSD code through the standard phone dialer, or turn on Test Simulation Mode.",
                                isSuccess = false,
                                failureReason = "Failure Code $failureCode",
                                executionType = ExecutionType.AUTOMATIC_IN_APP,
                                timestamp = System.currentTimeMillis(),
                                durationMs = duration,
                                simName = simLabel
                            )
                        )
                    }
                },
                mainHandler
            )
        } catch (se: SecurityException) {
            onResult(
                UssdResult(
                    code = code,
                    responseMessage = "SecurityException: ${se.localizedMessage ?: "Missing telephony permissions."}",
                    isSuccess = false,
                    failureReason = "SecurityException",
                    executionType = ExecutionType.AUTOMATIC_IN_APP,
                    timestamp = System.currentTimeMillis(),
                    simName = simLabel
                )
            )
        } catch (e: Exception) {
            onResult(
                UssdResult(
                    code = code,
                    responseMessage = "Error sending USSD: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    isSuccess = false,
                    failureReason = e.javaClass.simpleName,
                    executionType = ExecutionType.AUTOMATIC_IN_APP,
                    timestamp = System.currentTimeMillis(),
                    simName = simLabel
                )
            )
        }
    }

    /**
     * Dials the USSD code directly using the phone app, passing multi-SIM slot hints.
     */
    fun dialDirectly(code: String, selectedSim: SimAccountInfo? = null): UssdResult {
        val encodedHashUri = Uri.parse("tel:" + Uri.encode(code))
        val intentAction = if (hasCallPhonePermission()) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(intentAction, encodedHashUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (selectedSim != null) {
                putExtra("com.android.phone.extra.slot", selectedSim.slotIndex)
                putExtra("simSlot", selectedSim.slotIndex)
                putExtra("android.telecom.extra.SLOT_INDEX", selectedSim.slotIndex)
            }
        }

        val simLabel = selectedSim?.displayName ?: "Default SIM"

        try {
            context.startActivity(intent)
            return UssdResult(
                code = code,
                responseMessage = "Triggered phone dialer on $simLabel with code: $code\nCheck device screen for carrier USSD dialog.",
                isSuccess = true,
                failureReason = null,
                executionType = ExecutionType.DIALER_FALLBACK,
                timestamp = System.currentTimeMillis(),
                durationMs = 0,
                simName = simLabel
            )
        } catch (e: Exception) {
            return UssdResult(
                code = code,
                responseMessage = "Failed to open phone dialer: ${e.localizedMessage ?: "Unknown error"}",
                isSuccess = false,
                failureReason = e.javaClass.simpleName,
                executionType = ExecutionType.DIALER_FALLBACK,
                timestamp = System.currentTimeMillis(),
                durationMs = 0,
                simName = simLabel
            )
        }
    }

    /**
     * Generates a simulated USSD response reflecting the selected SIM card.
     */
    suspend fun simulateUssd(code: String, selectedSim: SimAccountInfo? = null): UssdResult {
        val startTime = System.currentTimeMillis()
        delay(1100) // Realistic cellular handshake latency
        val duration = System.currentTimeMillis() - startTime
        val simName = selectedSim?.displayName ?: "SIM 1"
        val isSim2 = selectedSim?.slotIndex == 1

        val responseText = when (code.trim()) {
            "*31124*1#" -> if (isSim2) {
                """
                    USSD SERVICE RESPONSE (*31124*1#)
                    Carrier : Global Roam (SIM 2)
                    ─────────────────────────────────
                    Status         : Roaming Active
                    Subscriber ID  : 9841-31124-B
                    Plan           : Global Pass 30D
                    Balance        : €35.00 EUR
                    Data Remaining : 8.5 GB / 10 GB
                    Validity Ends  : 15-Nov-2026 23:59
                    Ref            : USSD-ROAM-31124-2
                    ─────────────────────────────────
                    Connected via Global Roam Partner.
                """.trimIndent()
            } else {
                """
                    USSD SERVICE RESPONSE (*31124*1#)
                    Carrier : Telecom 5G (SIM 1)
                    ─────────────────────────────────
                    Account Status : Active
                    Subscriber ID  : 9841-31124-A
                    Service Package: Ultra Data & Voice
                    Balance        : $48.75 USD
                    Data Remaining : 14.8 GB of 20 GB
                    Validity Ends  : 26-Nov-2026 23:59
                    Ref            : USSD-31124-1-OK
                    ─────────────────────────────────
                    Thank you for choosing Telecom Mobile!
                """.trimIndent()
            }

            "*123#" -> """
                MAIN BALANCE INQUIRY ($simName)
                ─────────────────────────────────
                Main Balance: ${if (isSim2) "€12.80" else "$24.50"}
                Free Minutes: ${if (isSim2) "80 roaming mins" else "200 local mins"}
                Validity    : 30 days remaining
            """.trimIndent()

            "*100#" -> """
                TELECOM SELF-CARE MENU ($simName)
                ─────────────────────────────────
                1. Check Balance & Usage
                2. Buy Data Bundles
                3. Roaming Packs
                4. Value Added Services
                5. Customer Care Support
            """.trimIndent()

            "*#06#" -> """
                DEVICE IDENTIFICATION
                ─────────────────────────────────
                IMEI 1 (SIM 1 Slot): 864209041234567
                IMEI 2 (SIM 2 Slot): 864209041234568
                SN    : 9A32BC44EF01
            """.trimIndent()

            else -> """
                USSD RESPONSE FOR $code
                Executed on: $simName
                ─────────────────────────────────
                Status: Query Processed Successfully
                Carrier Code: 200 OK
                Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}
                Message: Service option active for code $code.
            """.trimIndent()
        }

        return UssdResult(
            code = code,
            responseMessage = responseText,
            isSuccess = true,
            failureReason = null,
            executionType = ExecutionType.SIMULATION,
            timestamp = System.currentTimeMillis(),
            durationMs = duration,
            simName = simName
        )
    }
}
