package com.loresuelvo.serviceprovider.acceptance.notifications

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FcmBroadcastProcessor
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.auth.*
import com.loresuelvo.serviceprovider.domain.account.*
import com.loresuelvo.serviceprovider.domain.activity.*
import com.loresuelvo.serviceprovider.domain.auth.*
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.conversation.*
import com.loresuelvo.serviceprovider.domain.notifications.*
import com.loresuelvo.serviceprovider.domain.paymentaccount.*
import com.loresuelvo.serviceprovider.platform.notifications.AndroidNotificationDisplay
import com.loresuelvo.serviceprovider.platform.notifications.ProviderFirebaseMessagingService
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.profile.PROVIDER_PROFILE_DATA_TAG
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.*

/** Native receipt is simulated through the real SDK/Android/Hilt service, never claimed as FCM delivery. */
internal class NativeNotificationHarness(val compose: ComposeTestRule) : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val manager = context.getSystemService(NotificationManager::class.java)
    val entry = EntryPointAccessors.fromApplication(context, NativeNotificationEntryPoint::class.java)
    val sessions = entry.sessions()
    val store = entry.store()
    val account = entry.account()
    val conversations = entry.conversations()
    val orders = entry.orders()
    val installations = entry.installations()
    val display = entry.display()
    private val originallyGranted = hasPermission()
    private val originalPermissionFlags = if (Build.VERSION.SDK_INT >= 33) shell("dumpsys package ${context.packageName}")
        .lineSequence().firstOrNull { it.contains(Manifest.permission.POST_NOTIFICATIONS) && it.contains("flags=") }.orEmpty() else ""
    private val connections = mutableSetOf<ServiceConnection>()
    private val serviceContext = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean =
            super.bindService(intent, connection, flags).also { if (it) synchronized(connections) { connections += connection } }
    }

    init {
        assertSame(sessions, entry.authSessions())
        FcmBroadcastProcessor.reset()
        grantPermission()
        manager.cancelAll()
        display.createChannels()
        setProvider(7)
        entry.payment().outcome = PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.PENDING))
        conversations.outcome = ConversationsOutcome.Success(listOf(Conversation(42, ConversationStatus.Active,
            ConversationCounterpart(3, "Ana", "Perez", null), null, 1)))
        conversations.detailOutcome = ConversationDetailOutcome.Success(detail())
        orders.detail = WorkOrderDetailOutcome.Success(order())
        sessions.saveSession(AuthSession(User("provider-native", "p@example.test"), "synthetic-native-session"))
    }

    fun setProvider(id: Int) {
        account.outcome = CurrentAccountOutcome.Success(CurrentAccount.Provider(id, "Carlos", "Gomez", "p@example.test", Category(1, "Service"), null))
    }
    fun detail(messages: List<ConversationMessage> = emptyList()) = ConversationDetail(42, ConversationStatus.Active,
        ConversationCounterpart(3, "Ana", "Perez", null), messages, messages.lastOrNull()?.createdOnEpochMillis ?: 1)
    fun order(status: WorkOrderStatus = WorkOrderStatus.Scheduled) = WorkOrderDetail(55, 10, 3, 7, 123456,
        System.currentTimeMillis() + 3_600_000, "Current authorized service detail", status, null,
        paidOn = if (status == WorkOrderStatus.Paid) System.currentTimeMillis() else null)
    fun launch(intent: Intent = Intent(context, MainActivity::class.java), awaitResumed: Boolean = true) {
        // Warm notification/payment intents replace Activity.intent. ActivityScenario's launch-intent
        // matcher then stops tracking it, so this native fixture owns the actual lifecycle instances.
        instrumentation.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // A real permission dialog pauses this Activity until the test answers it.
        if (awaitResumed) { awaitActivity(); compose.waitForIdle() }
    }
    fun waitRegistered() = compose.waitUntil(10_000) { store.read().binding?.let { it.active && it.acknowledged } == true }
    fun closeActivity() {
        instrumentation.runOnMainSync {
            appActivities().forEach(Activity::finish)
        }
        compose.waitUntil(10_000) {
            var closed = false
            instrumentation.runOnMainSync { closed = appActivities().isEmpty() }
            closed
        }
    }
    private fun appActivities() = Stage.values().filter { it != Stage.DESTROYED }.flatMap {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it).filterIsInstance<MainActivity>()
    }.distinct()
    fun background() {
        instrumentation.runOnMainSync { activity().moveTaskToBack(true) }
        compose.waitUntil(10_000) {
            var stopped = false
            instrumentation.runOnMainSync {
                stopped = resumedActivities().isEmpty() && ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.STOPPED).any { it is MainActivity }
            }
            stopped
        }
    }
    fun resume() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        awaitActivity(); compose.waitForIdle()
    }
    fun recreate() {
        lateinit var old: MainActivity
        instrumentation.runOnMainSync { old = activity(); old.recreate() }
        compose.waitUntil(10_000) {
            var replaced = false
            instrumentation.runOnMainSync { replaced = old.isDestroyed && resumedActivities().any { it !== old } }
            replaced
        }
        compose.waitForIdle()
    }
    private fun resumedActivities() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
    fun awaitActivity() = compose.waitUntil(10_000) {
        var resumed = false
        instrumentation.runOnMainSync { resumed = resumedActivities().isNotEmpty() }
        resumed
    }
    fun activity(): MainActivity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        .filterIsInstance<MainActivity>().single()
    fun assertOneActivity() = instrumentation.runOnMainSync {
        assertEquals(1, ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().size)
    }
    fun openProfile() {
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(PROVIDER_PROFILE_DATA_TAG).fetchSemanticsNodes().size == 1 }
    }
    fun assertHome() = compose.waitUntil(10_000) { compose.onAllNodesWithText(context.getString(R.string.provider_home_title)).fetchSemanticsNodes().isNotEmpty() }
    fun payload(type: String = MESSAGE, resourceId: Int = 42, event: String? = null, expires: Long = System.currentTimeMillis() + 120_000): Map<String, String> {
        val state = store.read(); val binding = checkNotNull(state.binding)
        val service = type != MESSAGE
        val (title, body) = when (type) {
            ACCEPTED -> "Propuesta aceptada" to "Se confirmó una contratación."
            REMINDER -> "Turno próximo" to "Tenés un servicio programado dentro de las próximas 24 horas."
            PAID -> "Pago final confirmado" to "Se aprobó el pago del saldo de tu servicio."
            else -> "Nuevo mensaje" to "Tenés un nuevo mensaje en LoResuelvo."
        }
        return mapOf("version" to "1", "event_id" to (event ?: if (service) "notification:$type:123" else "message:123:${binding.recipientId}"),
            "type" to type, "resource_type" to if (service) "work_order" else "conversation", "resource_id" to resourceId.toString(),
            "destination" to if (service) "work_order" else "conversation", "recipient_user_id" to binding.recipientId.toString(),
            "recipient_app" to "provider", "installation_id" to state.id, "binding_id" to binding.id,
            "title" to title, "body" to body, "expires_at" to Instant.ofEpochMilli(expires).toString())
    }
    fun deliver(payload: Map<String, String>) {
        val intent = Intent("com.google.android.c2dm.intent.RECEIVE").apply {
            payload.forEach { (key, value) -> putExtra(key, value) }
            // SDK replay suppression must never substitute for our persisted business-event deduplication.
            putExtra("google.message_id", UUID.randomUUID().toString())
        }
        assertEquals(-1, Tasks.await(FcmBroadcastProcessor(serviceContext).process(intent), 15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }
    fun deliverTokenCallback() {
        val intent = Intent("com.google.firebase.messaging.NEW_TOKEN").putExtra("token", "outdated-callback-token")
        assertEquals(-1, Tasks.await(FcmBroadcastProcessor(serviceContext).process(intent), 15, TimeUnit.SECONDS))
    }
    fun notices(): List<StatusBarNotification> = manager.activeNotifications.toList()
    fun waitNotice(): StatusBarNotification {
        compose.waitUntil(10_000) { notices().size == 1 }
        return notices().single()
    }
    fun tap(pending: PendingIntent = waitNotice().notification.contentIntent) {
        var previousActivity: MainActivity? = null
        var previousIntent: Intent? = null
        instrumentation.runOnMainSync {
            previousActivity = resumedActivities().singleOrNull()
            previousIntent = previousActivity?.intent
        }
        val options = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle() else null
        pending.send(context, 0, null, null, null, null, options)
        // SystemUI auto-cancels the tapped row; dispatching its native PendingIntent alone does not.
        notices().filter { it.notification.contentIntent == pending }.forEach { manager.cancel(it.tag, it.id) }
        // Cold delivery creates an Activity; warm onNewIntent replaces its actual Intent.
        compose.waitUntil(10_000) {
            var arrived = false
            instrumentation.runOnMainSync {
                arrived = resumedActivities().any { it !== previousActivity || it.intent !== previousIntent }
            }
            arrived
        }
        compose.waitForIdle()
    }
    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun grantPermission() { if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS) }
    fun revokePermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            revokeWithoutKill()
            shell("pm clear-permission-flags ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS} user-set user-fixed")
        }
    }
    private fun revokeWithoutKill() {
        // AOSP's notification-only TestApi avoids killing the instrumentation UID during native permission tests.
        // https://android.googlesource.com/platform/frameworks/base/+/c39d2cf4662903fc19f6550ec2fd468d25a19adb/core/java/android/permission/PermissionManager.java
        val automation = instrumentation.uiAutomation
        automation.adoptShellPermissionIdentity("android.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL", "android.permission.REVOKE_RUNTIME_PERMISSIONS")
        try {
            val manager = checkNotNull(context.getSystemService("permission"))
            manager.javaClass.getMethod("revokePostNotificationPermissionWithoutKillForTest", String::class.java, Int::class.javaPrimitiveType)
                .invoke(manager, context.packageName, android.os.Process.myUid() / 100000)
        } finally { automation.dropShellPermissionIdentity() }
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    fun systemBack() { shell("input keyevent KEYCODE_BACK") }
    fun systemTap(x: Int, y: Int) { shell("input tap $x $y") }
    override fun close() {
        var failure: Throwable? = null
        fun cleanup(block: () -> Unit) {
            try { block() } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        installations.removalGate?.complete(Unit)
        cleanup { closeActivity() }
        val bound = synchronized(connections) { connections.toList().also { connections.clear() } }
        bound.forEach { connection -> cleanup { context.unbindService(connection) } }
        cleanup { context.stopService(Intent(context, ProviderFirebaseMessagingService::class.java)); instrumentation.waitForIdleSync() }
        cleanup { FcmBroadcastProcessor.reset() }
        cleanup { sessions.clearSession() }
        cleanup { manager.cancelAll() }
        cleanup { if (Build.VERSION.SDK_INT >= 33) {
            if (originallyGranted) grantPermission() else revokeWithoutKill()
            shell("pm clear-permission-flags ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS} user-set user-fixed")
            val flags = listOf("USER_SET" to "user-set", "USER_FIXED" to "user-fixed").filter { originalPermissionFlags.contains(it.first) }.map { it.second }
            if (flags.isNotEmpty()) shell("pm set-permission-flags ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS} ${flags.joinToString(" ")}")
        } }
        failure?.let { throw it }
    }
    companion object {
        const val MESSAGE = "conversation.message.created"
        const val ACCEPTED = "service_proposal_accepted"
        const val REMINDER = "work_order_close_to_scheduled_time"
        const val PAID = "work_order_final_payment_approved"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NativeNotificationEntryPoint {
    fun sessions(): ProviderSignupSessionStore
    fun authSessions(): AuthSessionStore
    fun store(): ProviderNotificationTestStore
    fun account(): ProviderSignupCurrentAccountRepository
    fun conversations(): ProviderSignupConversationRepository
    fun orders(): ProviderSignupWorkOrderRepository
    fun installations(): ProviderNotificationTestInstallations
    fun display(): AndroidNotificationDisplay
    fun local(): NotificationLocalSession
    fun payment(): ProviderSignupPaymentAccountRepository
    fun token(): ProviderNotificationTestToken
}
