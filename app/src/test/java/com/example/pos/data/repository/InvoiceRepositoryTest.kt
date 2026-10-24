package com.example.pos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosResult
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InvoiceRepositoryTest {

    private lateinit var db: PosDatabase
    private lateinit var repository: InvoiceRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            // Seed initial app_settings
            db.appSettingsDao().insertOrUpdate(
                AppSettingsEntity(
                    id = 1,
                    language = "bn",
                    themeMode = "LIGHT",
                    paperSize = "58mm",
                    autoCutterEnabled = true,
                    nextOrderSequence = 10001L
                )
            )
        }

        repository = InvoiceRepositoryImpl(db.invoiceDao(), db.appSettingsDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testGetNextOrderIdFormatsCorrectly() = runBlocking {
        val orderId = repository.getNextOrderId()
        assertEquals("INV-10001", orderId)
    }

    @Test
    fun testSaveOrderFullyPaidCreatesCashPaymentRecordAndIncrementsSequence() = runBlocking {
        val orderId = "INV-10001"
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = 1700000000000L,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = "01711223344",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            isPaid = true,
            paidAmountBdt = 500L
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = orderId,
                itemId = "prod-1",
                itemName = "Octane",
                itemType = "Liquid",
                unit = "L",
                quantityStr = "4.0",
                unitPricePaisa = 12500L,
                lineTotalPaisa = 50000L,
                sortOrder = 1
            )
        )

        val result = repository.saveOrder(invoice, lines)
        assertTrue("Save order should succeed", result is PosResult.Success)

        // Verify invoice persistence
        val savedInvoice = repository.getInvoice(orderId)
        assertNotNull(savedInvoice)
        assertEquals(true, savedInvoice!!.isPaid)
        assertEquals(500L, savedInvoice.paidAmountBdt)
        assertEquals(500L, savedInvoice.roundedTotalBdt)

        // Verify invoice lines persistence
        val savedLines = repository.getInvoiceLines(orderId)
        assertEquals(1, savedLines.size)
        assertEquals("Octane", savedLines[0].itemName)

        // Verify payment records persistence
        val payments = db.paymentDao().getPaymentsForOrder(orderId).first()
        assertEquals(1, payments.size)
        assertEquals("CASH", payments[0].paymentMethod)
        assertEquals(50000L, payments[0].amountPaidPaisa)
        assertEquals(orderId, payments[0].orderId)

        // Verify sequence incremented
        val nextSeq = repository.getNextOrderId()
        assertEquals("INV-10002", nextSeq)
    }

    @Test
    fun testSaveOrderPartiallyPaidRecordsCashPaymentAndUnpaidDue() = runBlocking {
        val orderId = "INV-10002"
        val totalBdt = 1200L
        val paidBdt = 500L
        val dueBdt = totalBdt - paidBdt // 700 BDT
        assertEquals(700L, dueBdt)

        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = 1700000000000L,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = "01711223344",
            rawTotalPaisa = 120000L,
            roundedTotalBdt = totalBdt,
            isPaid = false,
            paidAmountBdt = paidBdt
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = orderId,
                itemId = "prod-2",
                itemName = "Engine Oil",
                itemType = "Product",
                unit = "Pcs",
                quantityStr = "2.0",
                unitPricePaisa = 60000L,
                lineTotalPaisa = 120000L,
                sortOrder = 1
            )
        )

        val result = repository.saveOrder(invoice, lines)
        assertTrue(result is PosResult.Success)

        // Verify invoice
        val savedInvoice = repository.getInvoice(orderId)
        assertNotNull(savedInvoice)
        assertEquals(false, savedInvoice!!.isPaid)
        assertEquals(500L, savedInvoice.paidAmountBdt)
        assertEquals(1200L, savedInvoice.roundedTotalBdt)

        // Verify payment record has CASH for the paid amount
        val payments = db.paymentDao().getPaymentsForOrder(orderId).first()
        assertEquals(1, payments.size)
        assertEquals("CASH", payments[0].paymentMethod)
        assertEquals(50000L, payments[0].amountPaidPaisa)

        // Verify sequence incremented
        val nextSeq = repository.getNextOrderId()
        assertEquals("INV-10002", nextSeq)
    }

    @Test
    fun testSaveOrderZeroPaymentInsertsNoPaymentRecord() = runBlocking {
        val orderId = "INV-10003"
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = 1700000000000L,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = "01711223344",
            rawTotalPaisa = 80000L,
            roundedTotalBdt = 800L,
            isPaid = false,
            paidAmountBdt = 0L
        )

        val result = repository.saveOrder(invoice, emptyList())
        assertTrue(result is PosResult.Success)

        // Verify invoice saved with isPaid = false, paidAmountBdt = 0
        val savedInvoice = repository.getInvoice(orderId)
        assertNotNull(savedInvoice)
        assertEquals(false, savedInvoice!!.isPaid)
        assertEquals(0L, savedInvoice.paidAmountBdt)

        // Verify NO payment record is inserted when received amount is zero
        val payments = db.paymentDao().getPaymentsForOrder(orderId).first()
        assertTrue("Payment records must be empty when received amount is zero", payments.isEmpty())

        // Verify sequence incremented
        val nextSeq = repository.getNextOrderId()
        assertEquals("INV-10002", nextSeq)
    }

    @Test
    fun testSaveOrderRejectsNegativePaidAmount() = runBlocking {
        val orderId = "INV-10004"
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = 1700000000000L,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = "01711223344",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            isPaid = false,
            paidAmountBdt = -50L
        )

        val result = repository.saveOrder(invoice, emptyList())
        assertTrue("Negative paid amount must be rejected", result is PosResult.Error)
        assertEquals("Paid amount cannot be negative", (result as PosResult.Error).error.message)

        // Verify invoice not saved
        val savedInvoice = repository.getInvoice(orderId)
        assertNull(savedInvoice)

        // Verify sequence not incremented
        assertEquals("INV-10001", repository.getNextOrderId())
    }

    @Test
    fun testSaveOrderRejectsPaidAmountExceedingTotal() = runBlocking {
        val orderId = "INV-10005"
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = 1700000000000L,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = "01711223344",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            isPaid = true,
            paidAmountBdt = 501L
        )

        val result = repository.saveOrder(invoice, emptyList())
        assertTrue("Paid amount exceeding total must be rejected", result is PosResult.Error)
        assertEquals("Paid amount cannot exceed invoice total", (result as PosResult.Error).error.message)

        val savedInvoice = repository.getInvoice(orderId)
        assertNull(savedInvoice)
        assertEquals("INV-10001", repository.getNextOrderId())
    }

    @Test
    fun testSaveOrderRejectsDueAsPaymentMethod() = runBlocking {
        val orderId = "INV-10006"
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = 1700000000000L,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = "01711223344",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            isPaid = true,
            paidAmountBdt = 500L
        )
        val duePayment = PaymentRecordEntity(
            orderId = orderId,
            paymentMethod = "DUE",
            amountPaidPaisa = 50000L
        )

        val result = repository.saveOrder(invoice, emptyList(), duePayment)
        assertTrue("DUE as payment method must be rejected", result is PosResult.Error)
        assertEquals("Payment method must be CASH", (result as PosResult.Error).error.message)

        val savedInvoice = repository.getInvoice(orderId)
        assertNull(savedInvoice)
        assertEquals("INV-10001", repository.getNextOrderId())
    }

    @Test
    fun testSaveOrderRejectsPaymentRecordMismatch() = runBlocking {
        val orderId = "INV-10007"
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = 1700000000000L,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = "01711223344",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            isPaid = true,
            paidAmountBdt = 500L
        )
        val mismatchedPayment = PaymentRecordEntity(
            orderId = "INV-OTHER",
            paymentMethod = "CASH",
            amountPaidPaisa = 50000L
        )

        val result = repository.saveOrder(invoice, emptyList(), mismatchedPayment)
        assertTrue("Payment order ID mismatch must be rejected", result is PosResult.Error)
        assertEquals("Payment order ID mismatch", (result as PosResult.Error).error.message)

        val savedInvoice = repository.getInvoice(orderId)
        assertNull(savedInvoice)
        assertEquals("INV-10001", repository.getNextOrderId())
    }

    @Test
    fun testSaveOrderRejectsPaymentWhenPaidAmountIsZero() = runBlocking {
        val orderId = "INV-10008"
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = 1700000000000L,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = "01711223344",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            isPaid = false,
            paidAmountBdt = 0L
        )
        val extraneousPayment = PaymentRecordEntity(
            orderId = orderId,
            paymentMethod = "CASH",
            amountPaidPaisa = 10000L
        )

        val result = repository.saveOrder(invoice, emptyList(), extraneousPayment)
        assertTrue("Payment record for zero paid amount must be rejected", result is PosResult.Error)
        assertEquals("Payment record cannot be provided when paid amount is zero", (result as PosResult.Error).error.message)

        val savedInvoice = repository.getInvoice(orderId)
        assertNull(savedInvoice)
        assertEquals("INV-10001", repository.getNextOrderId())
    }
}
