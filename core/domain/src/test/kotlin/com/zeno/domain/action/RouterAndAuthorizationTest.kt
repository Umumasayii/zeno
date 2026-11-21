package com.zeno.domain.action

import com.zeno.domain.intent.AssistantIntent
import com.zeno.domain.intent.ContactRef
import com.zeno.domain.intent.IntentKind
import com.zeno.domain.policy.AuthorizationGate
import com.zeno.domain.policy.CommandSource
import com.zeno.domain.policy.DenyReason
import com.zeno.domain.policy.PolicyDecision
import com.zeno.domain.policy.UserApproval
import com.zeno.domain.root.RootBridge
import com.zeno.domain.root.RootOperation
import com.zeno.testing.FakeActionAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterAndAuthorizationTest {

    private val sms = AssistantIntent.SendSms(ContactRef("Mum"), "hi")

    @Test fun `deny and clarify never authorize`() {
        assertNull(AuthorizationGate.authorize(sms, PolicyDecision.Deny(DenyReason.UNKNOWN_SPEAKER), CommandSource.VOICE))
        assertNull(AuthorizationGate.authorize(sms, PolicyDecision.Clarify("?"), CommandSource.VOICE))
    }

    @Test fun `confirm needs a confirmation for the same intent`() {
        val confirm = PolicyDecision.Confirm("send?")
        assertNull(AuthorizationGate.authorize(sms, confirm, CommandSource.VOICE))
        val edited = sms.copy(body = "changed")
        assertNull(AuthorizationGate.authorize(edited, confirm, CommandSource.VOICE, UserApproval.Confirmed(sms)))
        assertNotNull(AuthorizationGate.authorize(sms, confirm, CommandSource.VOICE, UserApproval.Confirmed(sms)))
    }

    @Test fun `device credential cannot be satisfied by a spoken confirmation`() {
        val d = PolicyDecision.RequireDeviceCredential("unlock")
        val i = AssistantIntent.RequestUnlock
        assertNull(AuthorizationGate.authorize(i, d, CommandSource.VOICE, UserApproval.Confirmed(i)))
        assertNotNull(AuthorizationGate.authorize(i, d, CommandSource.VOICE, UserApproval.DeviceCredentialPassed))
    }

    @Test fun `router prefers Android API over accessibility over root`() = runTest {
        val root = FakeActionAdapter(ExecutionRoute.ROOT, setOf(IntentKind.LOCK_SCREEN))
        val acc = FakeActionAdapter(ExecutionRoute.ACCESSIBILITY, setOf(IntentKind.LOCK_SCREEN))
        val api = FakeActionAdapter(ExecutionRoute.ANDROID_API, setOf(IntentKind.LOCK_SCREEN))
        val router = OrderedActionRouter(listOf(root, acc, api))
        val auth = AuthorizationGate.authorize(AssistantIntent.LockScreen, PolicyDecision.Allow, CommandSource.VOICE)!!

        router.route(auth)
        assertEquals(1, api.executed.size)

        api.availability = Availability.Unavailable("device admin not enabled")
        router.route(auth)
        assertEquals(1, acc.executed.size)
        assertEquals(0, root.executed.size)
    }

    @Test fun `failed execution does not fall through to another route`() = runTest {
        val api = FakeActionAdapter(ExecutionRoute.ANDROID_API, setOf(IntentKind.SEND_SMS), result = ActionResult.OutcomeUnknown("no carrier callback"))
        val acc = FakeActionAdapter(ExecutionRoute.ACCESSIBILITY, setOf(IntentKind.SEND_SMS))
        val auth = AuthorizationGate.authorize(sms, PolicyDecision.Allow, CommandSource.VOICE)!!
        val result = OrderedActionRouter(listOf(api, acc)).route(auth)
        assertTrue(result is ActionResult.OutcomeUnknown)
        assertEquals(0, acc.executed.size)
    }

    @Test fun `nothing available reports every reason`() = runTest {
        val api = FakeActionAdapter(ExecutionRoute.ANDROID_API, setOf(IntentKind.HOME), availability = Availability.Unavailable("n/a"))
        val acc = FakeActionAdapter(ExecutionRoute.ACCESSIBILITY, setOf(IntentKind.HOME), availability = Availability.Unavailable("service off"))
        val auth = AuthorizationGate.authorize(AssistantIntent.Home, PolicyDecision.Allow, CommandSource.VOICE)!!
        val r = OrderedActionRouter(listOf(api, acc)).route(auth) as ActionResult.Unavailable
        assertEquals(2, r.reasons.size)
    }

    /** docs/06 + M7 gate: "verify no command string can enter shell". */
    @Test fun `root bridge contract accepts no strings`() {
        val stringy = setOf(String::class.java, CharSequence::class.java, Array<String>::class.java, List::class.java)
        for (m in RootBridge::class.java.declaredMethods) {
            for (p in m.parameterTypes) assertTrue("${m.name} takes ${p.simpleName}", p !in stringy)
        }
        // Allowlist size is reviewed on purpose; changing it must update this test and docs/06.
        assertEquals(setOf("POWER_OFF", "REBOOT"), RootOperation.entries.map { it.name }.toSet())
    }
}
