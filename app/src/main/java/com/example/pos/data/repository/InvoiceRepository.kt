package com.example.pos.data.repository

import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.data.db.AppSettingsDao
import com.example.pos.data.db.InvoiceDao
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PaymentRecordEntity

interface InvoiceRepository {
    suspend fun getNextOrderId(): String
    suspend fun saveOrder(
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        payment: PaymentRecordEntity? = null
    ): PosResult<Unit>
    suspend fun getInvoice(orderId: String): InvoiceEntity?
    suspend fun getInvoiceLines(orderId: String): List<InvoiceLineEntity>
}

class InvoiceRepositoryImpl(
    private val invoiceDao: InvoiceDao,
    private val appSettingsDao: AppSettingsDao
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

        val recordToInsert = if (invoice.paidAmountBdt > 0L) {
            payment ?: PaymentRecordEntity(
                orderId = invoice.orderId,
                paymentMethod = "CASH",
                amountPaidPaisa = invoice.paidAmountBdt * 100L,
                timestamp = invoice.dateTime
            )
        } else {
            null
        }

        return try {
            invoiceDao.saveFullOrderAtomic(invoice, lines, recordToInsert)
            PosResult.Success(Unit)
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to save order atomically: ${e.message}", e))
        }
    }

    override suspend fun getInvoice(orderId: String): InvoiceEntity? = invoiceDao.getInvoiceById(orderId)

    override suspend fun getInvoiceLines(orderId: String): List<InvoiceLineEntity> =
        invoiceDao.getLinesForOrder(orderId)
}
