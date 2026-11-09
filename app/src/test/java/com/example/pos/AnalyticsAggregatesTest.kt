package com.example.pos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.PosDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnalyticsAggregatesTest {

    private lateinit var db: PosDatabase

    private val utcDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseUtc(str: String): Long = utcDateFormat.parse(str)!!.time

    @Before
    fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSalesAggregationByDayMonthYearWithKnownValues() = runBlocking {
        val dao = db.invoiceDao()

        val t1 = parseUtc("2024-01-15 10:00:00")
        val t2 = parseUtc("2024-01-15 14:30:00")
        val t3 = parseUtc("2024-01-15 18:00:00")
        val t4 = parseUtc("2024-01-16 09:15:00")
        val t5 = parseUtc("2024-01-16 16:45:00")
        val t6 = parseUtc("2024-02-10 11:20:00")
        val t7 = parseUtc("2025-03-01 12:00:00")

        val invoices = listOf(
            InvoiceEntity(orderId = "INV-001", customerInfo = "01711111111", rawTotalPaisa = 1050L, roundedTotalBdt = 11L, dateTime = t1, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-002", customerInfo = "01722222222", rawTotalPaisa = 2025L, roundedTotalBdt = 20L, dateTime = t2, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-003", customerInfo = "01733333333", rawTotalPaisa = 5000L, roundedTotalBdt = 50L, dateTime = t3, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-004", customerInfo = "01744444444", rawTotalPaisa = 3000L, roundedTotalBdt = 30L, dateTime = t4, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-005", customerInfo = "01755555555", rawTotalPaisa = 7000L, roundedTotalBdt = 70L, dateTime = t5, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-006", customerInfo = "01766666666", rawTotalPaisa = 15000L, roundedTotalBdt = 150L, dateTime = t6, operatorId = "OP-1", operatorName = "Operator A"),
            InvoiceEntity(orderId = "INV-007", customerInfo = "01777777777", rawTotalPaisa = 40000L, roundedTotalBdt = 400L, dateTime = t7, operatorId = "OP-1", operatorName = "Operator A")
        )

        dao.insertInvoices(invoices)

        // 1. Test Sales by Day
        val dayAggregates = dao.getSalesByDayFlow(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = parseUtc("2024-01-17 00:00:00")
        ).first()
        assertEquals(2, dayAggregates.size)

        val day1 = dayAggregates.first { it.periodKey == "2024-01-15" }
        assertEquals(3, day1.transactionCount)
        assertEquals(1050L + 2025L + 5000L, day1.totalRawPaisa) // 8075L
        assertEquals(11L + 20L + 50L, day1.totalRoundedBdt) // 81L

        val day2 = dayAggregates.first { it.periodKey == "2024-01-16" }
        assertEquals(2, day2.transactionCount)
        assertEquals(3000L + 7000L, day2.totalRawPaisa) // 10000L
        assertEquals(30L + 70L, day2.totalRoundedBdt) // 100L

        // 2. Test Sales by Month
        val monthAggregates = dao.getSalesByMonthFlow(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = parseUtc("2024-12-31 23:59:59")
        ).first()
        assertEquals(2, monthAggregates.size)

        val jan = monthAggregates.first { it.periodKey == "2024-01" }
        assertEquals(5, jan.transactionCount)
        assertEquals(8075L + 10000L, jan.totalRawPaisa) // 18075L
        assertEquals(81L + 100L, jan.totalRoundedBdt) // 181L

        val feb = monthAggregates.first { it.periodKey == "2024-02" }
        assertEquals(1, feb.transactionCount)
        assertEquals(15000L, feb.totalRawPaisa)
        assertEquals(150L, feb.totalRoundedBdt)

        // 3. Test Sales by Year
        val yearAggregates = dao.getSalesByYearFlow(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = Long.MAX_VALUE
        ).first()
        assertEquals(2, yearAggregates.size)

        val y2024 = yearAggregates.first { it.periodKey == "2024" }
        assertEquals(6, y2024.transactionCount)
        assertEquals(18075L + 15000L, y2024.totalRawPaisa) // 33075L
        assertEquals(181L + 150L, y2024.totalRoundedBdt) // 331L

        val y2025 = yearAggregates.first { it.periodKey == "2025" }
        assertEquals(1, y2025.transactionCount)
        assertEquals(40000L, y2025.totalRawPaisa)
        assertEquals(400L, y2025.totalRoundedBdt)

        // 4. Test Empty Result for Non-existent Range
        val emptyAggregates = dao.getSalesByDayFlow(
            query = "",
            orderIdFilter = "",
            customerFilter = "",
            startDate = parseUtc("2023-01-01 00:00:00"),
            endDate = parseUtc("2023-01-31 23:59:59")
        ).first()
        assertTrue(emptyAggregates.isEmpty())
    }

    @Test
    fun testScenario1FullPaymentOnInvoiceCreation() = runBlocking {
        val invoiceDao = db.invoiceDao()
        val paymentDao = db.paymentDao()

        val day1Start = parseUtc("2024-05-01 00:00:00")
        val day1End = parseUtc("2024-05-01 23:59:59")
        val day1Time = parseUtc("2024-05-01 10:30:00")

        // Day 1: Invoice Total 1,000 BDT, Paid 1,000 BDT (Cash)
        val invoice = InvoiceEntity(
            orderId = "INV-S1-001",
            customerInfo = "01700000001",
            rawTotalPaisa = 100000L,
            roundedTotalBdt = 1000L,
            isPaid = true,
            paidAmountBdt = 1000L,
            dateTime = day1Time,
            operatorId = "OP-1",
            operatorName = "Operator A"
        )
        invoiceDao.insertInvoice(invoice)

        val payment = com.example.pos.data.db.PaymentRecordEntity(
            orderId = "INV-S1-001",
            paymentMethod = "CASH",
            amountPaidPaisa = 100000L,
            timestamp = day1Time
        )
        paymentDao.insertPayment(payment)

        // Day 1 Analytics Assertions
        val salesAgg = invoiceDao.getAggregatedSalesFlow("", day1Start, day1End).first()
        val payments = paymentDao.getPaymentMethodBreakdownFiltered(day1Start, day1End).first()
        val totalCollectionBdt = payments.sumOf { it.totalPaisa } / 100L
        val periodDue = invoiceDao.getPeriodDueFilteredFlow("", "", "", day1Start, day1End).first()
        val overallDue = invoiceDao.getOverallOutstandingDueFlow().first()

        assertEquals(1000L, salesAgg.totalRoundedBdt)
        assertEquals(1000L, totalCollectionBdt)
        assertEquals(0L, periodDue)
        assertEquals(0L, overallDue)
    }

    @Test
    fun testScenario2PartialPaymentWithSubsequentDueSettlement() = runBlocking {
        val invoiceDao = db.invoiceDao()
        val paymentDao = db.paymentDao()

        val day1Start = parseUtc("2024-05-01 00:00:00")
        val day1End = parseUtc("2024-05-01 23:59:59")
        val day1Time = parseUtc("2024-05-01 11:00:00")

        val day2Start = parseUtc("2024-05-02 00:00:00")
        val day2End = parseUtc("2024-05-02 23:59:59")
        val day2Time = parseUtc("2024-05-02 15:30:00")

        // Day 1: Create Invoice Total 1,000 BDT, Paid 400 BDT (Cash)
        val invoice = InvoiceEntity(
            orderId = "INV-S2-001",
            customerInfo = "01700000002",
            rawTotalPaisa = 100000L,
            roundedTotalBdt = 1000L,
            isPaid = false,
            paidAmountBdt = 400L,
            dateTime = day1Time,
            operatorId = "OP-1",
            operatorName = "Operator A"
        )
        invoiceDao.insertInvoice(invoice)

        paymentDao.insertPayment(
            com.example.pos.data.db.PaymentRecordEntity(
                orderId = "INV-S2-001",
                paymentMethod = "CASH",
                amountPaidPaisa = 40000L,
                timestamp = day1Time
            )
        )

        // Day 2: Settle Due: 600 BDT (bKash) on the Day 1 invoice
        invoiceDao.settleInvoiceDueAtomic(
            orderId = "INV-S2-001",
            additionalPaymentBdt = 600L,
            paymentRecord = com.example.pos.data.db.PaymentRecordEntity(
                orderId = "INV-S2-001",
                paymentMethod = "BKASH",
                amountPaidPaisa = 60000L,
                timestamp = day2Time
            )
        )

        // Day 1 Analytics (Date filter = Day 1)
        val day1Sales = invoiceDao.getAggregatedSalesFlow("", day1Start, day1End).first().totalRoundedBdt
        val day1Payments = paymentDao.getPaymentMethodBreakdownFiltered(day1Start, day1End).first()
        val day1CollectionBdt = day1Payments.sumOf { it.totalPaisa } / 100L
        val day1PeriodDue = invoiceDao.getPeriodDueFilteredFlow("", "", "", day1Start, day1End).first()
        val day1OverallDue = invoiceDao.getOverallOutstandingDueFlow().first()

        assertEquals(1000L, day1Sales)
        assertEquals(400L, day1CollectionBdt)
        assertEquals(0L, day1PeriodDue) // Day 1 invoice is now fully settled
        assertEquals(0L, day1OverallDue)

        // Day 2 Analytics (Date filter = Day 2)
        val day2Sales = invoiceDao.getAggregatedSalesFlow("", day2Start, day2End).first().totalRoundedBdt
        val day2Payments = paymentDao.getPaymentMethodBreakdownFiltered(day2Start, day2End).first()
        val day2CollectionBdt = day2Payments.sumOf { it.totalPaisa } / 100L
        val day2PeriodDue = invoiceDao.getPeriodDueFilteredFlow("", "", "", day2Start, day2End).first()
        val day2OverallDue = invoiceDao.getOverallOutstandingDueFlow().first()

        assertEquals(0L, day2Sales)
        assertEquals(600L, day2CollectionBdt)
        assertEquals(0L, day2PeriodDue)
        assertEquals(0L, day2OverallDue)

        // Multi-Day Analytics (Date filter = Day 1 to Day 2)
        val multiSales = invoiceDao.getAggregatedSalesFlow("", day1Start, day2End).first().totalRoundedBdt
        val multiPayments = paymentDao.getPaymentMethodBreakdownFiltered(day1Start, day2End).first()
        val multiCollectionBdt = multiPayments.sumOf { it.totalPaisa } / 100L
        val multiPeriodDue = invoiceDao.getPeriodDueFilteredFlow("", "", "", day1Start, day2End).first()
        val multiOverallDue = invoiceDao.getOverallOutstandingDueFlow().first()

        assertEquals(1000L, multiSales)
        assertEquals(1000L, multiCollectionBdt)
        assertEquals(0L, multiPeriodDue)
        assertEquals(0L, multiOverallDue)
    }

    @Test
    fun testScenario3UnpaidInvoiceAcrossDateFilters() = runBlocking {
        val invoiceDao = db.invoiceDao()
        val paymentDao = db.paymentDao()

        val day1Start = parseUtc("2024-05-01 00:00:00")
        val day1End = parseUtc("2024-05-01 23:59:59")
        val day1Time = parseUtc("2024-05-01 14:00:00")

        val day2Start = parseUtc("2024-05-02 00:00:00")
        val day2End = parseUtc("2024-05-02 23:59:59")

        // Day 1: Create Invoice Total 500 BDT, Paid 0 BDT
        val invoice = InvoiceEntity(
            orderId = "INV-S3-001",
            customerInfo = "01700000003",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            isPaid = false,
            paidAmountBdt = 0L,
            dateTime = day1Time,
            operatorId = "OP-1",
            operatorName = "Operator A"
        )
        invoiceDao.insertInvoice(invoice)

        // Day 1 Analytics (Date filter = Day 1)
        val day1Sales = invoiceDao.getAggregatedSalesFlow("", day1Start, day1End).first().totalRoundedBdt
        val day1Payments = paymentDao.getPaymentMethodBreakdownFiltered(day1Start, day1End).first()
        val day1Collection = day1Payments.sumOf { it.totalPaisa } / 100L
        val day1PeriodDue = invoiceDao.getPeriodDueFilteredFlow("", "", "", day1Start, day1End).first()
        val day1OverallDue = invoiceDao.getOverallOutstandingDueFlow().first()

        assertEquals(500L, day1Sales)
        assertEquals(0L, day1Collection)
        assertEquals(500L, day1PeriodDue)
        assertEquals(500L, day1OverallDue)

        // Day 2 Analytics (Date filter = Day 2)
        val day2Sales = invoiceDao.getAggregatedSalesFlow("", day2Start, day2End).first().totalRoundedBdt
        val day2Payments = paymentDao.getPaymentMethodBreakdownFiltered(day2Start, day2End).first()
        val day2Collection = day2Payments.sumOf { it.totalPaisa } / 100L
        val day2PeriodDue = invoiceDao.getPeriodDueFilteredFlow("", "", "", day2Start, day2End).first()
        val day2OverallDue = invoiceDao.getOverallOutstandingDueFlow().first()

        assertEquals(0L, day2Sales)
        assertEquals(0L, day2Collection)
        assertEquals(0L, day2PeriodDue) // No invoice created on Day 2
        assertEquals(500L, day2OverallDue) // Reflects the unpaid Day 1 invoice across all time
    }

    @Test
    fun testScenario4MultiplePaymentMethodsInDateRange() = runBlocking {
        val invoiceDao = db.invoiceDao()
        val paymentDao = db.paymentDao()

        val day1Start = parseUtc("2024-05-01 00:00:00")
        val day1End = parseUtc("2024-05-01 23:59:59")
        val t1 = parseUtc("2024-05-01 09:00:00")
        val t2 = parseUtc("2024-05-01 12:00:00")
        val t3 = parseUtc("2024-05-01 16:00:00")

        // Invoice 1: 1,000 BDT paid in Cash
        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-S4-001",
                customerInfo = "01700000010",
                rawTotalPaisa = 100000L,
                roundedTotalBdt = 1000L,
                isPaid = true,
                paidAmountBdt = 1000L,
                dateTime = t1,
                operatorId = "OP-1",
                operatorName = "Operator A"
            )
        )
        paymentDao.insertPayment(
            com.example.pos.data.db.PaymentRecordEntity(
                orderId = "INV-S4-001",
                paymentMethod = "CASH",
                amountPaidPaisa = 100000L,
                timestamp = t1
            )
        )

        // Invoice 2: 500 BDT paid via bKash
        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-S4-002",
                customerInfo = "01700000020",
                rawTotalPaisa = 50000L,
                roundedTotalBdt = 500L,
                isPaid = true,
                paidAmountBdt = 500L,
                dateTime = t2,
                operatorId = "OP-1",
                operatorName = "Operator A"
            )
        )
        paymentDao.insertPayment(
            com.example.pos.data.db.PaymentRecordEntity(
                orderId = "INV-S4-002",
                paymentMethod = "BKASH",
                amountPaidPaisa = 50000L,
                timestamp = t2
            )
        )

        // Invoice 3: 2,000 BDT paid via Card
        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-S4-003",
                customerInfo = "01700000030",
                rawTotalPaisa = 200000L,
                roundedTotalBdt = 2000L,
                isPaid = true,
                paidAmountBdt = 2000L,
                dateTime = t3,
                operatorId = "OP-1",
                operatorName = "Operator A"
            )
        )
        paymentDao.insertPayment(
            com.example.pos.data.db.PaymentRecordEntity(
                orderId = "INV-S4-003",
                paymentMethod = "CARD",
                amountPaidPaisa = 200000L,
                timestamp = t3
            )
        )

        // Day 1 Analytics Assertions
        val payments = paymentDao.getPaymentMethodBreakdownFiltered(day1Start, day1End).first()
        val totalCollectionBdt = payments.sumOf { it.totalPaisa } / 100L

        assertEquals(3500L, totalCollectionBdt)

        val cashSummary = payments.first { it.payment_method == "CASH" }
        assertEquals(1000L, cashSummary.totalPaisa / 100L)

        val bkashSummary = payments.first { it.payment_method == "BKASH" }
        assertEquals(500L, bkashSummary.totalPaisa / 100L)

        val cardSummary = payments.first { it.payment_method == "CARD" }
        assertEquals(2000L, cardSummary.totalPaisa / 100L)
    }

    @Test
    fun testScenario5MixedFinancialScenarioWithOverlappingDates() = runBlocking {
        val invoiceDao = db.invoiceDao()
        val paymentDao = db.paymentDao()

        val day1Start = parseUtc("2024-05-01 00:00:00")
        val day1End = parseUtc("2024-05-01 23:59:59")
        val t1 = parseUtc("2024-05-01 10:00:00")

        val day2Start = parseUtc("2024-05-02 00:00:00")
        val day2End = parseUtc("2024-05-02 23:59:59")
        val t2 = parseUtc("2024-05-02 11:00:00")

        val day3Start = parseUtc("2024-05-03 00:00:00")
        val day3End = parseUtc("2024-05-03 23:59:59")
        val t3 = parseUtc("2024-05-03 14:00:00")

        // 1. Day 1: Invoice A (1200 BDT, Paid 700 BDT Cash, Remaining Due 500 BDT)
        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-MIX-A",
                customerInfo = "01700000091",
                rawTotalPaisa = 120000L,
                roundedTotalBdt = 1200L,
                isPaid = false,
                paidAmountBdt = 700L,
                dateTime = t1,
                operatorId = "OP-1",
                operatorName = "Operator A"
            )
        )
        paymentDao.insertPayment(
            com.example.pos.data.db.PaymentRecordEntity(
                orderId = "INV-MIX-A",
                paymentMethod = "CASH",
                amountPaidPaisa = 70000L,
                timestamp = t1
            )
        )

        // 2. Day 2: Invoice B (800 BDT, Unpaid 0 BDT)
        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-MIX-B",
                customerInfo = "01700000092",
                rawTotalPaisa = 80000L,
                roundedTotalBdt = 800L,
                isPaid = false,
                paidAmountBdt = 0L,
                dateTime = t2,
                operatorId = "OP-1",
                operatorName = "Operator A"
            )
        )

        // 3. Day 3: Settle 300 BDT of Invoice A via bKash, and create Invoice C (500 BDT fully paid via Card)
        invoiceDao.settleInvoiceDueAtomic(
            orderId = "INV-MIX-A",
            additionalPaymentBdt = 300L,
            paymentRecord = com.example.pos.data.db.PaymentRecordEntity(
                orderId = "INV-MIX-A",
                paymentMethod = "BKASH",
                amountPaidPaisa = 30000L,
                timestamp = t3
            )
        )

        invoiceDao.insertInvoice(
            InvoiceEntity(
                orderId = "INV-MIX-C",
                customerInfo = "01700000093",
                rawTotalPaisa = 50000L,
                roundedTotalBdt = 500L,
                isPaid = true,
                paidAmountBdt = 500L,
                dateTime = t3,
                operatorId = "OP-1",
                operatorName = "Operator A"
            )
        )
        paymentDao.insertPayment(
            com.example.pos.data.db.PaymentRecordEntity(
                orderId = "INV-MIX-C",
                paymentMethod = "CARD",
                amountPaidPaisa = 50000L,
                timestamp = t3
            )
        )

        // Test Day 1:
        // Sales = 1200 BDT
        // Collection = 700 BDT
        // Period Due = 200 BDT (Invoice A was 1200, paid 700+300=1000, so remaining due is 200)
        // Overall Due = 200 (from A) + 800 (from B) = 1000 BDT
        val d1Sales = invoiceDao.getAggregatedSalesFlow("", day1Start, day1End).first().totalRoundedBdt
        val d1Payments = paymentDao.getPaymentMethodBreakdownFiltered(day1Start, day1End).first()
        val d1Collection = d1Payments.sumOf { it.totalPaisa } / 100L
        val d1PeriodDue = invoiceDao.getPeriodDueFilteredFlow("", "", "", day1Start, day1End).first()
        val d1OverallDue = invoiceDao.getOverallOutstandingDueFlow().first()

        assertEquals(1200L, d1Sales)
        assertEquals(700L, d1Collection)
        assertEquals(200L, d1PeriodDue)
        assertEquals(1000L, d1OverallDue)

        // Test Day 2:
        // Sales = 800 BDT
        // Collection = 0 BDT
        // Period Due = 800 BDT (Invoice B)
        // Overall Due = 1000 BDT (A has 200, B has 800)
        val d2Sales = invoiceDao.getAggregatedSalesFlow("", day2Start, day2End).first().totalRoundedBdt
        val d2Payments = paymentDao.getPaymentMethodBreakdownFiltered(day2Start, day2End).first()
        val d2Collection = d2Payments.sumOf { it.totalPaisa } / 100L
        val d2PeriodDue = invoiceDao.getPeriodDueFilteredFlow("", "", "", day2Start, day2End).first()

        assertEquals(800L, d2Sales)
        assertEquals(0L, d2Collection)
        assertEquals(800L, d2PeriodDue)

        // Test Day 3:
        // Sales = 500 BDT (Invoice C)
        // Collection = 300 BDT (bKash on A) + 500 BDT (Card on C) = 800 BDT
        // Period Due = 0 BDT (Invoice C was created today and is fully paid)
        // Overall Due = 1000 BDT (A has 200, B has 800)
        val d3Sales = invoiceDao.getAggregatedSalesFlow("", day3Start, day3End).first().totalRoundedBdt
        val d3Payments = paymentDao.getPaymentMethodBreakdownFiltered(day3Start, day3End).first()
        val d3Collection = d3Payments.sumOf { it.totalPaisa } / 100L
        val d3PeriodDue = invoiceDao.getPeriodDueFilteredFlow("", "", "", day3Start, day3End).first()

        assertEquals(500L, d3Sales)
        assertEquals(800L, d3Collection)
        assertEquals(0L, d3PeriodDue)

        // Test All 3 Days Combined (day1Start to day3End):
        // Total Sales = 1200 + 800 + 500 = 2500 BDT
        // Total Collection = 700 + 300 + 500 = 1500 BDT
        // Period Due = 200 (A) + 800 (B) = 1000 BDT
        // Overall Due = 1000 BDT
        val fullSales = invoiceDao.getAggregatedSalesFlow("", day1Start, day3End).first().totalRoundedBdt
        val fullPayments = paymentDao.getPaymentMethodBreakdownFiltered(day1Start, day3End).first()
        val fullCollection = fullPayments.sumOf { it.totalPaisa } / 100L
        val fullPeriodDue = invoiceDao.getPeriodDueFilteredFlow("", "", "", day1Start, day3End).first()
        val fullOverallDue = invoiceDao.getOverallOutstandingDueFlow().first()

        assertEquals(2500L, fullSales)
        assertEquals(1500L, fullCollection)
        assertEquals(1000L, fullPeriodDue)
        assertEquals(1000L, fullOverallDue)
    }
}
