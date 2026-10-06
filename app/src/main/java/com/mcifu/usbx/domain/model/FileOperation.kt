package com.mcifu.usbx.domain.model

enum class OperationKind { COPY, MOVE, DELETE }

enum class OperationStatus { PREPARING, RUNNING, DONE, FAILED, CANCELLED }

/** Estado de la operación en curso (copiar, mover o eliminar). Una sola a la vez. */
data class OperationProgress(
    val kind: OperationKind,
    val status: OperationStatus = OperationStatus.PREPARING,
    val totalItems: Int = 0,
    val doneItems: Int = 0,
    val totalBytes: Long = 0,
    val doneBytes: Long = 0,
    val currentName: String? = null,
    val failedNames: List<String> = emptyList(),
    val message: String? = null,
) {
    val isFinished: Boolean
        get() = status == OperationStatus.DONE || status == OperationStatus.FAILED || status == OperationStatus.CANCELLED
}
