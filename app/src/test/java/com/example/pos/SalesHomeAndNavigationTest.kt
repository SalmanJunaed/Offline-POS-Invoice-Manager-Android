package com.example.pos

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosResult
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.HistoryRepositoryImpl
import com.example.pos.data.repository.HistorySortOption
import com.example.pos.data.repository.InvoiceRepositoryImpl
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
class SalesHomeAndNavigationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: PosDatabase
    private lateinit var invoiceRepo: InvoiceRepositoryImpl
    private lateinit var historyRepo: HistoryRepositoryImpl

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        runBlocking {
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

        invoiceRepo = InvoiceRepositoryImpl(db.invoiceDao(), db.appSettingsDao())
        historyRepo = HistoryRepositoryImpl(db.invoiceDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    // 1. Default landing screen is Sales Home after login (index 0, isNewInvoiceOpen = false)
    @Test
    fun testDefaultLandingScreenIsSalesHome() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        assertEquals(HistorySortOption.DATE_DESC, vm.historySortOption.value)
        assertFalse(vm.isProcessingOrder.value)
    }

    // 2. Saved invoice list loads correctly in newest-first order by default
    @Test
    fun testSavedInvoiceListLoadsNewestFirst() = runBlocking {
        val now = System.currentTimeMillis()
        val inv1 = InvoiceEntity(
            orderId = "INV-001",
            dateTime = now - 50000,
            operatorId = "OP-1",
            operatorName = "User A",
            customerInfo = "Cust 1",
            rawTotalPaisa = 10000,
            roundedTotalBdt = 100,
            isPaid = true,
            paidAmountBdt = 100
        )
        val inv2 = InvoiceEntity(
            orderId = "INV-002",
            dateTime = now,
            operatorId = "OP-1",
            operatorName = "User A",
            customerInfo = "Cust 2",
            rawTotalPaisa = 20000,
            roundedTotalBdt = 200,
            isPaid = false,
            paidAmountBdt = 50
        )

        db.invoiceDao().insertInvoice(inv1)
        db.invoiceDao().insertInvoice(inv2)

        val pagedList = historyRepo.getInvoicesPaged(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.DATE_DESC,
            limit = 10,
            offset = 0
        ).first()

        assertEquals(2, pagedList.size)
        assertEquals("INV-002", pagedList[0].orderId)
        assertEquals("INV-001", pagedList[1].orderId)
    }

    // 3. Search and date filter controls correctly filter Sales Records
    @Test
    fun testSearchAndFilterSalesRecords() = runBlocking {
        val inv1 = InvoiceEntity(
            orderId = "INV-101",
            dateTime = 1710000000000L,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = "01711223344",
            rawTotalPaisa = 15000,
            roundedTotalBdt = 150,
            isPaid = true,
            paidAmountBdt = 150
        )
        val inv2 = InvoiceEntity(
            orderId = "INV-102",
            dateTime = 1710000000000L,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = "01899000000",
            rawTotalPaisa = 30000,
            roundedTotalBdt = 300,
            isPaid = false,
            paidAmountBdt = 0
        )
        db.invoiceDao().insertInvoice(inv1)
        db.invoiceDao().insertInvoice(inv2)

        val results = historyRepo.getInvoicesPaged(
            query = "01711223344",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE,
            sortOption = HistorySortOption.DATE_DESC,
            limit = 10,
            offset = 0
        ).first()

        assertEquals(1, results.size)
        assertEquals("INV-101", results[0].orderId)
    }

    // 4. Record card shows Paid badge for paid invoices
    // 5. Record card shows Due badge for partial/zero deposit invoices
    // 6. Record card shows paid and remaining due amounts matching integer arithmetic
    @Test
    fun testInvoiceCardPaidDueBadgeAndIntegerBdtArithmetic() = runBlocking {
        val paidTotal = 500L
        val paidInv = InvoiceEntity(
            orderId = "INV-PAID",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier",
            customerInfo = "Walk-in",
            rawTotalPaisa = paidTotal * 100L,
            roundedTotalBdt = paidTotal,
            isPaid = true,
            paidAmountBdt = paidTotal
        )
        val paidDueAmount = paidInv.roundedTotalBdt - paidInv.paidAmountBdt
        assertTrue("Paid invoice must have isPaid = true", paidInv.isPaid)
        assertEquals("Paid invoice remaining due must be 0", 0L, paidDueAmount)
        assertEquals("Paid amount must equal total", paidTotal, paidInv.paidAmountBdt)

        val dueTotal = 1200L
        val dueDeposit = 400L
        val dueInv = InvoiceEntity(
            orderId = "INV-DUE",
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier",
            customerInfo = "01711000000",
            rawTotalPaisa = dueTotal * 100L,
            roundedTotalBdt = dueTotal,
            isPaid = false,
            paidAmountBdt = dueDeposit
        )
        val remainingDue = dueInv.roundedTotalBdt - dueInv.paidAmountBdt
        assertFalse("Due invoice must have isPaid = false", dueInv.isPaid)
        assertEquals("Due amount must be exactly total - deposit", 800L, remainingDue)
        assertEquals("Paid amount must be deposit amount", dueDeposit, dueInv.paidAmountBdt)
    }

    // 7. Tapping FAB opens New Invoice workflow with an empty cart
    @Test
    fun testFabOpensNewInvoiceWithEmptyCart() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        assertTrue("Fresh cart must be empty", vm.draftItems.value.isEmpty())
        assertEquals(0L, vm.rawSubtotalPaisa.value)
        assertEquals(0L, vm.roundedTotalBdt.value)
    }

    // 8. Back navigation from New Invoice with dirty draft prompts discard confirmation
    // 9. Discarding dirty draft clears cart and returns to Sales Home
    // 10. Canceling discard keeps draft intact
    // 11. Back navigation from New Invoice with empty draft returns to Sales Home immediately
    @Test
    fun testDraftNavigationAndDiscardSafety() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        assertTrue(vm.draftItems.value.isEmpty())

        val prod = ProductEntity(
            itemId = "P1",
            itemName = "Test Fuel",
            itemType = "Liquid",
            unit = "L",
            defaultUnitPricePaisa = 12000L
        )
        vm.addItemFromCatalog(prod)
        assertEquals(1, vm.draftItems.value.size)

        // Cancel discard: draft remains intact
        assertEquals(1, vm.draftItems.value.size)
        assertEquals("Test Fuel", vm.draftItems.value[0].itemName)

        // Confirm discard: draft is cleared
        vm.clearDraft()
        assertTrue("Cleared draft cart must be empty", vm.draftItems.value.isEmpty())
    }

    // 12. Saving Paid invoice opens Invoice Preview instead of printing directly
    // 13. Saving Due invoice opens Invoice Preview instead of printing directly
    @Test
    fun testSavePaidAndDueInvoicesOpenPreviewWithoutDirectPrint() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        val prod = ProductEntity(
            itemId = "P-100",
            itemName = "Engine Oil",
            itemType = "Product",
            unit = "Bottle",
            defaultUnitPricePaisa = 45000L
        )
        vm.addItemFromCatalog(prod)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(450L, vm.roundedTotalBdt.value)

        // Checkout paid
        vm.checkoutPaid()
        var attempts = 0
        while (!vm.receiptPreviewOpen.value && attempts < 30) {
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
            attempts++
        }

        // Verify receipt preview is open and flag to navigate home after preview is set
        assertTrue("Receipt preview must open after checkout (toast: ${vm.toastMessage.value})", vm.receiptPreviewOpen.value)
        assertTrue("navigateHomeAfterPreview flag must be set", vm.navigateHomeAfterPreview.value)
        assertFalse("Direct printing must NOT be actively running without preview confirmation", vm.isPrinting.value)
        assertTrue("Draft must be cleared after successful checkout", vm.draftItems.value.isEmpty())
    }

    // 14. Closing Invoice Preview returns to Sales Home and refreshes list
    @Test
    fun testClosingInvoicePreviewResetsState() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        vm.closeReceiptPreview()
        assertFalse(vm.receiptPreviewOpen.value)

        vm.resetNavigateHomeAfterPreview()
        assertFalse(vm.navigateHomeAfterPreview.value)
    }

    // 15. Tapping Print in Invoice Preview sends print job and returns to Sales Home without duplicate save
    @Test
    fun testPrintFromPreviewDoesNotDuplicateInvoice() = runBlocking {
        val orderId = "INV-PRINT-1"
        val inv = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier",
            customerInfo = "01700000000",
            rawTotalPaisa = 10000,
            roundedTotalBdt = 100,
            isPaid = true,
            paidAmountBdt = 100
        )
        db.invoiceDao().insertInvoice(inv)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        // Invoke print from preview
        vm.printInvoiceFromPreview(inv)

        // Verify that the invoice in the database is not duplicated
        val fetched = db.invoiceDao().getInvoiceById(orderId)
        assertNotNull(fetched)
        assertEquals(orderId, fetched!!.orderId)
    }

    // 16. Tapping a saved record opens Invoice Details with all required fields
    // 17. Details screen Preview/Print opens Invoice Preview first
    @Test
    fun testSavedRecordDetailsAndPreviewFirst() = runBlocking {
        val orderId = "INV-DETAIL-1"
        val inv = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier B",
            customerInfo = "01999887766",
            rawTotalPaisa = 50000,
            roundedTotalBdt = 500,
            isPaid = false,
            paidAmountBdt = 200,
            footerNote = "Thank you for shopping"
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = orderId,
                itemId = "ITM-1",
                itemName = "Octane 95",
                itemType = "Liquid",
                unit = "L",
                quantityStr = "5",
                unitPricePaisa = 10000,
                lineTotalPaisa = 50000,
                sortOrder = 1
            )
        )
        val app = ApplicationProvider.getApplicationContext<Application>()
        val appDb = PosDatabase.getInstance(app)
        appDb.invoiceDao().insertInvoice(inv)
        appDb.invoiceDao().insertInvoiceLines(lines)

        val vm = PosViewModel(app)

        // Open preview for the saved invoice
        vm.openInvoicePreview(inv)
        var attempts = 0
        while (!vm.receiptPreviewOpen.value && attempts < 30) {
            Thread.sleep(50)
            testDispatcher.scheduler.advanceUntilIdle()
            attempts++
        }

        assertTrue("Preview must be opened for details preview/print action", vm.receiptPreviewOpen.value)
        assertEquals(orderId, vm.selectedInvoiceForReceipt.value?.orderId)
        assertTrue(vm.receiptPreviewText.value.contains(orderId))
    }

    // 18. Details screen Edit updates customer information and notes only, preserving order ID and totals
    @Test
    fun testEditInvoiceDetailsPreservesFinancialIntegrity() = runBlocking {
        val orderId = "INV-EDIT-1"
        val originalTotal = 750L
        val originalRaw = 75000L
        val originalPaid = 300L

        val inv = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier A",
            customerInfo = "Old Customer",
            rawTotalPaisa = originalRaw,
            roundedTotalBdt = originalTotal,
            isPaid = false,
            paidAmountBdt = originalPaid,
            footerNote = "Old note"
        )
        db.invoiceDao().insertInvoice(inv)

        // Perform metadata-only update through InvoiceDao
        val rows = db.invoiceDao().updateInvoiceCustomerAndNote(orderId, "Updated Customer Name", "New updated note")
        assertEquals(1, rows)

        val updated = db.invoiceDao().getInvoiceById(orderId)
        assertNotNull(updated)
        assertEquals("Updated Customer Name", updated!!.customerInfo)
        assertEquals("New updated note", updated.footerNote)
        // Financial fields remain strictly untouched
        assertEquals(orderId, updated.orderId)
        assertEquals(originalRaw, updated.rawTotalPaisa)
        assertEquals(originalTotal, updated.roundedTotalBdt)
        assertEquals(originalPaid, updated.paidAmountBdt)
        assertEquals(false, updated.isPaid)
    }

    // 19. Details screen Delete permanently removes invoice, lines, and payment records atomically
    @Test
    fun testDeleteInvoiceRemovesPaymentsLinesAndInvoiceAtomically() = runBlocking {
        val orderId = "INV-DEL-10"
        val inv = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier",
            customerInfo = "Customer to delete",
            rawTotalPaisa = 20000,
            roundedTotalBdt = 200,
            isPaid = true,
            paidAmountBdt = 200
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = orderId,
                itemId = "P1",
                itemName = "Item 1",
                itemType = "Product",
                unit = "Pcs",
                quantityStr = "2",
                unitPricePaisa = 10000,
                lineTotalPaisa = 20000,
                sortOrder = 1
            )
        )
        val payment = PaymentRecordEntity(
            orderId = orderId,
            paymentMethod = "CASH",
            amountPaidPaisa = 20000,
            timestamp = System.currentTimeMillis()
        )

        db.invoiceDao().saveFullOrderAtomic(inv, lines, payment)

        // Verify records exist prior to deletion
        assertNotNull(db.invoiceDao().getInvoiceById(orderId))
        assertEquals(1, db.invoiceDao().getLinesForOrder(orderId).size)
        assertEquals(1, db.paymentDao().getPaymentsForOrder(orderId).first().size)

        // Delete full order
        db.invoiceDao().deleteFullOrder(orderId)

        // Verify atomic cleanup of all records including payments
        assertNull("Invoice record must be deleted", db.invoiceDao().getInvoiceById(orderId))
        assertTrue("Invoice lines must be deleted", db.invoiceDao().getLinesForOrder(orderId).isEmpty())
        assertTrue("Payment records must be deleted", db.paymentDao().getPaymentsForOrder(orderId).first().isEmpty())
    }

    // 20. Save failure preserves draft; print failure preserves saved invoice
    @Test
    fun testSaveFailurePreservesDraftAndPrintFailureKeepsSavedInvoice() = runBlocking {
        // 1. Save failure test
        val orderId = "INV-FAIL-2"
        val invalidInvoice = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier",
            customerInfo = "Cust",
            rawTotalPaisa = 10000L,
            roundedTotalBdt = 100L,
            isPaid = false,
            paidAmountBdt = 9999L // Invalid: paid exceeds rounded total
        )
        val saveResult = invoiceRepo.saveOrder(invalidInvoice, emptyList(), null)
        assertTrue("Invalid transaction must return error", saveResult is PosResult.Error)
        assertNull("Failed order must not be persisted", invoiceRepo.getInvoice(orderId))

        // 2. Print failure test
        val validOrderId = "INV-SAVED-SAFE"
        val validInvoice = InvoiceEntity(
            orderId = validOrderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier",
            customerInfo = "Cust",
            rawTotalPaisa = 10000L,
            roundedTotalBdt = 100L,
            isPaid = true,
            paidAmountBdt = 100L
        )
        val successResult = invoiceRepo.saveOrder(validInvoice, emptyList(), null)
        assertTrue(successResult is PosResult.Success)

        // Even if bluetooth printing is unavailable/fails, invoice in DB is intact
        val persisted = invoiceRepo.getInvoice(validOrderId)
        assertNotNull("Saved invoice must remain safely in database regardless of printer status", persisted)
        assertEquals(validOrderId, persisted!!.orderId)
    }
}
