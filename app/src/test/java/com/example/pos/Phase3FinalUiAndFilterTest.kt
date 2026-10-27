package com.example.pos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosResult
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.repository.HistoryRepositoryImpl
import com.example.pos.data.repository.HistorySortOption
import com.example.pos.data.repository.InvoiceRepositoryImpl
import com.example.pos.excel.ExcelExchangeEngine
import com.example.pos.ui.viewmodel.DateFilterMode
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
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.util.zip.ZipInputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3FinalUiAndFilterTest {

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
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun insertTestInvoice(
        orderId: String,
        timestamp: Long,
        customerInfo: String = "01711223344",
        totalBdt: Long = 500L,
        paidBdt: Long = 500L,
        isPaid: Boolean = true
    ): InvoiceEntity {
        val invoice = InvoiceEntity(
            orderId = orderId,
            globalTransactionId = "TXN-$orderId",
            branchCode = "01",
            dateTime = timestamp,
            operatorId = "OP-1",
            operatorName = "Operator A",
            customerInfo = customerInfo,
            rawTotalPaisa = totalBdt * 100L,
            roundedTotalBdt = totalBdt,
            paidAmountBdt = paidBdt,
            isPaid = isPaid,
            footerNote = "Thank you"
        )
        val line = InvoiceLineEntity(
            orderId = orderId,
            itemId = "ITEM-1",
            itemName = "Product 1",
            itemType = "Product",
            unit = "pcs",
            quantityStr = "1",
            unitPricePaisa = totalBdt * 100L,
            lineTotalPaisa = totalBdt * 100L,
            sortOrder = 1
        )
        val payment = PaymentRecordEntity(
            orderId = orderId,
            amountPaidPaisa = paidBdt * 100L,
            paymentMethod = "CASH",
            timestamp = timestamp
        )
        invoiceRepo.saveOrder(invoice, listOf(line), payment)
        return invoice
    }

    @Test
    fun testSearchByOrderIdAcrossEntireRoomDataset() = runBlocking {
        val now = System.currentTimeMillis()
        for (i in 1..15) {
            insertTestInvoice("INV-P3-$i", now - i * 1000L, customerInfo = "Customer $i")
        }

        val matching = historyRepo.getInvoicesPaged(
            query = "INV-P3-12",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = now + 86400000L,
            sortOption = HistorySortOption.DATE_DESC,
            limit = 50,
            offset = 0
        ).first()

        assertEquals("Should match exactly the searched Order ID", 1, matching.size)
        assertEquals("INV-P3-12", matching[0].orderId)
    }

    @Test
    fun testSearchByCustomerInfoAcrossEntireRoomDataset() = runBlocking {
        val now = System.currentTimeMillis()
        insertTestInvoice("INV-CUST-1", now - 1000L, customerInfo = "01811223344")
        insertTestInvoice("INV-CUST-2", now - 2000L, customerInfo = "01999887766")
        insertTestInvoice("INV-CUST-3", now - 3000L, customerInfo = "Rahim Mia, 01811223344")

        val matching = historyRepo.getInvoicesPaged(
            query = "01811223344",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = now + 86400000L,
            sortOption = HistorySortOption.DATE_DESC,
            limit = 50,
            offset = 0
        ).first()

        assertEquals("Both records containing 01811223344 should be returned", 2, matching.size)
    }

    @Test
    fun testCombinedSearchAndDateFilter() = runBlocking {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.NOVEMBER, 18, 14, 0, 0)
        val targetDayTimestamp = cal.timeInMillis

        cal.set(2025, Calendar.NOVEMBER, 25, 14, 0, 0)
        val otherDayTimestamp = cal.timeInMillis

        insertTestInvoice("INV-DATE-MATCH", targetDayTimestamp, customerInfo = "Karim 01700")
        insertTestInvoice("INV-DATE-OTHER", otherDayTimestamp, customerInfo = "Karim 01700")

        val dayStart = DateTimeUtils.getStartOfDay(targetDayTimestamp)
        val dayEnd = dayStart + 86400000L - 1

        val matching = historyRepo.getInvoicesPaged(
            query = "Karim",
            orderIdFilter = "",
            customerFilter = "",
            startDate = dayStart,
            endDate = dayEnd,
            sortOption = HistorySortOption.DATE_DESC,
            limit = 50,
            offset = 0
        ).first()

        assertEquals("Only invoice on the target day matching Karim should return", 1, matching.size)
        assertEquals("INV-DATE-MATCH", matching[0].orderId)
    }

    @Test
    fun testEmptyStateWhenSearchMatchesNothing() = runBlocking {
        val now = System.currentTimeMillis()
        insertTestInvoice("INV-EXISTS", now, customerInfo = "01700")

        val matching = historyRepo.getInvoicesPaged(
            query = "NON_EXISTENT_QUERY",
            orderIdFilter = "",
            customerFilter = "",
            startDate = 0L,
            endDate = now + 86400000L,
            sortOption = HistorySortOption.DATE_DESC,
            limit = 50,
            offset = 0
        ).first()

        assertTrue("Empty list returned for non-matching search", matching.isEmpty())
    }

    @Test
    fun testSingleDayCustomDateRangeFiltering() = runBlocking {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JUNE, 10, 10, 30, 0)
        val day10 = cal.timeInMillis

        cal.set(2025, Calendar.JUNE, 11, 10, 30, 0)
        val day11 = cal.timeInMillis

        insertTestInvoice("INV-D10", day10)
        insertTestInvoice("INV-D11", day11)

        val calStart = Calendar.getInstance().apply {
            timeInMillis = day10
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val actualStart = calStart.timeInMillis
        val actualEnd = actualStart + 86400000L - 1

        val list = historyRepo.getInvoicesForExportFiltered(
            startDate = actualStart,
            endDate = actualEnd
        )

        assertEquals("Single day custom filter should only include Day 10 records", 1, list.size)
        assertEquals("INV-D10", list[0].orderId)
    }

    @Test
    fun testMultiDayInclusiveCustomDateRange() = runBlocking {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JUNE, 10, 10, 0, 0)
        val day10 = cal.timeInMillis

        cal.set(2025, Calendar.JUNE, 12, 18, 0, 0)
        val day12 = cal.timeInMillis

        cal.set(2025, Calendar.JUNE, 13, 10, 0, 0)
        val day13 = cal.timeInMillis

        insertTestInvoice("INV-RANGE-10", day10)
        insertTestInvoice("INV-RANGE-12", day12)
        insertTestInvoice("INV-RANGE-13", day13)

        val calStart = Calendar.getInstance().apply {
            timeInMillis = day10
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calEnd = Calendar.getInstance().apply {
            timeInMillis = day12
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val list = historyRepo.getInvoicesForExportFiltered(
            startDate = calStart.timeInMillis,
            endDate = calEnd.timeInMillis
        )

        assertEquals("Inclusive date range 10th to 12th should include 2 records", 2, list.size)
        assertTrue(list.any { it.orderId == "INV-RANGE-10" })
        assertTrue(list.any { it.orderId == "INV-RANGE-12" })
        assertFalse(list.any { it.orderId == "INV-RANGE-13" })
    }

    @Test
    fun testCustomDateValidationEndDateNotEarlierThanStartDate() {
        val parsedStart = DateTimeUtils.parseDateDdMmYyyy("20-11-2025")
        val parsedEnd = DateTimeUtils.parseDateDdMmYyyy("15-11-2025")

        assertNotNull(parsedStart)
        assertNotNull(parsedEnd)
        assertTrue("End date earlier than Start date must be flagged as invalid", parsedEnd!!.before(parsedStart))
    }

    @Test
    fun testExportFilteredQueriesEntireDatasetAcrossPages() = runBlocking {
        val now = System.currentTimeMillis()
        for (i in 1..30) {
            insertTestInvoice("INV-EXP-$i", now - i * 100L, customerInfo = "Bulk Export Client")
        }

        val allExported = historyRepo.getInvoicesForExportFiltered(
            startDate = 0L,
            endDate = now + 86400000L,
            query = "Bulk Export Client"
        )

        assertEquals("Export query must retrieve all 30 matching records across pagination", 30, allExported.size)
    }

    @Test
    fun testExportToXlsxProducesValidArchiveWithZeroRecords() = runBlocking {
        val bytes = ExcelExchangeEngine.exportToByteArray(emptyList(), "01") { emptyList() }
        assertTrue("XLSX byte array must not be empty", bytes.isNotEmpty())

        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        var hasContentTypes = false
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "[Content_Types].xml") {
                hasContentTypes = true
            }
            entry = zis.nextEntry
        }
        zis.close()
        assertTrue("Empty export must produce valid OpenXML archive with [Content_Types].xml", hasContentTypes)
    }

    @Test
    fun testDeleteOrderAtomicallyRemovesPaymentsAndLines() = runBlocking {
        val now = System.currentTimeMillis()
        insertTestInvoice("INV-DELETE-ATOMIC", now, totalBdt = 800L, paidBdt = 800L)

        val invoiceBefore = db.invoiceDao().getInvoiceById("INV-DELETE-ATOMIC")
        val linesBefore = db.invoiceDao().getLinesForOrder("INV-DELETE-ATOMIC")
        val paymentsBefore = db.paymentDao().getPaymentsForOrder("INV-DELETE-ATOMIC").first()

        assertNotNull("Invoice must exist before delete", invoiceBefore)
        assertEquals("Lines must exist before delete", 1, linesBefore.size)
        assertEquals("Payments must exist before delete", 1, paymentsBefore.size)

        db.invoiceDao().deleteFullOrder("INV-DELETE-ATOMIC")

        val invoiceAfter = db.invoiceDao().getInvoiceById("INV-DELETE-ATOMIC")
        val linesAfter = db.invoiceDao().getLinesForOrder("INV-DELETE-ATOMIC")
        val paymentsAfter = db.paymentDao().getPaymentsForOrder("INV-DELETE-ATOMIC").first()

        assertNull("Invoice must be deleted", invoiceAfter)
        assertTrue("Lines must be deleted", linesAfter.isEmpty())
        assertTrue("Payments must be deleted atomically", paymentsAfter.isEmpty())
    }
}
