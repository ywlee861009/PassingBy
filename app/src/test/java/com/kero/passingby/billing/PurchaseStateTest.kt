package com.kero.passingby.billing

import org.junit.Assert.*
import org.junit.Test

class PurchaseStateTest {
    @Test fun initialResumeAndManualRequestsShareOneQuery() {
        val state = PurchaseRefresh()
        val first = state.begin(manual = false, ready = false)!!
        assertTrue(first.connect)
        assertNull(state.begin(manual = false, ready = false))
        assertNull(state.begin(manual = true, ready = false))
        assertTrue(state.restoring)
        assertTrue(state.connected(first.id))
        assertFalse(state.connected(first.id))
        assertTrue(state.querying(first.id))
        assertEquals(RestoreStatus.RESTORED, state.finish(first.id, true, true))
        assertNull(state.finish(first.id, true, true))
        assertFalse(state.restoring)
    }

    @Test fun failureAndTimeoutAllowRetryAndIgnoreLateCallbacks() {
        val state = PurchaseRefresh()
        val failed = state.begin(true, false)!!
        assertEquals(RestoreStatus.FAILED, state.finish(failed.id, false))
        val retry = state.begin(true, true)!!
        assertFalse(retry.connect)
        assertFalse(state.connected(failed.id))
        assertFalse(state.querying(failed.id))
        assertNull(state.finish(failed.id, true, true))
        assertTrue(state.restoring)
        assertEquals(RestoreStatus.EMPTY, state.finish(retry.id, true))
    }

    @Test fun pendingOnlyRestoreShowsPendingInsteadOfEmptyOrSuccess() {
        val state = PurchaseRefresh()
        val request = state.begin(true, true)!!
        assertEquals(RestoreStatus.PENDING, state.finish(request.id, true, false, true))
    }

    @Test fun automaticQueriesHaveNoRestoreNotificationAndCloseInvalidatesRequest() {
        val state = PurchaseRefresh()
        val auto = state.begin(false, true)!!
        assertNull(state.finish(auto.id, true, true))
        val manual = state.begin(true, false)!!
        state.cancel()
        assertFalse(state.connected(manual.id))
        assertNull(state.finish(manual.id, false))
        assertFalse(state.restoring)
    }

    @Test fun pendingUnknownAndAlreadyAcknowledgedPurchasesNeverGetAcknowledged() {
        val batch = classifyPurchases(listOf(
            PurchaseRecord(listOf("a"), PaymentState.PENDING, false),
            PurchaseRecord(listOf("b"), PaymentState.PURCHASED, false),
            PurchaseRecord(listOf("c"), PaymentState.PURCHASED, true),
            PurchaseRecord(listOf("unknown"), PaymentState.PURCHASED, false),
            PurchaseRecord(listOf("d"), PaymentState.OTHER, false),
        ), setOf("a", "b", "c", "d"))
        assertEquals(setOf("b", "c"), batch.owned)
        assertEquals(setOf("a"), batch.pending)
        assertEquals(listOf(1), batch.acknowledgeAt)
    }

    @Test fun lateApprovalUnlocksAndEmptyOrPartialResultsPreserveExistingEntitlements() {
        var localOwned = setOf("existing")
        val known = setOf("existing", "new")
        val pending = classifyPurchases(listOf(PurchaseRecord(listOf("new"), PaymentState.PENDING, false)), known)
        localOwned += pending.owned
        assertEquals(setOf("existing"), localOwned)
        val approved = classifyPurchases(listOf(PurchaseRecord(listOf("new"), PaymentState.PURCHASED, false)), known)
        localOwned += approved.owned
        localOwned += classifyPurchases(emptyList(), known).owned
        assertEquals(setOf("existing", "new"), localOwned)
        assertEquals(listOf(0), approved.acknowledgeAt)
        assertTrue(approved.pending.isEmpty())
    }
}
