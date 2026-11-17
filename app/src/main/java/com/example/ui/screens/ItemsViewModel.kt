package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SavedItem
import com.example.data.repository.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemsViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = ItemRepository(application)

  val itemsState: StateFlow<List<SavedItem>> = repository.observeItems()
    .catch { emit(emptyList()) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  fun saveOrUpdateFromBilling(
    name: String,
    rateMinor: Long
  ) {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return
    val normalized = SavedItem.normalize(cleanName)
    val existing = itemsState.value.firstOrNull { it.normalizedName == normalized }

    val item = SavedItem(
      id = existing?.id ?: "",
      name = cleanName,
      normalizedName = normalized,
      unitLabel = "pcs",
      rateMinor = maxOf(0L, rateMinor),
      defaultDiscountType = "FIXED",
      defaultDiscountValue = 0.0,
      hsnSac = "",
      taxRateBps = 0L,
      useCount = (existing?.useCount ?: 0L) + 1L
    )

    viewModelScope.launch {
      repository.upsertItem(item, existing)
    }
  }

  fun saveItem(
    existingItem: SavedItem? = null,
    name: String,
    rateMinor: Long,
    onSuccess: () -> Unit = {},
    onError: (String) -> Unit = {}
  ) {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
      onError("Enter item name")
      return
    }
    val normalized = SavedItem.normalize(cleanName)
    val matchedExisting = existingItem ?: itemsState.value.firstOrNull { it.normalizedName == normalized }

    val item = SavedItem(
      id = matchedExisting?.id ?: "",
      name = cleanName,
      normalizedName = normalized,
      unitLabel = "pcs",
      rateMinor = maxOf(0L, rateMinor),
      defaultDiscountType = "FIXED",
      defaultDiscountValue = 0.0,
      hsnSac = "",
      taxRateBps = 0L,
      useCount = matchedExisting?.useCount ?: 1L
    )

    viewModelScope.launch {
      _isSaving.value = true
      val res = repository.upsertItem(item, matchedExisting)
      _isSaving.value = false
      if (res.isSuccess) {
        onSuccess()
      } else {
        onError(res.exceptionOrNull()?.localizedMessage ?: "Failed to save item")
      }
    }
  }

  fun deleteItem(itemId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
    viewModelScope.launch {
      val res = repository.deleteItem(itemId)
      if (res.isSuccess) {
        onSuccess()
      } else {
        onError(res.exceptionOrNull()?.localizedMessage ?: "Failed to delete item")
      }
    }
  }
}
