package com.example.pos

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosResult
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.PosRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditTransactionAtomicityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: PosDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: PosRepository
    private val dbName = "atomicity_test_${System.nanoTime()}.db"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val f = context.getDatabasePath(dbName)
        if (f.exists()) f.delete()

        db = PosDatabase.createFreshForTest(context, dbName)
        db.awaitInitialized()
        db.appSettingsDao().insertOrUpdate(AppSettingsEntity(id = 1, nextOrderSequence = 2001L))
        db.companyHeaderDao().insertOrUpdate(
            CompanyHeaderEntity(1, "Atomicity Station", "Main", "01", "Dhaka", "01700000000")
        )

        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dsFile = tempFolder.newFile("atomicity_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dsFile }
        val sessionManager = SecureSessionManager.createForTest(
            dataStore = dataStore,
            cipher = InMemoryAesGcmSessionCipher()
        )
        repository = PosRepository(context, db, sessionManager)
        assertTrue(repository.registerOperator("Atom Op", "01744000004", "Dhaka", "1234").isSuccess)
    }

    @After
    fun tearDown() {
        db.close()
        context.getDatabasePath(dbName).delete()
        dataStoreScope.cancel()
    }

    @Test
    fun testAuditInsertFailureRollsBackCheckoutDueSettlementAndProductAtomically() = runBlocking {
        // Seed one valid invoice BEFORE adding the trigger so we can test settleInvoiceDue rollback too
        val seedInv = InvoiceEntity(
            orderId = "INV-SEED-ATOMIC",
            globalTransactionId = "GLB-SEED-ATOMIC",
            branchCode = "01",
            dateTime = 1_765_000_000_000L,
            operatorId = "OP-X",
            operatorName = "Atom Op",
            customerInfo = "01911000111",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            isPaid = false,
            paidAmountBdt = 100L
        )
        assertTrue(
            repository.checkoutOrderAtomic(
                seedInv,
                listOf(InvoiceLineEntity(orderId = "INV-SEED-ATOMIC", itemId = "item-1", itemName = "Octane", itemType = "Liquid", unit = "L", quantityStr = "5", unitPricePaisa = 10000L, lineTotalPaisa = 50000L)),
                "01911000111"
            ) is PosResult.Success
        )
        val seqBeforeRollback = db.appSettingsDao().getSettings()!!.nextOrderSequence
        val auditCountBefore = db.auditLogDao().getTotalAuditLogCountFlow().first()

        // Install trigger to force SQLite ABORT on any audit_logs insert
        val writable = db.openHelper.writableDatabase
        writable.execSQL(
            """
            CREATE TRIGGER fail_audit_insert
            BEFORE INSERT ON audit_logs
            BEGIN
                SELECT RAISE(ABORT, 'simulated audit insert failure');
            END;
            """.trimIndent()
        )

        // 1. Checkout must fail and roll back invoice, lines, payment, customer, and sequence
        val rollbackInv = InvoiceEntity(
            orderId = "INV-ROLLBACK-999",
            globalTransactionId = "GLB-ROLLBACK-999",
            branchCode = "01",
            dateTime = 1_765_000_005_000L,
            operatorId = "OP-X",
            operatorName = "Atom Op",
            customerInfo = "01999888777",
            rawTotalPaisa = 25000L,
            roundedTotalBdt = 250L,
            isPaid = true,
            paidAmountBdt = 250L
        )
        val checkoutRes = repository.checkoutOrderAtomic(
            rollbackInv,
            listOf(InvoiceLineEntity(orderId = "INV-ROLLBACK-999", itemId = "item-1", itemName = "Octane", itemType = "Liquid", unit = "L", quantityStr = "2", unitPricePaisa = 12500L, lineTotalPaisa = 25000L)),
            "01999888777"
        )
        assertTrue("Checkout must return PosResult.Error on audit failure", checkoutRes is PosResult.Error)
        assertNull("Invoice must be rolled back", db.invoiceDao().getInvoiceById("INV-ROLLBACK-999"))
        assertTrue("Invoice lines must be rolled back", db.invoiceDao().getLinesForOrder("INV-ROLLBACK-999").isEmpty())
        assertTrue("Payment records must be rolled back", db.paymentDao().getPaymentsForOrderDirect("INV-ROLLBACK-999").isEmpty())
        assertNull("Customer CRM upsert must be rolled back", repository.getCustomerByInfo("01999888777"))
        assertEquals("Order sequence must be rolled back", seqBeforeRollback, db.appSettingsDao().getSettings()!!.nextOrderSequence)

        // 2. Due settlement must roll back paidAmountBdt & payment record
        val settleRes = repository.settleInvoiceDue("INV-SEED-ATOMIC", 400L, "CASH")
        assertTrue(settleRes is PosResult.Error)
        val afterSeed = db.invoiceDao().getInvoiceById("INV-SEED-ATOMIC")!!
        assertEquals(100L, afterSeed.paidAmountBdt)
        assertFalse(afterSeed.isPaid)
        assertEquals(1, db.paymentDao().getPaymentsForOrderDirect("INV-SEED-ATOMIC").size)

        // 3. Product creation must roll back
        val prodRes = repository.saveProductAudited(
            ProductEntity(
                itemId = "prod-rollback",
                itemName = "Should Not Exist",
                itemType = "Product",
                unit = "Pcs",
                defaultUnitPricePaisa = 10000L
            )
        )
        assertTrue(prodRes is PosResult.Error)
        assertNull(db.productDao().getProductById("prod-rollback"))

        // Drop trigger and confirm audit count never grew
        writable.execSQL("DROP TRIGGER IF EXISTS fail_audit_insert")
        assertEquals(auditCountBefore, db.auditLogDao().getTotalAuditLogCountFlow().first())
    }

    @Test
    fun testNoOpEditsAndFailedValidationsEmitZeroSpuriousAuditLogs() = runBlocking {
        // Seed product & company & settings
        val prodRes = repository.saveProductAudited(
            ProductEntity(
                itemId = "prod-noop",
                itemName = "Noop Item",
                nameBn = "নোপ আইটেম",
                nameEn = "Noop Item",
                itemType = "Product",
                unit = "Pcs",
                defaultUnitPricePaisa = 20000L,
                sortOrder = 1
            )
        )
        val savedProd = (prodRes as PosResult.Success).data
        repository.updateFooterNotes("Line A", "Line B")
        repository.updateLanguage("bn")

        val baselineCount = db.auditLogDao().getTotalAuditLogCountFlow().first()

        // Repeat exact same values (no-op edits) -> ZERO new audit rows!
        repository.saveProductAudited(savedProd)
        repository.company.updateCompanyHeader("Atomicity Station", "Main", "01", "01700000000", "Dhaka")
        repository.updateFooterNotes("Line A", "Line B")
        repository.updateLanguage("bn")
        repository.updateThemeMode("LIGHT")
        repository.updatePaperSize("58mm")
        repository.updateAutoCutter(true)
        repository.updateOperatorProfile("Atom Op", "01744000004", "Dhaka")

        assertEquals("No-op edits must emit zero audit rows", baselineCount, db.auditLogDao().getTotalAuditLogCountFlow().first())

        // Failed validation on due settlement & delete non-existent order -> zero false success audit logs
        val badSettle = repository.settleInvoiceDue("NON-EXISTENT-INV", 100L)
        assertTrue(badSettle is PosResult.Error)
        val badDelete = repository.deleteOrderAudited("NON-EXISTENT-INV")
        assertTrue(badDelete is PosResult.Error)

        val logs = db.auditLogDao().getAllAuditLogsDirect()
        assertFalse(logs.any { it.actionType == AuditActionType.PAYMENT_DUE_SETTLED && it.isSuccess })
        assertFalse(logs.any { it.actionType == AuditActionType.INVOICE_DELETED && it.isSuccess })
        assertEquals(baselineCount, logs.size)
    }
}
