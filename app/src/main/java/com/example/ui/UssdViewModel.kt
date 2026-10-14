package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SimAccountInfo
import com.example.data.model.UssdResult
import com.example.telephony.UssdManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UssdUiState(
    val currentCode: String = "*31124*1#",
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val latestResult: UssdResult? = null,
    val isSimulationMode: Boolean = true,
    val availableSims: List<SimAccountInfo> = emptyList(),
    val selectedSim: SimAccountInfo? = null,
    val hasCallPermission: Boolean = false,
    val hasReadPhoneStatePermission: Boolean = false,
    val snackbarMessage: String? = null
)

class UssdViewModel(application: Application) : AndroidViewModel(application) {

    private val ussdManager = UssdManager(application)

    private val _uiState: MutableStateFlow<UssdUiState>

    init {
        val callPerm = ussdManager.hasCallPhonePermission()
        val readStatePerm = ussdManager.hasReadPhoneStatePermission()
        val info = ussdManager.getTelephonyInfo()
        val isSimMode = info.isEmulatorOrSimless
        val sims = ussdManager.getAvailableSimCards(isSimMode)
        val defaultSim = sims.firstOrNull()

        _uiState = MutableStateFlow(
            UssdUiState(
                hasCallPermission = callPerm,
                hasReadPhoneStatePermission = readStatePerm,
                isSimulationMode = isSimMode,
                availableSims = sims,
                selectedSim = defaultSim
            )
        )
    }

    val uiState: StateFlow<UssdUiState> = _uiState.asStateFlow()

    fun refreshTelephonyState() {
        val callPerm = ussdManager.hasCallPhonePermission()
        val readStatePerm = ussdManager.hasReadPhoneStatePermission()
        val sims = ussdManager.getAvailableSimCards(_uiState.value.isSimulationMode)
        val currentSelected = _uiState.value.selectedSim
        val updatedSelected = sims.find { it.subscriptionId == currentSelected?.subscriptionId }
            ?: sims.firstOrNull()

        _uiState.update {
            it.copy(
                hasCallPermission = callPerm,
                hasReadPhoneStatePermission = readStatePerm,
                availableSims = sims,
                selectedSim = updatedSelected
            )
        }
    }

    fun selectSim(sim: SimAccountInfo) {
        _uiState.update {
            it.copy(
                selectedSim = sim,
                snackbarMessage = "Selected ${sim.slotLabel} (${sim.carrierName})"
            )
        }
    }

    fun onCodeChanged(newCode: String) {
        _uiState.update { it.copy(currentCode = newCode) }
    }

    fun toggleSimulationMode(enabled: Boolean) {
        val sims = ussdManager.getAvailableSimCards(enabled)
        val updatedSelected = sims.firstOrNull()
        _uiState.update {
            it.copy(
                isSimulationMode = enabled,
                availableSims = sims,
                selectedSim = updatedSelected,
                snackbarMessage = if (enabled) "Switched to Test Simulation" else "Switched to Real Cellular SIM"
            )
        }
    }

    fun executeTargetUssd() {
        val codeToRun = _uiState.value.currentCode.trim().ifBlank { "*31124*1#" }
        val activeSim = _uiState.value.selectedSim
        val simName = activeSim?.slotLabel ?: "SIM 1"

        _uiState.update {
            it.copy(
                currentCode = codeToRun,
                isLoading = true,
                loadingMessage = "Connecting on $simName for $codeToRun...",
                latestResult = null // Clear previous result while loading
            )
        }

        if (_uiState.value.isSimulationMode) {
            viewModelScope.launch {
                val result = ussdManager.simulateUssd(codeToRun, activeSim)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        latestResult = result
                    )
                }
            }
        } else {
            if (!ussdManager.hasCallPhonePermission()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        snackbarMessage = "Phone permission required to run USSD."
                    )
                }
                return
            }

            ussdManager.sendUssdRequest(codeToRun, activeSim) { result ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        latestResult = result
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
