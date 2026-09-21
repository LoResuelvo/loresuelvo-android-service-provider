package com.loresuelvo.serviceprovider.platform.identity

import android.app.Activity
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationCredential
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.didit.sdk.Configuration
import me.didit.sdk.DiditSdk
import me.didit.sdk.DiditSdkState
import me.didit.sdk.VerificationError
import me.didit.sdk.VerificationResult
import me.didit.sdk.core.localization.SupportedLanguage

class DiditIdentityVerificationLauncher @Inject constructor() : IdentityVerificationLauncher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activity = WeakReference<Activity>(null)
    private var active = false
    private var launched = false
    private var callback: ((IdentityVerificationResult) -> Unit)? = null

    init {
        scope.launch {
            DiditSdk.state.collectLatest { state ->
                when (state) {
                    DiditSdkState.Ready -> launchUiOnce()
                    is DiditSdkState.Error -> finish(IdentityVerificationResult.Failed)
                    else -> Unit
                }
            }
        }
    }

    override fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
        if (DiditSdk.state.value == DiditSdkState.Ready) launchUiOnce()
    }

    override fun detach(activity: Activity) {
        if (this.activity.get() === activity) this.activity.clear()
    }

    override fun launch(
        credential: IdentityVerificationCredential,
        onResult: (IdentityVerificationResult) -> Unit,
    ) {
        if (active) return
        active = true
        launched = false
        callback = onResult
        DiditSdk.startVerification(
            token = credential.token,
            configuration = Configuration(
                languageLocale = SupportedLanguage.SPANISH,
                loggingEnabled = false,
            ),
        ) { result -> finish(result.toIdentityVerificationResult()) }
    }

    private fun launchUiOnce() {
        val currentActivity = activity.get() ?: return
        if (!active || launched) return
        launched = true
        DiditSdk.launchVerificationUI(currentActivity)
    }

    private fun finish(result: IdentityVerificationResult) {
        if (!active) return
        active = false
        launched = false
        callback?.also { callback = null }?.invoke(result)
    }
}

internal fun VerificationResult.toIdentityVerificationResult(): IdentityVerificationResult = when (this) {
    is VerificationResult.Completed -> IdentityVerificationResult.Completed
    is VerificationResult.Cancelled -> IdentityVerificationResult.Cancelled
    is VerificationResult.Failed -> when (error) {
        VerificationError.CameraAccessDenied -> IdentityVerificationResult.PermissionDenied
        else -> IdentityVerificationResult.Failed
    }
}
