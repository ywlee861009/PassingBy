package com.kero.passingby.ui.journey

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRecoveryTest {
    @Test fun firstRequestAndRetryAfterOneDenialCanUseThePermissionDialog() {
        assertEquals(NotificationRecoveryAction.REQUEST_PERMISSION,
            notificationRecoveryAction(35, permissionGranted = false, askedBefore = false, showRationale = false))
        assertEquals(NotificationRecoveryAction.REQUEST_PERMISSION,
            notificationRecoveryAction(35, permissionGranted = false, askedBefore = true, showRationale = true))
    }

    @Test fun aBlockedOrDismissedRequestCanAlwaysBeRecoveredThroughSettings() {
        assertEquals(NotificationRecoveryAction.OPEN_SETTINGS,
            notificationRecoveryAction(35, permissionGranted = false, askedBefore = true, showRationale = false))
    }

    @Test fun channelBlocksAndOlderAndroidVersionsUseTheirSystemSettings() {
        assertEquals(NotificationRecoveryAction.OPEN_SETTINGS,
            notificationRecoveryAction(35, permissionGranted = true, askedBefore = true, showRationale = false))
        assertEquals(NotificationRecoveryAction.OPEN_SETTINGS,
            notificationRecoveryAction(32, permissionGranted = false, askedBefore = true, showRationale = false))
    }
}
