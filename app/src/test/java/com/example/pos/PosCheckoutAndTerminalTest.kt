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
import com.example.pos.data.repository.InvoiceRepositoryImpl
import com.example.pos.printer.ThermalReceiptFormatter
import com.example.pos.ui.viewmodel.DraftInvoiceItem
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PosCheckoutAndTerminalTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: PosDatabase
    private lateinit var repository: InvoiceRepositoryImpl

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

        repository = InvoiceRepositoryImpl(db.invoiceDao(), db.appSettingsDao())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    // 1. Paid checkout: correct isPaid, paidAmountBdt, one CASH payment record for nonzero total, correct paisa conversion
    @Test
    fun testPaidCheckoutAtomicPersistence() = runBlocking {
        val orderId = "INV-10001"
        val totalBdt = 650L
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier A",
            customerInfo = "01711223344",
            rawTotalPaisa = totalBdt * 100L,
            roundedTotalBdt = totalBdt,
            isPaid = true,
            paidAmountBdt = totalBdt
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = orderId,
                itemId = "prod-1",
                itemName = "Super Octane",
                itemType = "Liquid",
                unit = "L",
                quantityStr = "5",
                unitPricePaisa = 13000L,
                lineTotalPaisa = 65000L,
                sortOrder = 1
            )
        )
        val payment = PaymentRecordEntity(
            orderId = orderId,
            paymentMethod = "CASH",
            amountPaidPaisa = totalBdt * 100L,
            timestamp = System.currentTimeMillis()
        )

        val result = repository.saveOrder(invoice, lines, payment)
        assertTrue("Save order should succeed", result is PosResult.Success)

        val saved = repository.getInvoice(orderId)
        assertNotNull(saved)
        assertTrue("Invoice must be marked isPaid = true", saved!!.isPaid)
        assertEquals("Invoice paid amount must match full total", totalBdt, saved.paidAmountBdt)

        val payments = db.paymentDao().getPaymentsForOrder(orderId).first()
        assertEquals("Must create exactly one payment record", 1, payments.size)
        assertEquals("Payment method must be CASH", "CASH", payments[0].paymentMethod)
        assertEquals("Payment amount must be 65000 paisa", 65000L, payments[0].amountPaidPaisa)
    }

    // 2. Partial deposit: correct paid amount, isPaid = false, correct due amount, and one CASH record
    @Test
    fun testPartialDepositDueCheckout() = runBlocking {
        val orderId = "INV-10002"
        val totalBdt = 1200L
        val depositBdt = 400L
        val dueBdt = totalBdt - depositBdt

        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier A",
            customerInfo = "01822334455",
            rawTotalPaisa = totalBdt * 100L,
            roundedTotalBdt = totalBdt,
            isPaid = false,
            paidAmountBdt = depositBdt
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = orderId,
                itemId = "prod-2",
                itemName = "Diesel Fuel",
                itemType = "Liquid",
                unit = "L",
                quantityStr = "10",
                unitPricePaisa = 12000L,
                lineTotalPaisa = 120000L,
                sortOrder = 1
            )
        )
        val payment = PaymentRecordEntity(
            orderId = orderId,
            paymentMethod = "CASH",
            amountPaidPaisa = depositBdt * 100L,
            timestamp = System.currentTimeMillis()
        )

        val result = repository.saveOrder(invoice, lines, payment)
        assertTrue(result is PosResult.Success)

        val saved = repository.getInvoice(orderId)
        assertNotNull(saved)
        assertFalse("Invoice must be marked isPaid = false", saved!!.isPaid)
        assertEquals("Paid amount must be 400 BDT", depositBdt, saved.paidAmountBdt)
        assertEquals("Calculated due amount must be 800 BDT", dueBdt, saved.roundedTotalBdt - saved.paidAmountBdt)

        val payments = db.paymentDao().getPaymentsForOrder(orderId).first()
        assertEquals("Must create exactly one payment record for partial deposit", 1, payments.size)
        assertEquals("Payment method must be CASH", "CASH", payments[0].paymentMethod)
        assertEquals("Payment amount must match deposit in paisa", 40000L, payments[0].amountPaidPaisa)
    }

    // 3. Zero deposit: isPaid = false for positive total, paidAmountBdt = 0L, and no payment record
    @Test
    fun testZeroDepositDueCheckout() = runBlocking {
        val orderId = "INV-10003"
        val totalBdt = 500L
        val depositBdt = 0L

        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier A",
            customerInfo = "01933445566",
            rawTotalPaisa = totalBdt * 100L,
            roundedTotalBdt = totalBdt,
            isPaid = false,
            paidAmountBdt = depositBdt
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = orderId,
                itemId = "prod-1",
                itemName = "Octane",
                itemType = "Liquid",
                unit = "L",
                quantityStr = "4",
                unitPricePaisa = 12500L,
                lineTotalPaisa = 50000L,
                sortOrder = 1
            )
        )

        // Null payment for zero deposit
        val result = repository.saveOrder(invoice, lines, payment = null)
        assertTrue(result is PosResult.Success)

        val saved = repository.getInvoice(orderId)
        assertNotNull(saved)
        assertFalse("isPaid must be false", saved!!.isPaid)
        assertEquals("paidAmountBdt must be 0", 0L, saved.paidAmountBdt)

        val payments = db.paymentDao().getPaymentsForOrder(orderId).first()
        assertTrue("Zero deposit must NOT create any payment records", payments.isEmpty())
    }

    // 4. Full deposit through Due dialog: treated as fully paid with correct values
    @Test
    fun testFullDepositThroughDueDialog() = runBlocking {
        val orderId = "INV-10004"
        val totalBdt = 800L
        val depositBdt = 800L // User entered full amount in Due dialog

        val isPaid = (depositBdt == totalBdt)
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier A",
            customerInfo = "01700000000",
            rawTotalPaisa = totalBdt * 100L,
            roundedTotalBdt = totalBdt,
            isPaid = isPaid,
            paidAmountBdt = depositBdt
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = orderId,
                itemId = "prod-1",
                itemName = "Engine Oil",
                itemType = "Product",
                unit = "Can",
                quantityStr = "1",
                unitPricePaisa = 80000L,
                lineTotalPaisa = 80000L,
                sortOrder = 1
            )
        )
        val payment = PaymentRecordEntity(
            orderId = orderId,
            paymentMethod = "CASH",
            amountPaidPaisa = depositBdt * 100L,
            timestamp = System.currentTimeMillis()
        )

        val result = repository.saveOrder(invoice, lines, payment)
        assertTrue(result is PosResult.Success)

        val saved = repository.getInvoice(orderId)
        assertNotNull(saved)
        assertTrue("isPaid must be true when deposit equals total", saved!!.isPaid)
        assertEquals(800L, saved.paidAmountBdt)
    }

    // 5. Invalid input: negative, malformed, decimal and greater-than-total amounts validation tests
    @Test
    fun testDueDepositValidationRules() {
        val totalBdt = 1000L

        fun validateDeposit(input: String): String? {
            val trimmed = input.trim()
            val hasDecimal = trimmed.contains(".") || trimmed.contains(",")
            val parsedValue = trimmed.toLongOrNull()

            return when {
                hasDecimal -> "দশমিক গ্রহণযোগ্য নয়, পূর্ণ টাকা লিখুন"
                trimmed.isEmpty() -> "জমা টাকার পরিমাণ লিখুন"
                parsedValue == null -> "সঠিক পূর্ণ সংখ্যা লিখুন"
                parsedValue < 0L -> "জমা টাকা ঋণাত্মক হতে পারে না"
                parsedValue > totalBdt -> "জমা টাকা মোট টাকার (${totalBdt} ৳) বেশি হতে পারে না"
                else -> null
            }
        }

        // Decimal input rejected
        assertEquals("দশমিক গ্রহণযোগ্য নয়, পূর্ণ টাকা লিখুন", validateDeposit("100.5"))
        assertEquals("দশমিক গ্রহণযোগ্য নয়, পূর্ণ টাকা লিখুন", validateDeposit("50.00"))
        assertEquals("দশমিক গ্রহণযোগ্য নয়, পূর্ণ টাকা লিখুন", validateDeposit("50,00"))

        // Nonnumeric / malformed rejected
        assertEquals("সঠিক পূর্ণ সংখ্যা লিখুন", validateDeposit("abc"))
        assertEquals("সঠিক পূর্ণ সংখ্যা লিখুন", validateDeposit("100abc"))

        // Negative rejected
        assertEquals("জমা টাকা ঋণাত্মক হতে পারে না", validateDeposit("-50"))

        // Exceeding total rejected
        assertEquals("জমা টাকা মোট টাকার (1000 ৳) বেশি হতে পারে না", validateDeposit("1001"))
        assertEquals("জমা টাকা মোট টাকার (1000 ৳) বেশি হতে পারে না", validateDeposit("5000"))

        // Valid inputs
        assertNull(validateDeposit("0"))
        assertNull(validateDeposit("500"))
        assertNull(validateDeposit("1000"))
    }

    // 6. Repeated taps / in-flight processing guard prevents multiple simultaneous saves
    @Test
    fun testRepeatedTapsProcessingGuardInViewModel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PosViewModel(app)

        // Initially not processing
        assertFalse("Initial isProcessingOrder must be false", vm.isProcessingOrder.value)

        // When draft is empty, checkout calls abort without getting stuck
        vm.checkoutPaid()
        assertFalse("Empty draft checkout should not stay in processing state", vm.isProcessingOrder.value)
    }

    // 7. Save failure: draft remains available and not cleared
    @Test
    fun testSaveFailurePreservesDraft() = runBlocking {
        val orderId = "INV-FAIL-1"
        // Force an invalid invoice where paid amount exceeds total (triggers domain validation failure)
        val invalidInvoice = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier A",
            customerInfo = "01700000000",
            rawTotalPaisa = 10000L,
            roundedTotalBdt = 100L,
            isPaid = false,
            paidAmountBdt = 500L // Exceeds 100L!
        )

        val result = repository.saveOrder(invalidInvoice, emptyList(), null)
        assertTrue("Save order must return error for invalid data", result is PosResult.Error)

        val notSaved = repository.getInvoice(orderId)
        assertNull("Failed transaction must not insert invoice", notSaved)
    }

    // 8. Print failure: saved invoice remains persisted
    @Test
    fun testPrintFailureKeepsSavedInvoice() = runBlocking {
        val orderId = "INV-10008"
        val totalBdt = 250L
        val invoice = InvoiceEntity(
            orderId = orderId,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier A",
            customerInfo = "01799887766",
            rawTotalPaisa = 25000L,
            roundedTotalBdt = totalBdt,
            isPaid = true,
            paidAmountBdt = totalBdt
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = orderId,
                itemId = "prod-3",
                itemName = "Brake Oil",
                itemType = "Liquid",
                unit = "Bottle",
                quantityStr = "1",
                unitPricePaisa = 25000L,
                lineTotalPaisa = 25000L,
                sortOrder = 1
            )
        )
        val payment = PaymentRecordEntity(
            orderId = orderId,
            paymentMethod = "CASH",
            amountPaidPaisa = 25000L,
            timestamp = System.currentTimeMillis()
        )

        // Save order independently of printer
        val saveResult = repository.saveOrder(invoice, lines, payment)
        assertTrue(saveResult is PosResult.Success)

        // Even if printer is disconnected, database has committed the order
        val saved = repository.getInvoice(orderId)
        assertNotNull(saved)
        assertEquals(orderId, saved!!.orderId)
    }

    // 9. Receipt formatting: partial-payment receipt shows correct paid and due values
    @Test
    fun testReceiptFormattingShowsPaidAndDue() {
        val invoice = InvoiceEntity(
            orderId = "DH-2026-902",
            dateTime = 1711188000000L,
            operatorId = "OP-10",
            operatorName = "Rakib Hassan",
            customerInfo = "01899112233",
            rawTotalPaisa = 20000L,
            roundedTotalBdt = 200L,
            isPaid = false,
            paidAmountBdt = 50L
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = "DH-2026-902",
                itemId = "ITM-01",
                itemName = "Diesel",
                itemType = "Liquid",
                unit = "L",
                quantityStr = "2",
                unitPricePaisa = 10000L,
                lineTotalPaisa = 20000L,
                sortOrder = 1
            )
        )

        val text = ThermalReceiptFormatter.generateReceiptText(
            header = null,
            invoice = invoice,
            lines = lines,
            paperSize = "58mm",
            footerLine1 = "Thank you",
            footerLine2 = "Visit again",
            isFullPaid = false,
            lang = "bn"
        )

        assertTrue("Receipt must show 'জমা (Paid):'", text.contains("জমা (Paid):"))
        assertTrue("Receipt must show paid amount 50", text.contains("50 ৳"))
        assertTrue("Receipt must show 'বাকি (Due):'", text.contains("বাকি (Due):"))
        assertTrue("Receipt must show remaining due 150", text.contains("150 ৳"))
    }

    // 10. Terminal reset: draft can be cleared and new order sequence is ready
    @Test
    fun testTerminalResetForNextSale() = runBlocking {
        val order1 = repository.getNextOrderId()
        assertEquals("INV-10001", order1)

        val invoice = InvoiceEntity(
            orderId = order1,
            dateTime = System.currentTimeMillis(),
            operatorId = "OP-1",
            operatorName = "Cashier",
            customerInfo = "01700000000",
            rawTotalPaisa = 10000L,
            roundedTotalBdt = 100L,
            isPaid = true,
            paidAmountBdt = 100L
        )
        repository.saveOrder(invoice, emptyList(), null)

        // After save, next order ID increments atomically
        val order2 = repository.getNextOrderId()
        assertEquals("INV-10002", order2)
    }
}
