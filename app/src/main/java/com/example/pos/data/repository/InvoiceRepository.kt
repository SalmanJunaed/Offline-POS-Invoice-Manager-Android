package com.example.pos.data.repository

import androidx.room.withTransaction
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AppSettingsDao
import com.example.pos.data.db.AuditLogDao
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.CustomerDao
import com.example.pos.data.db.InvoiceDao
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.OperatorDao
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.domain.FinancialCalculationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface InvoiceRepository {
    suspend fun getNextOrderId(): String
    suspend fun saveOrder(
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        payment: PaymentRecordEntity? = null
    ): PosResult<Unit>

    suspend fun checkoutOrderAtomic(
        invoiceTemplate: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        customerInfoForCrm: String? = null
    ): PosResult<InvoiceEntity>

    suspend fun getInvoice(orderId: String): InvoiceEntity?
    suspend fun getInvoiceLines(orderId: String): List<InvoiceLineEntity>
}

class InvoiceRepositoryImpl(
    private val invoiceDao: InvoiceDao,
    private val appSettingsDao: AppSettingsDao,
    private val customerDao: CustomerDao? = null,
    private val operatorDao: OperatorDao? = null,
    private val auditLogDao: AuditLogDao? = null,
    private val db: PosDatabase? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : InvoiceRepository {

    override suspend fun getNextOrderId(): String {
        val settings = appSettingsDao.getSettings()
        val seq = settings?.nextOrderSequence ?: 10294L
        return "INV-$seq"
    }

    override suspend fun saveOrder(
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        payment: PaymentRecordEntity?
    ): PosResult<Unit> {
        val validationError = validateOrderInputs(invoice, payment)
        if (validationError != null) return validationError

        val recordToInsert = buildInitialPayment(invoice, payment)

        // Audited atomic path when operator/audit infrastructure is injected (app runtime & PosRepository)
        if (db != null && operatorDao != null && auditLogDao != null) {
            return try {
                db.withTransaction {
                    val actor = operatorDao.getActiveOperator()
                        ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                    val now = timeProvider()
                    val stampedInvoice = invoice.copy(
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        updatedByOperatorId = null,
                        updatedByOperatorName = null
                    )
                    val stampedPayment = recordToInsert?.copy(
                        collectedByOperatorId = actor.userId,
                        collectedByOperatorName = actor.operatorName
                    )

                    invoiceDao.saveFullOrderAtomic(stampedInvoice, lines, stampedPayment)
                    appendCheckoutAuditLogs(
                        auditLogDao = auditLogDao,
                        actor = actor,
                        invoice = stampedInvoice,
                        lines = lines,
                        payment = stampedPayment,
                        timestamp = now
                    )
                    PosResult.Success(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PosResult.Error(PosError.DatabaseError("Failed to save order atomically: ${e.message}", e))
            }
        }

        // Fallback path strictly for standalone pre-auth financial unit tests
        return try {
            invoiceDao.saveFullOrderAtomic(invoice, lines, recordToInsert)
            PosResult.Success(Unit)
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to save order atomically: ${e.message}", e))
        }
    }

    override suspend fun checkoutOrderAtomic(
        invoiceTemplate: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        customerInfoForCrm: String?
    ): PosResult<InvoiceEntity> {
        val validationError = validateOrderInputs(invoiceTemplate, null)
        if (validationError != null) return validationError

        if (db == null || operatorDao == null || auditLogDao == null) {
            val savedRes = saveOrder(invoiceTemplate, lines, null)
            return when (savedRes) {
                is PosResult.Success -> PosResult.Success(invoiceTemplate)
                is PosResult.Error -> savedRes
            }
        }

        return try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val now = timeProvider()
                val stampedInvoice = invoiceTemplate.copy(
                    operatorId = actor.userId,
                    operatorName = actor.operatorName,
                    updatedByOperatorId = null,
                    updatedByOperatorName = null
                )
                val stampedPayment = if (stampedInvoice.paidAmountBdt > 0L) {
                    PaymentRecordEntity(
                        orderId = stampedInvoice.orderId,
                        paymentMethod = "CASH",
                        amountPaidPaisa = stampedInvoice.paidAmountBdt * 100L,
                        transactionRef = null,
                        timestamp = stampedInvoice.dateTime,
                        createdAt = now,
                        collectedByOperatorId = actor.userId,
                        collectedByOperatorName = actor.operatorName
                    )
                } else {
                    null
                }

                invoiceDao.saveFullOrderAtomic(stampedInvoice, lines, stampedPayment)

                val crmTarget = customerInfoForCrm?.trim()?.takeIf { it.isNotBlank() }
                if (customerDao != null && crmTarget != null) {
                    CustomerRepositoryImpl.recordCustomerOrderWithinTransaction(
                        customerDao = customerDao,
                        auditLogDao = auditLogDao,
                        actor = actor,
                        customerInfo = crmTarget,
                        totalPaisa = stampedInvoice.rawTotalPaisa,
                        now = now
                    )
                }

                appendCheckoutAuditLogs(
                    auditLogDao = auditLogDao,
                    actor = actor,
                    invoice = stampedInvoice,
                    lines = lines,
                    payment = stampedPayment,
                    timestamp = now
                )
                PosResult.Success(stampedInvoice)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to complete checkout atomically: ${e.message}", e))
        }
    }

    override suspend fun getInvoice(orderId: String): InvoiceEntity? = invoiceDao.getInvoiceById(orderId)

    override suspend fun getInvoiceLines(orderId: String): List<InvoiceLineEntity> =
        invoiceDao.getLinesForOrder(orderId)

    private fun validateOrderInputs(
        invoice: InvoiceEntity,
        payment: PaymentRecordEntity?
    ): PosResult.Error? {
        if (invoice.paidAmountBdt < 0L) {
            return PosResult.Error(PosError.ValidationError("Paid amount cannot be negative"))
        }
        if (invoice.paidAmountBdt > invoice.roundedTotalBdt) {
            return PosResult.Error(PosError.ValidationError("Paid amount cannot exceed invoice total"))
        }
        if (invoice.paidAmountBdt == 0L && payment != null && payment.amountPaidPaisa > 0L) {
            return PosResult.Error(PosError.ValidationError("Payment record cannot be provided when paid amount is zero"))
        }
        if (payment != null) {
            if (payment.amountPaidPaisa < 0L) {
                return PosResult.Error(PosError.ValidationError("Payment amount cannot be negative"))
            }
            if (payment.paymentMethod != "CASH") {
                return PosResult.Error(PosError.ValidationError("Payment method must be CASH"))
            }
            if (payment.orderId != invoice.orderId) {
                return PosResult.Error(PosError.ValidationError("Payment order ID mismatch"))
            }
            if (payment.amountPaidPaisa != invoice.paidAmountBdt * 100L) {
                return PosResult.Error(PosError.ValidationError("Payment amount does not match invoice paid amount"))
            }
        }
        return null
    }

    private fun buildInitialPayment(
        invoice: InvoiceEntity,
        payment: PaymentRecordEntity?
    ): PaymentRecordEntity? = if (invoice.paidAmountBdt > 0L) {
        payment ?: PaymentRecordEntity(
            orderId = invoice.orderId,
            paymentMethod = "CASH",
            amountPaidPaisa = invoice.paidAmountBdt * 100L,
            timestamp = invoice.dateTime
        )
    } else {
        null
    }

    private suspend fun appendCheckoutAuditLogs(
        auditLogDao: AuditLogDao,
        actor: com.example.pos.data.db.OperatorEntity,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        payment: PaymentRecordEntity?,
        timestamp: Long
    ) {
        val dueBdt = maxOf(0L, invoice.roundedTotalBdt - invoice.paidAmountBdt)
        auditLogDao.insertAuditLog(
            AuditLogEntity(
                eventId = "AUD-" + UUID.randomUUID().toString(),
                timestamp = timestamp,
                operatorId = actor.userId,
                operatorName = actor.operatorName,
                actionType = AuditActionType.INVOICE_CREATED,
                entityType = AuditEntityType.INVOICE,
                entityId = invoice.orderId,
                summaryEn = "Created invoice #${invoice.orderId} (${AuditSanitizer.formatBdt(invoice.roundedTotalBdt, "en")}, Paid: ${AuditSanitizer.formatBdt(invoice.paidAmountBdt, "en")}, Due: ${AuditSanitizer.formatBdt(dueBdt, "en")})",
                summaryBn = "নতুন ইনভয়েস #${invoice.orderId} তৈরি (${AuditSanitizer.formatBdt(invoice.roundedTotalBdt, "bn")}, জমা: ${AuditSanitizer.formatBdt(invoice.paidAmountBdt, "bn")}, বকেয়া: ${AuditSanitizer.formatBdt(dueBdt, "bn")})",
                beforeData = null,
                afterData = AuditSanitizer.buildSnapshotJson(
                    mapOf(
                        "orderId" to invoice.orderId,
                        "customerInfo" to invoice.customerInfo,
                        "rawTotalBdt" to FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa),
                        "roundedTotalBdt" to invoice.roundedTotalBdt,
                        "paidAmountBdt" to invoice.paidAmountBdt,
                        "dueAmountBdt" to dueBdt,
                        "lineCount" to lines.size
                    )
                ),
                isSuccess = true
            )
        )

        if (payment != null && payment.amountPaidPaisa > 0L) {
            val paidBdt = payment.amountPaidPaisa / 100L
            auditLogDao.insertAuditLog(
                AuditLogEntity(
                    eventId = "AUD-" + UUID.randomUUID().toString(),
                    timestamp = timestamp,
                    operatorId = actor.userId,
                    operatorName = actor.operatorName,
                    actionType = AuditActionType.PAYMENT_COLLECTED_CHECKOUT,
                    entityType = AuditEntityType.PAYMENT,
                    entityId = invoice.orderId,
                    summaryEn = "Collected checkout payment for #${invoice.orderId} (${AuditSanitizer.formatBdt(paidBdt, "en")} via ${payment.paymentMethod})",
                    summaryBn = "#${invoice.orderId}-এর চেকআউট পেমেন্ট গ্রহণ (${AuditSanitizer.formatBdt(paidBdt, "bn")}, ${payment.paymentMethod})",
                    beforeData = null,
                    afterData = AuditSanitizer.buildSnapshotJson(
                        mapOf(
                            "orderId" to invoice.orderId,
                            "amountBdt" to paidBdt,
                            "method" to payment.paymentMethod
                        )
                    ),
                    isSuccess = true
                )
            )
        }
    }
}
