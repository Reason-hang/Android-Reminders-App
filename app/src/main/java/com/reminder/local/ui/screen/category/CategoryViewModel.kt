package com.reminder.local.ui.screen.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.local.data.repository.CategoryRepository
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val MAX_CATEGORY_COUNT = 20

val CATEGORY_COLOR_SWATCHES = listOf(
    "#4A90E2", "#7ED321", "#F5A623", "#D0021B",
    "#9013FE", "#50E3C2", "#B8860B", "#417505"
)

data class CategoryUiState(
    val categories: List<Category> = emptyList(),
    val reminderCounts: Map<Long, Int> = emptyMap(),
    val editingCategory: Category? = null,
    val pendingDeleteCategory: Category? = null,
    val showAddDialog: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val dialogState = MutableStateFlow(Triple<Category?, Category?, Boolean>(null, null, false))
    private val errorState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoryUiState> = combine(
        categoryRepository.observeAll(),
        reminderRepository.observeAll(),
        dialogState,
        errorState
    ) { categories, reminders, dialogs, error ->
        CategoryUiState(
            categories = categories,
            reminderCounts = reminders.mapNotNull { it.categoryId }.groupingBy { it }.eachCount(),
            editingCategory = dialogs.first,
            pendingDeleteCategory = dialogs.second,
            showAddDialog = dialogs.third,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryUiState())

    fun openAddDialog() {
        if (uiState.value.categories.size >= MAX_CATEGORY_COUNT) {
            errorState.value = "最多只能创建 $MAX_CATEGORY_COUNT 个分类"
            return
        }
        dialogState.value = dialogState.value.copy(third = true)
    }

    fun openEditDialog(category: Category) {
        dialogState.value = dialogState.value.copy(first = category)
    }

    fun dismissDialogs() {
        dialogState.value = Triple(null, null, false)
    }

    fun clearError() {
        errorState.value = null
    }

    fun addCategory(name: String, colorHex: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            errorState.value = "分类名称不能为空"
            return
        }
        if (uiState.value.categories.any { it.name == trimmed }) {
            errorState.value = "分类名称已存在"
            return
        }
        viewModelScope.launch {
            val nextOrder = (uiState.value.categories.maxOfOrNull { it.sortOrder } ?: -1) + 1
            categoryRepository.insert(
                Category(name = trimmed, colorHex = colorHex, sortOrder = nextOrder, isDefault = false)
            )
            dismissDialogs()
        }
    }

    fun updateCategory(category: Category, newName: String, newColor: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            errorState.value = "分类名称不能为空"
            return
        }
        if (uiState.value.categories.any { it.id != category.id && it.name == trimmed }) {
            errorState.value = "分类名称已存在"
            return
        }
        viewModelScope.launch {
            categoryRepository.update(category.copy(name = trimmed, colorHex = newColor))
            dismissDialogs()
        }
    }

    fun requestDelete(category: Category) {
        if (category.isDefault) {
            errorState.value = "内置分类不能删除"
            return
        }
        dialogState.value = dialogState.value.copy(second = category)
    }

    fun confirmDelete() {
        val category = dialogState.value.second ?: return
        viewModelScope.launch {
            categoryRepository.delete(category)
            dismissDialogs()
        }
    }
}
