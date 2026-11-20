package com.example.pos.data.repository

import androidx.room.withTransaction
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AuditLogDao
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.OperatorDao
import com.example.pos.data.db.PaymentDao
import com.example.pos.data.db.PaymentMethodSummary
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface PaymentRepository {
    fun getPaymentsForOrder(orderId: String): Flow<List<PaymentRecordEntity>>
    suspend fun getPaymentsForOrderDirect(orderId: String): List<PaymentRecordEntity>
    fun getPaymentMethodBreakdown(): Flow<List<PaymentMethodSummary>>
    fun getPaymentMethodBreakdownFiltered(startDate: Long, endDate: Long): Flow<List<PaymentMethodSummary>>
    suspend fun recordPayment(orderId: String, method: String, amountPaidPaisa: Long, ref: String?): PosResult<Unit>
}

class PaymentRepositoryImpl(
    private val paymentDao: PaymentDao,
    private val operatorDao: OperatorDao,
    private val auditLogDao: AuditLogDao,
    private val db: PosDatabase,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : PaymentRepository {

    override fun getPaymentsForOrder(orderId: String): Flow<List<PaymentRecordEntity>> =
        paymentDao.getPaymentsForOrder(orderId)

    override suspend fun getPaymentsForOrderDirect(orderId: String): List<PaymentRecordEntity> =
        paymentDao.getPaymentsForOrderDirect(orderId)

    override fun getPaymentMethodBreakdown(): Flow<List<PaymentMethodSummary>> =
        paymentDao.getPaymentMethodBreakdown()

    override fun getPaymentMethodBreakdownFiltered(startDate: Long, endDate: Long): Flow<List<PaymentMethodSummary>> =
        paymentDao.getPaymentMethodBreakdownFiltered(startDate, endDate)

    override suspend fun recordPayment(
        orderId: String,
        method: String,
        amountPaidPaisa: Long,
        ref: String?
    ): PosResult<Unit> {
        if (amountPaidPaisa <= 0L) {
            return PosResult.Error(PosError.ValidationError("Payment amount must be positive."))
        }
        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val now = timeProvider()
                val record = PaymentRecordEntity(
                    orderId = orderId,
                    paymentMethod = method,
                    amountPaidPaisa = amountPaidPaisa,
                    transactionRef = ref,
                    timestamp = now,
                    createdAt = now,
                    collectedByOperatorId = actor.userId,
                    collectedByOperatorName = actor.operatorName
                )
                paymentDao.insertPayment(record)

                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.PAYMENT_COLLECTED_CHECKOUT,
                        entityType = AuditEntityType.PAYMENT,
                        entityId = orderId,
                        summaryEn = "Recorded $method payment for #$orderId (${AuditSanitizer.formatPaisaAsBdt(amountPaidPaisa, "en")})",
                        summaryBn = "#$orderId-এ $method পেমেন্ট সংগ্রহ (${AuditSanitizer.formatPaisaAsBdt(amountPaidPaisa, "bn")})",
                        afterData = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "orderId" to orderId,
                                "method" to method,
                                "paidBdt" to (amountPaidPaisa / 100L),
                                "transactionRef" to (ref ?: "")
                            )
                        ),
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to record payment: ${e.message}", e))
        }
    }
}
