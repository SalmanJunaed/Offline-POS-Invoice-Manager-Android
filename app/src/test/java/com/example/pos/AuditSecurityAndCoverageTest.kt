package com.example.pos

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.CustomerEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.PosRepository
import com.example.pos.excel.ParseXlsxResult
import com.example.pos.excel.ParsedTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AuditSecurityAndCoverageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: PosDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: PosRepository
    private var nowMillis = 1_766_000_000_000L

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.appSettingsDao().insertOrUpdate(AppSettingsEntity(id = 1, nextOrderSequence = 3001L))
        db.companyHeaderDao().insertOrUpdate(
            CompanyHeaderEntity(1, "Security Station", "Main", "01", "Dhaka", "01700000000")
        )
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dsFile = tempFolder.newFile("security_audit_${System.nanoTime()}.preferences_pb")
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
    fun testAllAuthBusinessSettingsAndExchangeEventsLoggedWithZeroLeakedSecrets() = runBlocking {
        val secretPass = "SuperSecret9876"
        val badPass = "WrongGuess4321"
        val newPass = "RotatedSecret5555"
        val phone = "01755123456"

        // 1. Register with recovery
        val regRes = repository.registerOperatorWithRecovery("Sec Op", phone, "Main Branch", secretPass).getOrThrow()
        val initialRecoveryCode = regRes.recoveryCode
        val userId = regRes.operator.userId

        // 2. Profile update & Recovery code regeneration
        assertTrue(repository.updateOperatorProfile("Sec Op Updated", phone, "Branch 2").isSuccess)
        val regenCode = repository.regenerateRecoveryCode(userId).getOrThrow()

        // 3. Business, Company Header/Logo, Footer Notes, Printer, Language/Theme, Export & Import
        assertTrue(repository.company.updateCompanyHeader("Sec Station Pro", "North", "02", "01700000001", "Bogura") is PosResult.Success)
        val currentHeader = repository.getCompanyHeader()!!
        assertTrue(
            repository.updateCompanyHeaderAudited(currentHeader.copy(logoUri = "/tmp/logo.png"), logoChangeAction = "CUSTOM_CONFIGURED") is PosResult.Success
        )
        assertTrue(
            repository.updateCompanyHeaderAudited(currentHeader.copy(logoUri = null), logoChangeAction = "REMOVED") is PosResult.Success
        )
        repository.updateFooterNotes("Footer One", "Footer Two")
        repository.updatePaperSize("80mm")
        repository.updateAutoCutter(false)
        repository.updateBluetoothPrinter("AA:BB:CC:DD:EE:FF", "RPP02N")
        repository.updateLanguage("en")
        repository.updateThemeMode("DARK")

        val inv = InvoiceEntity(
            orderId = "INV-3001",
            globalTransactionId = "GLB-3001",
            branchCode = "02",
            dateTime = nowMillis,
            operatorId = userId,
            operatorName = "Sec Op Updated",
            customerInfo = "01811000222",
            rawTotalPaisa = 10000L,
            roundedTotalBdt = 100L,
            isPaid = true,
            paidAmountBdt = 100L
        )
        assertTrue(
            repository.checkoutOrderAtomic(
                inv,
                listOf(InvoiceLineEntity(orderId = "INV-3001", itemId = "item-1", itemName = "Octane", itemType = "Liquid", unit = "L", quantityStr = "1", unitPricePaisa = 10000L, lineTotalPaisa = 10000L)),
                "01811000222"
            ) is PosResult.Success
        )
        assertTrue(repository.recordXlsxExportAudit("FULL_FILE", 1, "ALL", "export.xlsx") is PosResult.Success)
        val importSummary = repository.importAndMergeTransactions(
            ParseXlsxResult(
                transactions = listOf(
                    ParsedTransaction(
                        invoice = inv.copy(orderId = "INV-EXT-1", globalTransactionId = "GLB-EXT-1"),
                        lines = listOf(InvoiceLineEntity(orderId = "INV-EXT-1", itemId = "item-1", itemName = "Octane", itemType = "Liquid", unit = "L", quantityStr = "1", unitPricePaisa = 10000L, lineTotalPaisa = 10000L))
                    )
                ),
                totalRowsParsed = 1,
                invalidRows = 0,
                validationErrors = emptyList()
            )
        )
        assertEquals(1, importSummary.importedCount)

        // 4. Logout -> failed login attempts -> lockout -> failed password reset -> successful password reset -> login
        repository.logout()
        repeat(5) {
            val failedLogin = repository.login(phone, badPass, rememberMe = false)
            assertTrue(failedLogin.isFailure)
        }
        // Locked attempt
        val lockedLogin = repository.login(phone, secretPass, rememberMe = false)
        assertTrue(lockedLogin.isFailure)

        // Failed password reset with wrong recovery code
        assertTrue(repository.resetPassword(phone, "WRONG-CODE-9999", newPass).isFailure)
        // Successful password reset with regenerated recovery code
        assertTrue(repository.resetPassword(phone, regenCode, newPass).isSuccess)
        // Login succeeds after reset
        assertTrue(repository.login(phone, newPass, rememberMe = true).isSuccess)

        // 5. Inspect ALL audit logs
        val allLogs = db.auditLogDao().getAllAuditLogsDirect()
        val actionSet = allLogs.map { it.actionType }.toSet()

        assertTrue(actionSet.contains(AuditActionType.AUTH_REGISTER))
        assertTrue(actionSet.contains(AuditActionType.AUTH_PROFILE_UPDATED))
        assertTrue(actionSet.contains(AuditActionType.AUTH_RECOVERY_CODE_REGENERATED))
        assertTrue(actionSet.contains(AuditActionType.AUTH_LOGOUT))
        assertTrue(actionSet.contains(AuditActionType.AUTH_LOGIN_FAILED))
        assertTrue(actionSet.contains(AuditActionType.AUTH_LOGIN_LOCKED))
        assertTrue(actionSet.contains(AuditActionType.AUTH_PASSWORD_RESET))
        assertTrue(actionSet.contains(AuditActionType.AUTH_LOGIN_SUCCESS))
        assertTrue(actionSet.contains(AuditActionType.COMPANY_HEADER_UPDATED))
        assertTrue(actionSet.contains(AuditActionType.COMPANY_LOGO_UPDATED))
        assertTrue(actionSet.contains(AuditActionType.COMPANY_LOGO_REMOVED))
        assertTrue(actionSet.contains(AuditActionType.FOOTER_NOTES_UPDATED))
        assertTrue(actionSet.contains(AuditActionType.PRINTER_SETTINGS_UPDATED))
        assertTrue(actionSet.contains(AuditActionType.APP_PREFERENCES_UPDATED))
        assertTrue(actionSet.contains(AuditActionType.INVOICE_CREATED))
        assertTrue(actionSet.contains(AuditActionType.PAYMENT_COLLECTED_CHECKOUT))
        assertTrue(actionSet.contains(AuditActionType.XLSX_EXPORTED))
        assertTrue(actionSet.contains(AuditActionType.XLSX_IMPORTED))

        // Verify both success & failure AUTH_PASSWORD_RESET events exist
        assertTrue(allLogs.any { it.actionType == AuditActionType.AUTH_PASSWORD_RESET && !it.isSuccess })
        assertTrue(allLogs.any { it.actionType == AuditActionType.AUTH_PASSWORD_RESET && it.isSuccess })

        // Verify ZERO leaked secrets or hashes across EVERY audit field
        val forbiddenTokens = listOf(
            secretPass,
            badPass,
            newPass,
            initialRecoveryCode,
            regenCode,
            "WRONG-CODE-9999",
            "v1$",
            "v2$",
            regRes.operator.passwordHash,
            "data:image"
        )
        for (entry in allLogs) {
            val combined = listOfNotNull(
                entry.entityId,
                entry.summaryEn,
                entry.summaryBn,
                entry.beforeData,
                entry.afterData,
                entry.metadataJson
            ).joinToString(" | ")
            for (token in forbiddenTokens) {
                assertFalse("Audit log ${entry.actionType} must NEVER leak secret token '$token'", combined.contains(token))
            }
        }

        // Verify AuditSanitizer blocks sensitive keys and produces masked phones
        assertEquals("017****3456", AuditSanitizer.maskPhone(phone))
        assertTrue(AuditSanitizer.isSensitiveKey("passwordHash"))
        assertTrue(AuditSanitizer.isSensitiveKey("recoveryCode"))
        assertTrue(AuditSanitizer.isSensitiveKey("sessionToken"))
        assertEquals("[REDACTED]", AuditSanitizer.sanitizeValue("password", "abc"))
    }

    @Test
    fun testUnauthenticatedMutationsAreBlockedAcrossAllRepositories() = runBlocking {
        // Ensure zero active operator
        repository.logout()

        assertTrue((repository.company.updateCompanyHeader("X", "B", "1", "1", "A") as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.saveProductAudited(ProductEntity("p1", "N", itemType = "Product", unit = "Pcs", defaultUnitPricePaisa = 100L)) as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.deleteProductAudited("p1") as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.updateProductReorderAudited(listOf("p1")) as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.customersRepo.saveCustomer(CustomerEntity("c1")) as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.updateLanguageAudited("en") as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.updateThemeModeAudited("DARK") as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.updateFooterNotesAudited("1", "2") as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.updatePaperSizeAudited("80mm") as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.updateAutoCutterAudited(false) as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.updateBluetoothPrinterAudited("00:11", "P") as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.settleInvoiceDue("INV-1", 50L) as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.updateInvoiceCustomerAndNoteAudited("INV-1", "C", "N") as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.deleteOrderAudited("INV-1") as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue((repository.payments.recordPayment("INV-1", "CASH", 100L, null) as PosResult.Error).error is PosError.Unauthenticated)
        assertTrue(
            (repository.checkoutOrderAtomic(
                InvoiceEntity("INV-U", "GLB-U", "01", 1000L, "OP", "Op", "017", 1000L, 10L),
                emptyList()
            ) as PosResult.Error).error is PosError.Unauthenticated
        )
        assertTrue((repository.recordXlsxExportAudit("FULL", 0) as PosResult.Error).error is PosError.Unauthenticated)
    }
}
