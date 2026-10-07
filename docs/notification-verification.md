# Provider notification verification

US-20 has two distinct verification boundaries. JVM/Cucumber proof exercises the
owned account/session/registration decisions. Instrumented Android tests exercise
real permissions, native channels/display, the Android/Hilt Messaging service,
immutable notification PendingIntents and the existing navigation/screens.
Both use deterministic external ports and simulated receipt. Neither proves
Firebase transport delivery.

## Deterministic Android verification

Use the repository Delivery final User Story verification workflow after the
reviewed final commit is pushed. The full instrumented suite uses
`ProviderNotificationTestModule`: synthetic token/installation HTTP ports and
isolated installation state, while retaining the production notification display,
clock, local session, service, Activity and navigation. Firebase client
configuration, when present, does not turn these tests into real delivery tests.
Before FirebaseInitProvider runs, HiltTestRunner temporarily disables the pinned
SDK auto-init and metrics-export preferences, restoring their prior values when
instrumentation finishes. This prevents SDK token acquisition outside Hilt from
contacting Firebase merely because a real client file was later configured.
No test runtime switch or exported debug receiver is added to the app.

The native fixture sends data through the pinned Firebase SDK's
`FcmBroadcastProcessor`, which binds and dispatches the actual Android/Hilt
`ProviderFirebaseMessagingService`. Every dispatch has a new SDK message ID;
replay cases preserve the business event ID so production persisted deduplication
is tested. Native display expiry uses real time. Tests dispatch the actual
immutable content PendingIntent and emulate SystemUI row auto-cancellation,
then assert real screens, Back and recreation.

Notification state defaults to permission already requested for unrelated
regression tests. Permission tests explicitly reset it before Activity creation.
On Android 13+, tests grant/deny the actual system prompt. AOSP's notification-only
[TestApi](https://android.googlesource.com/platform/frameworks/base/+/c39d2cf4662903fc19f6550ec2fd468d25a19adb/core/java/android/permission/PermissionManager.java)
revokes POST_NOTIFICATIONS without killing the instrumentation UID, under a
short-lived shell identity adopting both `REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL`
and `REVOKE_RUNTIME_PERMISSIONS`, always released. API34 CI confirmed the TestApi
exists; its underlying revoke additionally requires the ordinary revoke permission.
Availability is verified by execution; reflection/permission failure is a failed prerequisite,
never substituted by a fake permission result. Ordinary runtime-permission
revocation would kill instrumentation. Permission grants and user-set/fixed flags
are restored, SDK service bindings are unbound, Activities are closed, and the
native channel-disable test restores its original channel setting. Native Activity
instances are launched with Instrumentation and owned through the lifecycle monitor:
warm intents legitimately replace the Activity intent, which would make an
ActivityScenario launch-intent tracker ignore later lifecycle events. Cleanup
always attempts every owned Activity/service/permission boundary and retains
failures. Background/resume uses real task movement and a foreground app launch;
recreation keeps the Activity's actual intent. System Back requires the actual
returned Activity/screen, rather than a synthetic lifecycle callback. Android
version-specific coverage requires an OS supporting that API; API24/25 settings
fallback additionally has device-free Robolectric proof.

## Real API → FCM → phone verification, pending infrastructure

The human infrastructure owner must configure a matching Firebase project and
real flavor client configuration, enable API sending authorization, and provide
a Google Play-capable phone. App Distribution setup alone does not provide
Messaging configuration. Never commit client/server credentials or fabricate
configuration to make these checks pass.

After configuration, install the matching real build, authenticate as a provider,
and grant notification permission. Confirm the backend acknowledges the current
installation binding without exposing its token, secret or session credentials.
Use actual consumer/service operations against the reviewed API to generate:

- A consumer message, including text and media journeys, while the provider reads
  another screen or the app is closed/locked.
- A deposit-approved accepted proposal, a turn in the next 24 hours, and a
  final-payment approval whose persisted order detail reports the authoritative
  paid status.

Verify the physical phone receives each real notice once, uses the correct native
channel, shows only generic text on the lock screen, and opens the current
conversation/order with correct Back behavior. Also verify the active resumed
chat refreshes without an extra alert; permission denial, expiry, logout and
account replacement remain safe. Exercise offline logout followed by the next
real authenticated login so server rebinding is verified across the real API.

Record Android/OS/build, notice kind and observed outcome without payloads or
credentials. If Firebase client setup, API authorization or real transport is
missing, keep that check pending with its owner. Passed deterministic gates do
not establish real delivery or full US closure. Run Delivery finalization only
after all required evidence, including real delivery, is complete.
