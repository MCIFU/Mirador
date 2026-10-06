package com.mcifu.mirador.domain.model

enum class SortField { NAME, DATE, SIZE, TYPE }

data class SortOrder(
    val field: SortField = SortField.NAME,
    val ascending: Boolean = true,
    val foldersFirst: Boolean = true,
)
