package com.example.pos

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.ui.viewmodel.PosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3StageADirectPreviewTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: PosDatabase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext<Application>()
        db = PosDatabase.getInstance(app)

        runBlocking {
            db.appSettingsDao().insertOrUpdate(
                AppSettingsEntity(
                    id = 1,
                    language = "bn",
                    themeMode = "LIGHT",
                    paperSize = "58mm",
                    autoCutterEnabled = true,
                    nextOrderSequence = 10001L,
                    footerNoteLine1 = "ধন্যবাদ আবার আসবেন"
                )
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun insertSampleInvoice(
        orderId: String = "INV-STAGE-A-01",
        paidAmountBdt: Long = 400L,
        roundedTotalBdt: Long = 500L,
        rawTotalPaisa: Long = 50000L,
        customerInfo: String = "01712345678 (করিম)",
        footerNote: String = "ধন্যবাদ"
    ): InvoiceEntity {
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Staff User",
            customerInfo = customerInfo,
            footerNote = footerNote,
            rawTotalPaisa = rawTotalPaisa,
            roundedTotalBdt = roundedTotalBdt,
            paidAmountBdt = paidAmountBdt,
            isPaid = paidAmountBdt >= roundedTotalBdt
        )
        db.invoiceDao().insertInvoice(invoice)

        db.invoiceDao().insertInvoiceLines(
            listOf(
                InvoiceLineEntity(
                    orderId = orderId,
                    itemId = "OCT-1",
                    itemName = "অকটেন",
                    itemType = "FUEL",
                    unit = "L",
                    quantityStr = "4.0",
                    unitPricePaisa = 12500L,
                    lineTotalPaisa = 50000L
                )
            )
        )

        db.invoiceDao().insertPayment(
            PaymentRecordEntity(
                orderId = orderId,
                paymentMethod = "CASH",
                amountPaidPaisa = paidAmountBdt * 100L,
                transactionRef = "REF-$orderId",
                createdAt = System.currentTimeMillis()
            )
        )

        return invoice
    }

    @Test
    fun testDirectSavedVoucherPreview_opensImmediatelyOnCardTap() = runBlocking {
        val invoice = insertSampleInvoice("INV-DIRECT-01")
        val vm = PosViewModel(app)

        // Verify initial state
        assertFalse("Receipt preview should initially be closed", vm.receiptPreviewOpen.value)

        // Simulate tapping saved invoice card in Sales Home (direct preview navigation)
        vm.openInvoicePreview(invoice)

        var attempts = 0
        while (!vm.receiptPreviewOpen.value && attempts < 30) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
            attempts++
        }

        // 1. Direct preview opened without intermediary screen
        assertTrue("Receipt preview must open immediately on card tap", vm.receiptPreviewOpen.value)
        assertEquals("Selected invoice for preview must match tapped invoice", invoice.orderId, vm.selectedInvoiceForReceipt.value?.orderId)

        // 2. Contains invoice ID, customer info, itemized line, totals, and due
        val previewText = vm.receiptPreviewText.value
        assertTrue("Preview must contain Order ID", previewText.contains(invoice.orderId))
        assertTrue("Preview must contain customer info", previewText.contains("01712345678"))
        assertTrue("Preview must contain item name", previewText.contains("অকটেন"))
        assertTrue("Preview lines cache must be loaded", vm.receiptPopupLines.value.isNotEmpty())
        assertEquals("Line item name must match", "অকটেন", vm.receiptPopupLines.value.first().itemName)
    }

    @Test
    fun testReprintSafety_neverGeneratesDuplicateInvoiceOrPayment() = runBlocking {
        val invoice = insertSampleInvoice("INV-REPRINT-01")
        val vm = PosViewModel(app)

        vm.openInvoicePreview(invoice)
        var attempts = 0
        while (!vm.receiptPreviewOpen.value && attempts < 30) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
            attempts++
        }

        val initialInvoicesCount = db.invoiceDao().getInvoicesForExport(0L, Long.MAX_VALUE).count { it.orderId == "INV-REPRINT-01" }
        val initialPaymentsCount = db.paymentDao().getPaymentsForOrder("INV-REPRINT-01").first().size

        assertEquals(1, initialInvoicesCount)
        assertEquals(1, initialPaymentsCount)

        // Trigger reprint multiple times from preview
        vm.printInvoiceFromPreview(invoice)
        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(50)

        vm.printInvoiceFromPreview(invoice)
        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(50)

        // Verify database counts remain strictly 1: NO DUPLICATE INVOICE OR PAYMENT CREATED
        val afterInvoicesCount = db.invoiceDao().getInvoicesForExport(0L, Long.MAX_VALUE).count { it.orderId == "INV-REPRINT-01" }
        val afterPaymentsCount = db.paymentDao().getPaymentsForOrder("INV-REPRINT-01").first().size

        assertEquals("Reprinting must never insert duplicate invoices", 1, afterInvoicesCount)
        assertEquals("Reprinting must never insert duplicate payment records", 1, afterPaymentsCount)
        assertEquals("Order ID must remain strictly identical", "INV-REPRINT-01", invoice.orderId)
    }

    @Test
    fun testEditMetadataFromPreview_preservesAllFinancialCalculations() = runBlocking {
        val invoice = insertSampleInvoice(
            orderId = "INV-EDIT-01",
            paidAmountBdt = 300L,
            roundedTotalBdt = 500L,
            rawTotalPaisa = 50000L
        )
        val vm = PosViewModel(app)

        vm.openInvoicePreview(invoice)
        var attempts = 0
        while (!vm.receiptPreviewOpen.value && attempts < 30) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
            attempts++
        }

        // Open edit metadata from preview
        vm.openEditInvoice(invoice)
        assertEquals("Invoice to edit must be set in ViewModel", invoice.orderId, vm.invoiceToEdit.value?.orderId)

        // Perform metadata-only update (customer and note)
        vm.updateInvoiceDetails(
            orderId = invoice.orderId,
            customerInfo = "Updated Customer 01999999999",
            footerNote = "Updated Note"
        )

        var updateAttempts = 0
        while (!vm.receiptPreviewText.value.contains("01999999999") && updateAttempts < 30) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
            updateAttempts++
        }

        val updatedInvoice = db.invoiceDao().getInvoiceById("INV-EDIT-01")
        assertNotNull(updatedInvoice)
        assertEquals("Updated Customer 01999999999", updatedInvoice!!.customerInfo)
        assertEquals("Updated Note", updatedInvoice.footerNote)

        // Financials must remain 100% IMMUTABLE
        assertEquals("Raw total paisa must not be altered", 50000L, updatedInvoice.rawTotalPaisa)
        assertEquals("Rounded total BDT must not be altered", 500L, updatedInvoice.roundedTotalBdt)
        assertEquals("Paid amount BDT must not be altered", 300L, updatedInvoice.paidAmountBdt)
        assertFalse("Paid status must remain consistent with financial amounts", updatedInvoice.isPaid)

        // Preview text must be updated with new customer info
        assertTrue("Preview text must reflect updated customer phone", vm.receiptPreviewText.value.contains("01999999999"))
    }

    @Test
    fun testDeleteFromPreview_atomicallyDeletesOrderLinesAndPayments() = runBlocking {
        val invoice = insertSampleInvoice("INV-DELETE-01")
        val vm = PosViewModel(app)

        vm.openInvoicePreview(invoice)
        var attempts = 0
        while (!vm.receiptPreviewOpen.value && attempts < 30) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
            attempts++
        }

        // Open delete from preview
        vm.openDeleteInvoice(invoice)
        assertEquals("Invoice to delete must be set", invoice.orderId, vm.invoiceToDelete.value?.orderId)

        // Confirm deletion with onSuccess callback
        var isDeleted = false
        vm.deleteInvoice(invoice.orderId) {
            isDeleted = true
        }

        var delAttempts = 0
        while (!isDeleted && delAttempts < 30) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
            delAttempts++
        }

        // Assert atomic wipe of invoice, lines, and payment records
        assertNull("Invoice must be deleted from database", db.invoiceDao().getInvoiceById("INV-DELETE-01"))
        assertTrue("Invoice lines must be deleted", db.invoiceDao().getLinesForOrder("INV-DELETE-01").isEmpty())
        assertTrue("Payment records must be deleted", db.paymentDao().getPaymentsForOrder("INV-DELETE-01").first().isEmpty())

        // Preview must be cleanly closed
        assertFalse("Receipt preview must be closed after deletion", vm.receiptPreviewOpen.value)
        assertNull("Selected invoice for preview must be cleared", vm.selectedInvoiceForReceipt.value)
    }

    @Test
    fun testClosePreview_restoresCleanNavigationState() = runBlocking {
        val invoice = insertSampleInvoice("INV-CLOSE-01")
        val vm = PosViewModel(app)

        vm.openInvoicePreview(invoice)
        var attempts = 0
        while (!vm.receiptPreviewOpen.value && attempts < 30) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(50)
            attempts++
        }
        assertTrue(vm.receiptPreviewOpen.value)

        vm.closeReceiptPreview()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Receipt preview must be closed", vm.receiptPreviewOpen.value)
        assertNull("Selected invoice must be cleared", vm.selectedInvoiceForReceipt.value)
        assertTrue("Receipt preview text must be cleared", vm.receiptPreviewText.value.isEmpty())
        assertTrue("Receipt lines cache must be empty", vm.receiptPopupLines.value.isEmpty())
    }
}
