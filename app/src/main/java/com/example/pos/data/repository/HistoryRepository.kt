package com.example.pos.data.repository

import androidx.room.withTransaction
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AggregatedSalesResult
import com.example.pos.data.db.AuditLogDao
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.InvoiceDao
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.ItemSalesSummary
import com.example.pos.data.db.OperatorDao
import com.example.pos.data.db.PaymentDao
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class HistorySortOption {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC,
    ORDER_ID_DESC,
    ORDER_ID_ASC
}

interface HistoryRepository {
    fun getInvoicesPaged(
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    fun getInvoicesPaged(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        sortOption: HistorySortOption,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    fun getInvoiceCount(query: String, startDate: Long, endDate: Long): Flow<Int>

    fun getInvoiceCount(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int>

    fun getAggregatedSales(
        query: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult>

    fun getAggregatedSales(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult>

    fun getTopSellingItems(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int = 5
    ): Flow<List<ItemSalesSummary>>

    fun getSalesByDay(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>>

    fun getSalesByMonth(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>>

    fun getSalesByYear(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>>

    fun getPeriodDue(
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        startDate: Long,
        endDate: Long
    ): Flow<Long>

    fun getOverallOutstandingDue(): Flow<Long>

    fun getLinesForOrderFlow(orderId: String): Flow<List<InvoiceLineEntity>>

    suspend fun getLinesForOrders(orderIds: List<String>): List<InvoiceLineEntity>

    suspend fun getInvoicesForExport(startDate: Long, endDate: Long, limit: Int = 200000): List<InvoiceEntity>

    suspend fun getInvoicesForExportFiltered(
        startDate: Long,
        endDate: Long,
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        limit: Int = 200000
    ): List<InvoiceEntity>

    suspend fun getMatchingOrderIds(query: String, limit: Int = 8): List<String>

    suspend fun updateInvoiceCustomerAndNote(orderId: String, customerInfo: String, footerNote: String): Int

    suspend fun updateInvoiceCustomerAndNoteAudited(orderId: String, customerInfo: String, footerNote: String): PosResult<Int>

    suspend fun settleInvoiceDue(orderId: String, additionalPaymentBdt: Long, method: String = "CASH"): PosResult<InvoiceEntity>

    suspend fun deleteOrder(orderId: String)

    suspend fun deleteOrderAudited(orderId: String): PosResult<Unit>

    fun getTotalInvoiceCountFlow(): Flow<Int>

    fun getTotalInvoiceLinesCountFlow(): Flow<Int>
}

class HistoryRepositoryImpl(
    private val invoiceDao: InvoiceDao,
    private val paymentDao: PaymentDao? = null,
    private val operatorDao: OperatorDao? = null,
    private val auditLogDao: AuditLogDao? = null,
    private val db: PosDatabase? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : HistoryRepository {

    override fun getInvoicesPaged(
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>> = invoiceDao.getInvoicesPagedFlow(query, startDate, endDate, limit, offset)

    override fun getInvoicesPaged(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        sortOption: HistorySortOption,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>> {
        val q = query.trim()
        val o = orderIdFilter.trim()
        val c = customerFilter.trim()
        return when (sortOption) {
            HistorySortOption.DATE_DESC -> invoiceDao.getInvoicesPagedDateDescFlow(q, o, c, startDate, endDate, limit, offset)
            HistorySortOption.DATE_ASC -> invoiceDao.getInvoicesPagedDateAscFlow(q, o, c, startDate, endDate, limit, offset)
            HistorySortOption.AMOUNT_DESC -> invoiceDao.getInvoicesPagedAmountDescFlow(q, o, c, startDate, endDate, limit, offset)
            HistorySortOption.AMOUNT_ASC -> invoiceDao.getInvoicesPagedAmountAscFlow(q, o, c, startDate, endDate, limit, offset)
            HistorySortOption.ORDER_ID_DESC -> invoiceDao.getInvoicesPagedOrderIdDescFlow(q, o, c, startDate, endDate, limit, offset)
            HistorySortOption.ORDER_ID_ASC -> invoiceDao.getInvoicesPagedOrderIdAscFlow(q, o, c, startDate, endDate, limit, offset)
        }
    }

    override fun getInvoiceCount(query: String, startDate: Long, endDate: Long): Flow<Int> =
        invoiceDao.getInvoiceCountFlow(query, startDate, endDate)

    override fun getInvoiceCount(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int> = invoiceDao.getInvoiceCountFilteredFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getAggregatedSales(
        query: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult> = invoiceDao.getAggregatedSalesFlow(query, startDate, endDate)

    override fun getAggregatedSales(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult> =
        invoiceDao.getAggregatedSalesFilteredFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getTopSellingItems(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int
    ): Flow<List<ItemSalesSummary>> =
        invoiceDao.getTopSellingItemsFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate, limit)

    override fun getSalesByDay(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> =
        invoiceDao.getSalesByDayFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getSalesByMonth(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> =
        invoiceDao.getSalesByMonthFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getSalesByYear(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> =
        invoiceDao.getSalesByYearFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getPeriodDue(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<Long> =
        invoiceDao.getPeriodDueFilteredFlow(query.trim(), orderIdFilter.trim(), customerFilter.trim(), startDate, endDate)

    override fun getOverallOutstandingDue(): Flow<Long> =
        invoiceDao.getOverallOutstandingDueFlow()

    override fun getLinesForOrderFlow(orderId: String): Flow<List<InvoiceLineEntity>> =
        invoiceDao.getLinesForOrderFlow(orderId)

    override suspend fun getLinesForOrders(orderIds: List<String>): List<InvoiceLineEntity> =
        if (orderIds.isEmpty()) emptyList() else invoiceDao.getLinesForOrders(orderIds)

    override suspend fun getInvoicesForExport(startDate: Long, endDate: Long, limit: Int): List<InvoiceEntity> =
        invoiceDao.getInvoicesForExport(startDate, endDate, limit)

    override suspend fun getInvoicesForExportFiltered(
        startDate: Long,
        endDate: Long,
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        limit: Int
    ): List<InvoiceEntity> =
        invoiceDao.getInvoicesForExportFiltered(startDate, endDate, query, orderIdFilter, customerFilter, limit)

    override suspend fun getMatchingOrderIds(query: String, limit: Int): List<String> =
        invoiceDao.getMatchingOrderIds(query, limit)

    override suspend fun updateInvoiceCustomerAndNote(orderId: String, customerInfo: String, footerNote: String): Int {
        return when (val res = updateInvoiceCustomerAndNoteAudited(orderId, customerInfo, footerNote)) {
            is PosResult.Success -> res.data
            is PosResult.Error -> {
                if (res.error is PosError.RecordNotFound) 0 else throw PosErrorException(res.error)
            }
        }
    }

    override suspend fun updateInvoiceCustomerAndNoteAudited(
        orderId: String,
        customerInfo: String,
        footerNote: String
    ): PosResult<Int> {
        if (db == null || operatorDao == null || auditLogDao == null) {
            val rows = invoiceDao.updateInvoiceCustomerAndNote(orderId, customerInfo, footerNote)
            return PosResult.Success(rows)
        }

        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val existing = invoiceDao.getInvoiceById(orderId)
                    ?: return@withTransaction PosResult.Error(PosError.RecordNotFound("Invoice", orderId))

                val newCustomer = customerInfo.trim()
                val newNote = footerNote.trim()

                val (beforeJson, afterJson) = AuditSanitizer.computeDiffSnapshots(
                    before = mapOf(
                        "customerInfo" to existing.customerInfo,
                        "footerNote" to existing.footerNote
                    ),
                    after = mapOf(
                        "customerInfo" to newCustomer,
                        "footerNote" to newNote
                    )
                )

                if (beforeJson == null && afterJson == null) {
                    return@withTransaction PosResult.Success(0)
                }

                val now = timeProvider()
                val rows = invoiceDao.updateInvoiceCustomerAndNoteWithOperator(
                    orderId = orderId,
                    customerInfo = newCustomer,
                    footerNote = newNote,
                    updatedByOperatorId = actor.userId,
                    updatedByOperatorName = actor.operatorName,
                    updatedAt = now
                )

                if (rows > 0) {
                    auditLogDao.insertAuditLog(
                        AuditLogEntity(
                            eventId = "AUD-" + UUID.randomUUID().toString(),
                            timestamp = now,
                            operatorId = actor.userId,
                            operatorName = actor.operatorName,
                            actionType = AuditActionType.INVOICE_METADATA_UPDATED,
                            entityType = AuditEntityType.INVOICE,
                            entityId = orderId,
                            summaryEn = "Updated metadata for invoice #$orderId",
                            summaryBn = "ইনভয়েস #$orderId-এর কাস্টমার/নোট হালনাগাদ করা হয়েছে",
                            beforeData = beforeJson,
                            afterData = afterJson,
                            isSuccess = true
                        )
                    )
                }
                PosResult.Success(rows)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to update invoice metadata: ${e.message}", e))
        }
    }

    override suspend fun settleInvoiceDue(
        orderId: String,
        additionalPaymentBdt: Long,
        method: String
    ): PosResult<InvoiceEntity> {
        if (additionalPaymentBdt <= 0) {
            return PosResult.Error(PosError.ValidationError("Settlement amount must be positive"))
        }

        if (db == null || operatorDao == null || auditLogDao == null) {
            val invoice = invoiceDao.getInvoiceById(orderId)
                ?: return PosResult.Error(PosError.DatabaseError("Invoice not found"))
            val remainingDue = invoice.roundedTotalBdt - invoice.paidAmountBdt
            if (remainingDue <= 0) {
                return PosResult.Error(PosError.ValidationError("Invoice is already fully paid"))
            }
            if (additionalPaymentBdt > remainingDue) {
                return PosResult.Error(PosError.ValidationError("Settlement amount exceeds outstanding due of $remainingDue ৳"))
            }
            val now = timeProvider()
            val record = PaymentRecordEntity(
                orderId = orderId,
                paymentMethod = method,
                amountPaidPaisa = additionalPaymentBdt * 100L,
                transactionRef = "SETTLE-$orderId-$now",
                timestamp = now,
                createdAt = now
            )
            val updated = invoiceDao.settleInvoiceDueAtomic(orderId, additionalPaymentBdt, record)
                ?: return PosResult.Error(PosError.DatabaseError("Failed to update invoice payment"))
            return PosResult.Success(updated)
        }

        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val invoice = invoiceDao.getInvoiceById(orderId)
                    ?: return@withTransaction PosResult.Error(PosError.DatabaseError("Invoice not found"))

                val remainingDue = invoice.roundedTotalBdt - invoice.paidAmountBdt
                if (remainingDue <= 0) {
                    return@withTransaction PosResult.Error(PosError.ValidationError("Invoice is already fully paid"))
                }
                if (additionalPaymentBdt > remainingDue) {
                    return@withTransaction PosResult.Error(
                        PosError.ValidationError("Settlement amount exceeds outstanding due of $remainingDue ৳")
                    )
                }

                val now = timeProvider()
                val txRef = "SETTLE-$orderId-$now"
                val record = PaymentRecordEntity(
                    orderId = orderId,
                    paymentMethod = method,
                    amountPaidPaisa = additionalPaymentBdt * 100L,
                    transactionRef = txRef,
                    timestamp = now,
                    createdAt = now,
                    collectedByOperatorId = actor.userId,
                    collectedByOperatorName = actor.operatorName
                )
                val updated = invoiceDao.settleInvoiceDueAtomic(orderId, additionalPaymentBdt, record)
                    ?: return@withTransaction PosResult.Error(PosError.DatabaseError("Failed to update invoice payment"))

                val newDue = maxOf(0L, updated.roundedTotalBdt - updated.paidAmountBdt)
                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.PAYMENT_DUE_SETTLED,
                        entityType = AuditEntityType.PAYMENT,
                        entityId = orderId,
                        summaryEn = "Collected due payment for #$orderId (${AuditSanitizer.formatBdt(additionalPaymentBdt, "en")} via $method; Remaining due: ${AuditSanitizer.formatBdt(newDue, "en")})",
                        summaryBn = "#$orderId-এর বকেয়া পরিশোধ (${AuditSanitizer.formatBdt(additionalPaymentBdt, "bn")}, $method; অবশিষ্ট বকেয়া: ${AuditSanitizer.formatBdt(newDue, "bn")})",
                        beforeData = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "paidAmountBdt" to invoice.paidAmountBdt,
                                "dueAmountBdt" to remainingDue
                            )
                        ),
                        afterData = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "paidAmountBdt" to updated.paidAmountBdt,
                                "dueAmountBdt" to newDue
                            )
                        ),
                        metadataJson = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "amountBdt" to additionalPaymentBdt,
                                "method" to method,
                                "transactionRef" to txRef
                            )
                        ),
                        isSuccess = true
                    )
                )
                PosResult.Success(updated)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to settle due payment: ${e.message}", e))
        }
    }

    override suspend fun deleteOrder(orderId: String) {
        when (val res = deleteOrderAudited(orderId)) {
            is PosResult.Success -> Unit
            is PosResult.Error -> throw PosErrorException(res.error)
        }
    }

    override suspend fun deleteOrderAudited(orderId: String): PosResult<Unit> {
        if (db == null || operatorDao == null || auditLogDao == null) {
            invoiceDao.deleteFullOrder(orderId)
            return PosResult.Success(Unit)
        }

        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val existing = invoiceDao.getInvoiceById(orderId)
                    ?: return@withTransaction PosResult.Error(PosError.RecordNotFound("Invoice", orderId))

                val lineCount = invoiceDao.getLinesForOrder(orderId).size
                val paymentCount = paymentDao?.getPaymentsForOrderDirect(orderId)?.size ?: 0

                invoiceDao.deleteFullOrder(orderId)

                val now = timeProvider()
                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.INVOICE_DELETED,
                        entityType = AuditEntityType.INVOICE,
                        entityId = orderId,
                        summaryEn = "Deleted invoice #${existing.orderId} (${AuditSanitizer.formatBdt(existing.roundedTotalBdt, "en")})",
                        summaryBn = "ভাউচার #${existing.orderId} মুছে ফেলা হয়েছে (${AuditSanitizer.formatBdt(existing.roundedTotalBdt, "bn")})",
                        beforeData = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "orderId" to existing.orderId,
                                "customerInfo" to existing.customerInfo,
                                "roundedTotalBdt" to existing.roundedTotalBdt,
                                "paidAmountBdt" to existing.paidAmountBdt,
                                "lineCount" to lineCount,
                                "paymentCount" to paymentCount
                            )
                        ),
                        afterData = null,
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to delete invoice: ${e.message}", e))
        }
    }

    override fun getTotalInvoiceCountFlow(): Flow<Int> =
        invoiceDao.getTotalInvoiceCountFlow()

    override fun getTotalInvoiceLinesCountFlow(): Flow<Int> =
        invoiceDao.getTotalInvoiceLinesCountFlow()
}
