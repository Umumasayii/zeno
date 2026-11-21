package com.zeno.assistant

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards Z-028 / ADR-001: the production app must not be able to reach the network, and in M0 it
 * requests only RECORD_AUDIO (M2a push-to-talk). Later milestones add permissions here deliberately, one at a time.
 */
@RunWith(AndroidJUnit4::class)
class ManifestPolicyTest {

    private val requested: Set<String> by lazy {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.toSet().orEmpty()
    }

    @Test fun `app never requests INTERNET`() {
        assertFalse("INTERNET must never be requested", "android.permission.INTERNET" in requested)
        assertFalse("ACCESS_NETWORK_STATE is not needed", "android.permission.ACCESS_NETWORK_STATE" in requested)
    }

    @Test fun `only RECORD_AUDIO is requested, besides the AndroidX receiver-guard permission`() {
        // androidx.core adds a signature-level DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION scoped to our own package.
        val unexpected = requested.filterNot { it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
        assertEquals(listOf("android.permission.RECORD_AUDIO"), unexpected)
    }

    @Test fun `cloud backup is disabled`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }
}
