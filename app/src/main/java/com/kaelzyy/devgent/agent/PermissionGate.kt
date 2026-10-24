package com.kaelzyy.devgent.agent

import com.kaelzyy.devgent.tools.DiffPreview
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PermissionDecision { DENY, ALLOW_ONCE, ALLOW_SESSION }

data class PermissionRequest(
    val tool: String,
    val path: String,
    val isNewFile: Boolean,
    val diff: DiffPreview?,
)

class PermissionGate {

    private val _pending = MutableStateFlow<PermissionRequest?>(null)
    val pending: StateFlow<PermissionRequest?> = _pending

    private var waiter: CompletableDeferred<PermissionDecision>? = null

    var sessionAllowAllEdits = false
        private set

    suspend fun request(request: PermissionRequest): PermissionDecision {
        if (sessionAllowAllEdits) return PermissionDecision.ALLOW_SESSION
        val deferred = CompletableDeferred<PermissionDecision>()
        waiter = deferred
        _pending.value = request
        try {
            return deferred.await()
        } finally {
            waiter = null
            _pending.value = null
        }
    }

    fun decide(decision: PermissionDecision) {
        if (decision == PermissionDecision.ALLOW_SESSION) {
            sessionAllowAllEdits = true
        }
        waiter?.complete(decision)
    }
}
