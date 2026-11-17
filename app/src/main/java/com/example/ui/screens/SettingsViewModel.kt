package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BusinessSettings
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = SettingsRepository(application)

  val settingsState: StateFlow<BusinessSettings> = repository.observeSettings()
    .catch { emit(BusinessSettings()) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = BusinessSettings()
    )

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  fun saveSettings(
    businessName: String,
    address: String,
    phone: String,
    email: String,
    invoicePrefix: String,
    terms: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    viewModelScope.launch {
      _isSaving.value = true
      val updated = settingsState.value.copy(
        businessName = businessName.trim(),
        address = address.trim(),
        phone = phone.trim(),
        email = email.trim(),
        invoicePrefix = invoicePrefix.trim().ifBlank { "BILL" },
        termsAndConditions = terms.trim()
      )
      val res = repository.saveSettings(updated)
      _isSaving.value = false
      if (res.isSuccess) {
        onSuccess()
      } else {
        onError(res.exceptionOrNull()?.localizedMessage ?: "Failed to save settings")
      }
    }
  }
}
