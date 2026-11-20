package com.example.pos

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosResult
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.CustomerEntity
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
import org.junit.Assert.assertNotNull
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
class MultiOperatorAttributionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: PosDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: PosRepository
    private var nowMillis = 1_765_000_000_000L

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.appSettingsDao().insertOrUpdate(AppSettingsEntity(id = 1, nextOrderSequence = 1001L))
        db.companyHeaderDao().insertOrUpdate(
            CompanyHeaderEntity(1, "Station", "Main", "01", "Dhaka", "01700000000")
        )
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dsFile = tempFolder.newFile("multi_op_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dsFile }
        val sessionManager = SecureSessionManager.createForTest(
            dataStore = dataStore,
            cipher = InMemoryAesGcmSessionCipher()
        )
        repository = PosRepository(context, db, sessionManager) {
            nowMillis += 1000L
            nowMillis
        }
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreScope.cancel()
    }

    @Test
    fun testMultiOperatorInvoiceProductCustomerAttributionAndSharedVisibility() = runBlocking {
        // 1. Register Operator A and create partial-paid INV-1001 (Total 500, Paid 200, Due 300)
        val opA = repository.registerOperator("Operator Alpha", "01711000001", "Branch A", "1234").getOrThrow()

        val invoiceTemplate = InvoiceEntity(
            orderId = "INV-1001",
            globalTransactionId = "GLB-1001",
            branchCode = "01",
            dateTime = nowMillis,
            operatorId = opA.userId,
            operatorName = opA.operatorName,
            customerInfo = "01811000001",
            rawTotalPaisa = 50000L,
            roundedTotalBdt = 500L,
            isPaid = false,
            paidAmountBdt = 200L,
            footerNote = "Initial note"
        )
        val lines = listOf(
            InvoiceLineEntity(
                orderId = "INV-1001",
                itemId = "item-1",
                itemName = "Octane",
                itemType = "Liquid",
                unit = "Liter",
                quantityStr = "4",
                unitPricePaisa = 12500L,
                lineTotalPaisa = 50000L,
                sortOrder = 1
            )
        )
        val checkoutRes = repository.checkoutOrderAtomic(invoiceTemplate, lines, "01811000001")
        assertTrue(checkoutRes is PosResult.Success)

        // Operator A also creates a Product and a Customer
        val prodRes = repository.saveProductAudited(
            ProductEntity(
                itemId = "prod-shared-1",
                itemName = "Engine Oil",
                nameBn = "ইঞ্জিন অয়েল",
                nameEn = "Engine Oil",
                itemType = "Product",
                unit = "Pcs",
                defaultUnitPricePaisa = 50000L,
                sortOrder = 1
            )
        )
        assertTrue(prodRes is PosResult.Success)

        repository.saveCustomer(
            CustomerEntity(
                customerInfo = "CUST-VIP-1",
                customerName = "Karim Motors",
                phoneNumber = "01811000099",
                address = "Bogura",
                notes = "VIP"
            )
        )

        // 2. Logout A, register & activate Operator B
        repository.logout()
        val opB = repository.registerOperator("Operator Beta", "01722000002", "Branch B", "5678").getOrThrow()

        // Operator B updates INV-1001 customer & footer note
        val editRes = repository.updateInvoiceCustomerAndNoteAudited(
            orderId = "INV-1001",
            customerInfo = "01811000001 (Updated by B)",
            footerNote = "Verified by B"
        )
        assertTrue(editRes is PosResult.Success)

        // Operator B settles the remaining 300 BDT due
        val settleRes = repository.settleInvoiceDue("INV-1001", 300L, "CASH")
        assertTrue(settleRes is PosResult.Success)

        // Operator B updates the Product created by A & edits Customer created by A
        val editedProduct = (prodRes as PosResult.Success).data.copy(defaultUnitPricePaisa = 55000L)
        val prodEditRes = repository.saveProductAudited(editedProduct)
        assertTrue(prodEditRes is PosResult.Success)

        val existingCust = repository.getCustomerByInfo("CUST-VIP-1")!!
        repository.saveCustomer(existingCust.copy(notes = "Verified VIP by Beta"))

        // 3. Logout B, register Operator C and inspect ALL shared records + attribution
        repository.logout()
        val opC = repository.registerOperator("Operator Gamma", "01733000003", "Branch C", "9999").getOrThrow()
        assertNotNull(opC)

        // C sees the invoice created by A and edited/settled by B
        val inv = repository.getInvoiceById("INV-1001")!!
        assertEquals("Creator operatorId must stay A", opA.userId, inv.operatorId)
        assertEquals("Creator operatorName must stay A", opA.operatorName, inv.operatorName)
        assertEquals("Modifier must be B", opB.userId, inv.updatedByOperatorId)
        assertEquals("Modifier name must be B", opB.operatorName, inv.updatedByOperatorName)
        assertTrue(inv.isPaid)
        assertEquals(500L, inv.paidAmountBdt)

        // C sees both payment records with distinct collector attribution A and B
        val payments = repository.getPaymentsForOrderDirect("INV-1001")
        assertEquals(2, payments.size)
        assertEquals(20000L, payments[0].amountPaidPaisa)
        assertEquals(opA.userId, payments[0].collectedByOperatorId)
        assertEquals(opA.operatorName, payments[0].collectedByOperatorName)
        assertEquals(30000L, payments[1].amountPaidPaisa)
        assertEquals(opB.userId, payments[1].collectedByOperatorId)
        assertEquals(opB.operatorName, payments[1].collectedByOperatorName)

        // C sees product created by A and updated by B
        val prod = db.productDao().getProductById("prod-shared-1")!!
        assertEquals(opA.userId, prod.createdByOperatorId)
        assertEquals(opA.operatorName, prod.createdByOperatorName)
        assertEquals(opB.userId, prod.updatedByOperatorId)
        assertEquals(opB.operatorName, prod.updatedByOperatorName)

        // C sees customer created by A and updated by B
        val cust = repository.getCustomerByInfo("CUST-VIP-1")!!
        assertEquals(opA.userId, cust.createdByOperatorId)
        assertEquals(opA.operatorName, cust.createdByOperatorName)
        assertEquals(opB.userId, cust.updatedByOperatorId)
        assertEquals(opB.operatorName, cust.updatedByOperatorName)

        // C queries audit logs for INV-1001 and sees A created/collected, B updated/settled
        val invAudit = repository.audit.getAuditLogsForEntity(AuditEntityType.INVOICE, "INV-1001").first()
        assertEquals(2, invAudit.size)
        assertTrue(invAudit.any { it.actionType == AuditActionType.INVOICE_CREATED && it.operatorId == opA.userId })
        assertTrue(invAudit.any { it.actionType == AuditActionType.INVOICE_METADATA_UPDATED && it.operatorId == opB.userId })

        val payAudit = repository.audit.getAuditLogsForEntity(AuditEntityType.PAYMENT, "INV-1001").first()
        assertEquals(2, payAudit.size)
        assertTrue(payAudit.any { it.actionType == AuditActionType.PAYMENT_COLLECTED_CHECKOUT && it.operatorId == opA.userId })
        assertTrue(payAudit.any { it.actionType == AuditActionType.PAYMENT_DUE_SETTLED && it.operatorId == opB.userId })

        // Verify distinct audit operators lists A, B, and C
        val distinctOps = repository.audit.getDistinctAuditOperators().first()
        assertTrue(distinctOps.any { it.operatorId == opA.userId })
        assertTrue(distinctOps.any { it.operatorId == opB.userId })
        assertTrue(distinctOps.any { it.operatorId == opC.userId })

        // Shared history count is 1 visible regardless of current active operator C
        val pagedInvoices = repository.getInvoicesPaged("", 0L, Long.MAX_VALUE, 25, 0).first()
        assertEquals(1, pagedInvoices.size)
        assertFalse(pagedInvoices.isEmpty())
        assertNull(db.productDao().getProductById("non-existent"))
    }
}
