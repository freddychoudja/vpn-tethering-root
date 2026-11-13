package com.freddychoudja.vpntethering.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freddychoudja.vpntethering.root.RootNetworkManager
import com.freddychoudja.vpntethering.service.TetheringForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val isActive: Boolean = false,
    val isBusy: Boolean = false,
    val statusText: String = "Inactif — activez Hotspot + VPN puis appuyez sur le bouton",
    val rootGranted: Boolean? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val networkManager = RootNetworkManager()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TetheringForegroundService.ACTION_STATUS) return
            val active = intent.getBooleanExtra(TetheringForegroundService.EXTRA_ACTIVE, false)
            val message = intent.getStringExtra(TetheringForegroundService.EXTRA_STATUS)
                ?: if (active) "Actif" else "Inactif"
            _uiState.update {
                it.copy(isActive = active, isBusy = false, statusText = message)
            }
        }
    }

    init {
        registerReceiver()
        requestRoot()
    }

    fun refreshRootStatus() {
        viewModelScope.launch {
            val granted = networkManager.isRootAvailable()
            applyRootStatus(granted)
        }
    }

    fun requestRoot() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isBusy = true, statusText = "Demande des droits root… (popup Magisk)")
            }
            val granted = networkManager.requestRootAccess()
            applyRootStatus(granted)
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun onPrimaryAction() {
        val state = _uiState.value
        if (state.isBusy) return

        if (state.rootGranted != true) {
            requestRoot()
            return
        }

        toggle()
    }

    fun toggle() {
        val state = _uiState.value
        if (state.isBusy) return

        _uiState.update { it.copy(isBusy = true) }
        val context = getApplication<Application>()

        if (state.isActive) {
            TetheringForegroundService.stop(context)
        } else {
            TetheringForegroundService.start(context, "Activation du routage…")
            _uiState.update { it.copy(statusText = "Activation en cours…") }
        }
    }

    private fun applyRootStatus(granted: Boolean) {
        _uiState.update {
            it.copy(
                rootGranted = granted,
                statusText = if (granted) {
                    if (it.isActive) it.statusText
                    else "Root OK — activez Hotspot + VPN puis Activer"
                } else {
                    "Root refusé. Ouvrez Magisk → Superutilisateur → autorisez cette app (ou retirez-la de la DenyList), puis réessayez."
                }
            )
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter(TetheringForegroundService.ACTION_STATUS)
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(statusReceiver, filter)
        }
    }

    override fun onCleared() {
        runCatching {
            getApplication<Application>().unregisterReceiver(statusReceiver)
        }
        super.onCleared()
    }
}
