package com.kero.passingby.billing

enum class RestoreStatus { IDLE, CHECKING, RESTORED, EMPTY, PENDING, FAILED }
enum class PurchaseSource { QUERY, UPDATE }

/** 메인 스레드에서 사용. 연결·조회 요청을 합치고, 시간 초과 뒤 도착한 오래된 콜백을 구분한다. */
internal class PurchaseRefresh {
    data class Request(val id: Long, val connect: Boolean)
    private var sequence = 0L
    private var active: Request? = null
    private var manual = false
    val connecting: Boolean get() = active?.connect == true
    val restoring: Boolean get() = manual

    fun begin(manual: Boolean, ready: Boolean): Request? {
        this.manual = this.manual || manual
        if (active != null) return null
        return Request(++sequence, !ready).also { active = it }
    }

    fun connected(id: Long): Boolean {
        if (active?.id != id || !connecting) return false
        active = Request(id, false)
        return true
    }

    fun querying(id: Long): Boolean = active?.let { it.id == id && !it.connect } == true

    fun finish(id: Long, success: Boolean, hasPurchases: Boolean = false, hasPending: Boolean = false): RestoreStatus? {
        if (active?.id != id) return null
        val status = if (!manual) null else when {
            !success -> RestoreStatus.FAILED
            hasPending -> RestoreStatus.PENDING
            hasPurchases -> RestoreStatus.RESTORED
            else -> RestoreStatus.EMPTY
        }
        active = null
        manual = false
        return status
    }

    fun cancel() { active = null; manual = false }
}

internal enum class PaymentState { PURCHASED, PENDING, OTHER }
internal data class PurchaseRecord(val products: List<String>, val state: PaymentState, val acknowledged: Boolean)
internal data class PurchaseBatch(val owned: Set<String>, val pending: Set<String>, val acknowledgeAt: List<Int>)

/** 조회는 전체 목록, 이벤트는 부분 목록이다. 어느 경우에도 기존 권한을 삭제하는 결과를 만들지 않는다. */
internal fun classifyPurchases(records: List<PurchaseRecord>, known: Set<String>): PurchaseBatch {
    val owned = mutableSetOf<String>()
    val pending = mutableSetOf<String>()
    val acknowledge = mutableListOf<Int>()
    records.forEachIndexed { index, record ->
        val products = record.products.filter { it in known }
        when (record.state) {
            PaymentState.PURCHASED -> {
                owned += products
                if (products.isNotEmpty() && !record.acknowledged) acknowledge += index
            }
            PaymentState.PENDING -> pending += products
            PaymentState.OTHER -> Unit
        }
    }
    return PurchaseBatch(owned, pending - owned, acknowledge)
}
