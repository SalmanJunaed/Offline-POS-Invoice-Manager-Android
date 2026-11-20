package com.example.pos

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.os.Looper
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.core.security.InMemoryAesGcmSessionCipher
import com.example.pos.core.security.PasswordSecurity
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.core.security.SessionCipher
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.AuthRepositoryImpl
import com.example.pos.data.repository.PosRepository
import com.example.pos.ui.viewmodel.PosViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthFoundationStep07BTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var context: Context
    private lateinit var db: PosDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cipher: InMemoryAesGcmSessionCipher
    private var currentTimeMs = 1_750_000_000_000L

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext<Application>()
        context = app
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreFile = tempFolder.newFile("step07b_session.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        cipher = InMemoryAesGcmSessionCipher()
        currentTimeMs = 1_750_000_000_000L
    }

    @After
    fun teardown() {
        db.close()
        dataStoreScope.cancel()
    }

    private fun createSessionManager(
        ds: DataStore<Preferences> = dataStore,
        sessionCipher: SessionCipher = cipher
    ): SecureSessionManager {
        return SecureSessionManager.createForTest(
            dataStore = ds,
            cipher = sessionCipher,
            timeProvider = { currentTimeMs }
        )
    }

    private fun createAuthRepo(
        sessionManager: SecureSessionManager = createSessionManager(),
        maxFailedAttempts: Int = 5,
        lockoutDurationMs: Long = 5 * 60 * 1000L
    ): AuthRepositoryImpl {
        return AuthRepositoryImpl(
            operatorDao = db.operatorDao(),
            sessionManager = sessionManager,
            db = null,
            maxFailedAttempts = maxFailedAttempts,
            lockoutDurationMs = lockoutDurationMs,
            timeProvider = { currentTimeMs }
        )
    }

    // ------------------------------------------------------------------------
    // 1. PasswordSecurity tests
    // ------------------------------------------------------------------------

    @Test
    fun testPasswordSecurityV2SaltedPbkdf2AndRejectionOfWeakFormats() {
        val rawPin = "4829"
        val hash1 = PasswordSecurity.hashPassword(rawPin)
        val hash2 = PasswordSecurity.hashPassword(rawPin)

        assertTrue("Must use v2 salted prefix", hash1.startsWith("pbkdf2_sha256\$v2\$120000\$"))
        assertNotEquals("Each hash must have unique salt and output", hash1, hash2)
        assertFalse("v2 hash should not need rehash", PasswordSecurity.needsRehash(hash1))
        assertTrue(PasswordSecurity.verifyPassword(rawPin, hash1))
        assertTrue(PasswordSecurity.verifyPassword(rawPin, hash2))
        assertFalse(PasswordSecurity.verifyPassword("9999", hash1))

        // Reject raw plaintext stored directly as hash
        assertFalse("Plaintext fallback must be rejected", PasswordSecurity.verifyPassword("1234", "1234"))

        // Reject unsalted SHA-256 hex digest
        val unsaltedSha256 = MessageDigest.getInstance("SHA-256")
            .digest(rawPin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertFalse("Unsalted SHA-256 must be rejected", PasswordSecurity.verifyPassword(rawPin, unsaltedSha256))
    }

    @Test
    fun testLegacyV1HashVerifiesAndUpgradesToV2OnLogin() = runBlocking {
        // Construct an authentic legacy v1 hash (10,000 iterations: pbkdf2_sha256$<salt>$<hash>)
        val password = "legacyPin88"
        val salt = ByteArray(16) { (it + 1).toByte() }
        val spec = PBEKeySpec(password.toCharArray(), salt, 10_000, 256)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val legacyHash = "pbkdf2_sha256\$${Base64.encodeToString(salt, Base64.NO_WRAP)}\$${Base64.encodeToString(derived, Base64.NO_WRAP)}"

        assertTrue(PasswordSecurity.verifyPassword(password, legacyHash))
        assertTrue("Legacy v1 hash must be flagged for rehash upgrade", PasswordSecurity.needsRehash(legacyHash))

        // Insert operator with legacy hash
        val phone = "01712345670"
        db.operatorDao().insertOperator(
            OperatorEntity(
                userId = "OP-LEGACY",
                operatorName = "Legacy Operator",
                operatorPhone = phone,
                operatorAddress = "Naogaon",
                passwordHash = legacyHash,
                isActive = false,
                rememberMe = false
            )
        )

        val repo = createAuthRepo()
        val loginRes = repo.login(phone, password, rememberMe = true)
        assertTrue("Login with legacy hash must succeed", loginRes is PosResult.Success)

        val upgradedOp = db.operatorDao().getOperatorByPhone(phone)!!
        assertTrue(
            "Password hash in DB must be transparently upgraded to v2 on login",
            upgradedOp.passwordHash.startsWith("pbkdf2_sha256\$v2\$120000\$")
        )
        assertFalse(PasswordSecurity.needsRehash(upgradedOp.passwordHash))
    }

    @Test
    fun testRecoveryCodeGenerationNormalizationAndVerification() {
        val code = PasswordSecurity.generateRecoveryCode()
        assertEquals(14, code.length) // XXXX-XXXX-XXXX
        assertEquals(3, code.split("-").size)

        val hash = PasswordSecurity.hashRecoveryCode(code)
        assertTrue(PasswordSecurity.verifyRecoveryCode(code, hash))
        assertTrue(PasswordSecurity.verifyRecoveryCode(code.lowercase().replace("-", " "), hash))
        assertFalse(PasswordSecurity.verifyRecoveryCode("AAAA-BBBB-CCCC", hash))
    }

    // ------------------------------------------------------------------------
    // 2. Database Migration, Empty Prepopulation & DB Constraints
    // ------------------------------------------------------------------------

    @Test
    fun testFreshDatabasePrepopulationCreatesZeroOperators(): Unit = runBlocking {
        val dbName = "fresh_prepopulate_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()

        val freshDb = PosDatabase.createFreshForTest(context, dbName)
        try {
            freshDb.awaitInitialized()

            assertEquals(
                "Fresh installation must contain 0 hardcoded/default operators",
                0,
                freshDb.operatorDao().getOperatorCount()
            )
            assertNull("Fresh install must have no active operator", freshDb.operatorDao().getActiveOperator())
            // Business header, products, and settings remain prepopulated cleanly
            assertNotNull(freshDb.companyHeaderDao().getCompanyHeader())
            assertTrue(freshDb.productDao().getProductCount() >= 8)
            assertNotNull(freshDb.appSettingsDao().getSettings())
        } finally {
            freshDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun testMigration7To8PreservesExistingDataAndAddsSecurityColumns(): Unit = runBlocking {
        val dbName = "migration_7_8_test.db"
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()

        // Create a V7 database schema directly
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS company_header (id INTEGER PRIMARY KEY NOT NULL, company_name TEXT NOT NULL, branch_name TEXT NOT NULL, branch_number TEXT NOT NULL, address TEXT NOT NULL, phone_number TEXT NOT NULL, logo_uri TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_company_header_branch_number ON company_header(branch_number)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS operators (user_id TEXT PRIMARY KEY NOT NULL, operator_name TEXT NOT NULL, operator_phone TEXT NOT NULL, operator_address TEXT NOT NULL, password_hash TEXT NOT NULL, is_active INTEGER NOT NULL, remember_me INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_operators_operator_phone ON operators(operator_phone)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_operators_is_active ON operators(is_active)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS products (item_id TEXT PRIMARY KEY NOT NULL, item_name TEXT NOT NULL, name_bn TEXT NOT NULL DEFAULT '', name_en TEXT NOT NULL DEFAULT '', item_type TEXT NOT NULL, unit TEXT NOT NULL, default_unit_price_paisa INTEGER NOT NULL, sort_order INTEGER NOT NULL, is_active INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_products_item_name ON products(item_name)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_products_name_bn ON products(name_bn)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_products_name_en ON products(name_en)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_products_item_type ON products(item_type)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_products_sort_order ON products(sort_order)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_products_is_active ON products(is_active)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS invoices (order_id TEXT PRIMARY KEY NOT NULL, global_transaction_id TEXT NOT NULL, branch_code TEXT NOT NULL, date_time INTEGER NOT NULL, operator_id TEXT NOT NULL, operator_name TEXT NOT NULL, customer_info TEXT NOT NULL, raw_total_paisa INTEGER NOT NULL, rounded_total_bdt INTEGER NOT NULL, is_paid INTEGER NOT NULL DEFAULT 1, paid_amount_bdt INTEGER NOT NULL DEFAULT 0, footer_note TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_date_time ON invoices(date_time)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoices_global_transaction_id ON invoices(global_transaction_id)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_operator_id ON invoices(operator_id)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_customer_info ON invoices(customer_info)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_rounded_total_bdt ON invoices(rounded_total_bdt)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_raw_total_paisa ON invoices(raw_total_paisa)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_is_paid ON invoices(is_paid)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS invoice_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, order_id TEXT NOT NULL, item_id TEXT NOT NULL, item_name TEXT NOT NULL, item_type TEXT NOT NULL, unit TEXT NOT NULL, quantity_str TEXT NOT NULL, unit_price_paisa INTEGER NOT NULL, line_total_paisa INTEGER NOT NULL, sort_order INTEGER NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(order_id) REFERENCES invoices(order_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_order_id ON invoice_lines(order_id)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_item_id ON invoice_lines(item_id)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (id INTEGER PRIMARY KEY NOT NULL, language TEXT NOT NULL, theme_mode TEXT NOT NULL, paper_size TEXT NOT NULL, auto_cutter_enabled INTEGER NOT NULL, bluetooth_printer_address TEXT, bluetooth_printer_name TEXT, footer_note_line1 TEXT NOT NULL, footer_note_line2 TEXT NOT NULL, next_order_sequence INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS payment_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, order_id TEXT NOT NULL, payment_method TEXT NOT NULL, amount_paid_paisa INTEGER NOT NULL, transaction_ref TEXT, timestamp INTEGER NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(order_id) REFERENCES invoices(order_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_records_order_id ON payment_records(order_id)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_records_payment_method ON payment_records(payment_method)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_records_timestamp ON payment_records(timestamp)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS customers (customer_info TEXT PRIMARY KEY NOT NULL, customer_name TEXT NOT NULL, phone_number TEXT NOT NULL, address TEXT NOT NULL, notes TEXT NOT NULL, total_orders INTEGER NOT NULL, total_spent_paisa INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customers_customer_info ON customers(customer_info)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_customer_name ON customers(customer_name)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_phone_number ON customers(phone_number)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_updated_at ON customers(updated_at)")

                        db.execSQL("INSERT INTO operators VALUES ('OP-V7-1', 'Karim Uddin', '01755667788', 'Dhaka', 'hash_val', 0, 0, 1000, 1000)")
                        db.execSQL("INSERT INTO invoices VALUES ('INV-V7-KEEP', 'gid-1', '01', 2000, 'OP-V7-1', 'Karim Uddin', '01811', 50000, 500, 1, 500, '', 2000, 2000)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        helper.writableDatabase.close()

        val migrated = Room.databaseBuilder(context, PosDatabase::class.java, dbName)
            .addMigrations(PosDatabase.MIGRATION_7_8, PosDatabase.MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()

        val op = migrated.operatorDao().getOperatorById("OP-V7-1")
        assertNotNull("Existing operator must survive V7 -> V8 migration", op)
        assertEquals("", op!!.recoveryCodeHash)
        assertEquals(0, op.failedAttempts)
        assertEquals(0L, op.lockedUntilMillis)

        val inv = migrated.invoiceDao().getInvoiceById("INV-V7-KEEP")
        assertNotNull("Existing invoice must survive V7 -> V8 migration", inv)
        assertEquals(500L, inv!!.roundedTotalBdt)

        migrated.close()
        dbFile.delete()
    }

    @Test(expected = SQLiteConstraintException::class)
    fun testOperatorDaoEnforcesUniquePhoneAtSqliteLevel() = runBlocking {
        val op1 = OperatorEntity("OP-1", "User 1", "01711000001", "A", "h1")
        val op2 = OperatorEntity("OP-2", "User 2", "01711000001", "B", "h2")
        db.operatorDao().insertOperator(op1)
        // Must throw SQLiteConstraintException (OnConflictStrategy.ABORT, never REPLACE!)
        db.operatorDao().insertOperator(op2)
    }

    // ------------------------------------------------------------------------
    // 3. Encrypted DataStore Session Storage Tests
    // ------------------------------------------------------------------------

    @Test
    fun testSessionPayloadIsEncryptedAndFailsClosedOnTamperingOrCipherFailure() = runBlocking {
        val manager = createSessionManager()
        val saveRes = manager.saveLoginSession("OP-SECRET-99", "01799001122", rememberMe = true)
        assertTrue(saveRes.isSuccess)

        val rawPrefs = dataStore.data.first()
        val rawStored = rawPrefs[stringPreferencesKey("encrypted_session_payload")]
        assertNotNull(rawStored)
        assertFalse("Ciphertext must not expose userId in plaintext", rawStored!!.contains("OP-SECRET-99"))
        assertFalse("Ciphertext must not expose phone in plaintext", rawStored.contains("01799001122"))

        // Tamper one character in stored ciphertext -> fail-closed
        val lastChar = if (rawStored.last() == 'A') 'B' else 'A'
        val tampered = rawStored.dropLast(1) + lastChar
        dataStore.edit { it[stringPreferencesKey("encrypted_session_payload")] = tampered }
        assertNull("Tampered GCM ciphertext must fail authentication and return null", manager.readValidSession())

        // Re-save valid session, then simulate Keystore/cipher decryption failure -> fail-closed
        assertTrue(manager.saveLoginSession("OP-SECRET-99", "01799001122", rememberMe = true).isSuccess)
        val failingCipher = object : SessionCipher {
            override fun encrypt(plainText: String) = Result.failure<String>(IllegalStateException("Keystore locked"))
            override fun decrypt(encodedCipherText: String): String? = null
        }
        val failingManager = createSessionManager(sessionCipher = failingCipher)
        assertNull("Keystore/cipher failure must fail closed and return null", failingManager.readValidSession())
        assertTrue("Keystore/cipher write failure must return Result.failure", failingManager.saveLoginSession("OP-1", "01711111111", true).isFailure)
    }

    @Test
    fun testRememberMeFalseDoesNotRestoreAcrossRestartWhileRememberMeTrueDoes() = runBlocking {
        val phone = "01744556677"
        val pass = "pin5678"
        val sm = createSessionManager()
        val repo1 = createAuthRepo(sm)

        val regRes = repo1.register("Session Tester", phone, "Bogra", pass) as PosResult.Success
        val opId = regRes.data.operator.userId

        // Logout & login with rememberMe = false
        repo1.logout()
        val ephemeralLogin = repo1.login(phone, pass, rememberMe = false)
        assertTrue(ephemeralLogin is PosResult.Success)
        assertTrue(db.operatorDao().getOperatorById(opId)!!.isActive)

        // Simulate process restart with new repository & SecureSessionManager instance reading same DataStore
        val repo2 = createAuthRepo(createSessionManager())
        val restored = repo2.checkAndRestoreSession()
        assertNull("Session with rememberMe=false must NOT restore across restart", restored)
        assertNull("DB active operator must be cleared when restore is rejected", db.operatorDao().getActiveOperator())

        // Login with rememberMe = true and simulate restart
        assertTrue(repo2.login(phone, pass, rememberMe = true) is PosResult.Success)
        val repo3 = createAuthRepo(createSessionManager())
        val restoredRemembered = repo3.checkAndRestoreSession()
        assertNotNull("Session with rememberMe=true must restore across restart", restoredRemembered)
        assertEquals(opId, restoredRemembered!!.userId)

        // Advance time past 30-day TTL -> expired session rejected
        currentTimeMs += SecureSessionManager.REMEMBERED_SESSION_TTL_MS + 1000L
        assertNull("Expired session must not restore", repo3.checkAndRestoreSession())
        assertNull("DB active operator must be cleared on expiry", db.operatorDao().getActiveOperator())
    }

    // ------------------------------------------------------------------------
    // 4. Repository Validation, Lockout & Recovery Password Reset Tests
    // ------------------------------------------------------------------------

    @Test
    fun testRegistrationValidationAndNormalizedDuplicateRejection() = runBlocking {
        val repo = createAuthRepo()

        // Invalid inputs
        assertTrue(repo.register("A", "01711223344", "", "1234") is PosResult.Error)
        assertTrue(repo.register("Valid Name", "123", "", "1234") is PosResult.Error)
        assertTrue(repo.register("Valid Name", "01711223344", "", "12") is PosResult.Error)

        // Valid registration normalizes phone
        val res1 = repo.register("Salman", "01711-223 344", "Main", "5566")
        assertTrue(res1 is PosResult.Success)
        assertEquals("01711223344", (res1 as PosResult.Success).data.operator.operatorPhone)

        // Duplicate formatted phone rejected
        val res2 = repo.register("Other", " 01711223344 ", "Branch", "7788")
        assertTrue(res2 is PosResult.Error)
        assertTrue((res2 as PosResult.Error).error is PosError.UserAlreadyExists)
    }

    @Test
    fun testBruteForceLockoutPersistsAcrossRestartAndUnlocksOnRecoveryOrExpiry() = runBlocking {
        val phone = "01766112233"
        val pass = "correct123"
        val repo1 = createAuthRepo(maxFailedAttempts = 5, lockoutDurationMs = 300_000L)
        val reg = (repo1.register("Lockout User", phone, "Dhaka", pass) as PosResult.Success).data
        repo1.logout()

        // 4 failed attempts -> InvalidCredentials
        repeat(4) {
            val res = repo1.login(phone, "wrong", false)
            assertTrue(res is PosResult.Error && res.error is PosError.InvalidCredentials)
        }
        // 5th failed attempt -> AccountLocked
        val lockedRes = repo1.login(phone, "wrong", false)
        assertTrue(lockedRes is PosResult.Error && lockedRes.error is PosError.AccountLocked)

        // Restart with brand new repo instance -> even CORRECT password must stay locked!
        val repoRestarted = createAuthRepo(createSessionManager(), maxFailedAttempts = 5, lockoutDurationMs = 300_000L)
        val correctWhileLocked = repoRestarted.login(phone, pass, true)
        assertTrue(
            "Lockout persisted in Room must block even correct password across restart",
            correctWhileLocked is PosResult.Error && correctWhileLocked.error is PosError.AccountLocked
        )

        // Valid recovery code resets password AND clears lockout
        val resetRes = repoRestarted.resetPassword(phone, reg.recoveryCode, "newPass999")
        assertTrue("Reset with valid recovery code must succeed", resetRes is PosResult.Success)

        val loginAfterReset = repoRestarted.login(phone, "newPass999", rememberMe = true)
        assertTrue("Operator can login immediately after recovery reset", loginAfterReset is PosResult.Success)
    }

    @Test
    fun testCheckAndRestoreFailsClosedWhenDataStoreEmptyDespiteActiveDbFlag() = runBlocking {
        // Simulate attacker or corrupted state setting is_active=1 in Room without encrypted DataStore session
        db.operatorDao().insertOperator(
            OperatorEntity(
                userId = "OP-TAMPERED",
                operatorName = "Fake Active",
                operatorPhone = "01799990000",
                operatorAddress = "",
                passwordHash = PasswordSecurity.hashPassword("1234"),
                isActive = true,
                rememberMe = true
            )
        )

        val repo = createAuthRepo()
        val restored = repo.checkAndRestoreSession()
        assertNull("Without valid encrypted DataStore session, DB is_active must NOT restore session", restored)
        assertNull("Repository must deactivate dangling DB flag", db.operatorDao().getActiveOperator())
    }

    // ------------------------------------------------------------------------
    // 5. PosViewModel Checkout Guard, Operator Stamping & Logout Wipe
    // ------------------------------------------------------------------------

    @Test
    fun testViewModelRejectsUnauthenticatedCheckoutStampsOperatorAndWipesOnLogout(): Unit = runBlocking {
        val repo = PosRepository(context, db, createSessionManager())
        val vm = PosViewModel.createForTest(app, repo)

        fun pumpMainLooper() {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }

        // Wait for initial session check to finish (0 operators -> activeOperator == null)
        var initWait = 0
        pumpMainLooper()
        while (vm.isSessionChecking.value && initWait < 40) {
            delay(20)
            pumpMainLooper()
            initWait++
        }
        assertFalse(vm.isSessionChecking.value)
        assertNull(vm.activeOperator.value)

        val product = ProductEntity(
            itemId = "item-auth-test",
            itemName = "Octane",
            nameBn = "অকটেন",
            nameEn = "Octane",
            itemType = "Liquid",
            unit = "Liter",
            defaultUnitPricePaisa = 13000L
        )
        vm.addItemFromCatalog(product)
        vm.setCustomerInfo("01711112222")
        vm.setDraftPaidAmount("50")

        val orderIdBeforeLogin = vm.draftOrderId.value
        vm.checkoutPaid(printDirectly = false)
        pumpMainLooper()
        delay(100)
        pumpMainLooper()

        assertNull(
            "Unauthenticated checkout must NOT persist an invoice in DB",
            db.invoiceDao().getInvoiceById(orderIdBeforeLogin)
        )
        assertFalse(
            "Unauthenticated checkout must NOT open receipt preview",
            vm.receiptPreviewOpen.value
        )
        assertNotNull("Rejection must set helpful toast message", vm.toastMessage.value)

        // Now register via ViewModel -> verify recovery code emitted and operator becomes active
        var regSuccess = false
        var recoveryCode: String? = null
        vm.registerWithRecovery("Operator Rafiq", "01733445566", "Mohadevpur", "9876") { ok, code, _ ->
            regSuccess = ok
            recoveryCode = code
        }
        var regWait = 0
        pumpMainLooper()
        while ((!regSuccess || vm.activeOperator.value == null) && regWait < 60) {
            delay(25)
            pumpMainLooper()
            regWait++
        }
        assertTrue(regSuccess)
        assertNotNull(recoveryCode)
        val loggedInOp = vm.activeOperator.value!!
        assertEquals("Operator Rafiq", loggedInOp.operatorName)

        // Legacy 3-arg resetPassword without recovery code must be rejected
        var legacyResetOk = true
        var legacyResetErr: String? = null
        vm.resetPassword("01733445566", "hacked123") { ok, err ->
            legacyResetOk = ok
            legacyResetErr = err
        }
        assertFalse("Unauthenticated resetPassword overload must be blocked", legacyResetOk)
        assertNotNull(legacyResetErr)

        // Checkout authenticated -> stamps REAL operator userId and operatorName (NOT OP-101)
        val nextId = vm.draftOrderId.value
        vm.checkoutPaid(printDirectly = false)
        var saveWait = 0
        pumpMainLooper()
        while ((db.invoiceDao().getInvoiceById(nextId) == null || !vm.receiptPreviewOpen.value) && saveWait < 60) {
            delay(25)
            pumpMainLooper()
            saveWait++
        }
        val savedInvoice = db.invoiceDao().getInvoiceById(nextId)
        assertNotNull("Authenticated checkout must save invoice", savedInvoice)
        assertEquals(loggedInOp.userId, savedInvoice!!.operatorId)
        assertEquals("Operator Rafiq", savedInvoice.operatorName)
        assertNotEquals("OP-101", savedInvoice.operatorId)

        // Populate sensitive draft & preview state, then invoke logout()
        vm.addItemFromCatalog(product)
        vm.setCustomerInfo("01888777666")
        vm.setDraftPaidAmount("100")
        pumpMainLooper()
        assertTrue(vm.receiptPreviewOpen.value)
        assertTrue(vm.draftItems.value.isNotEmpty())

        var logoutDone = false
        vm.logout { logoutDone = true }
        var logoutWait = 0
        pumpMainLooper()
        while ((!logoutDone || vm.activeOperator.value != null) && logoutWait < 60) {
            delay(25)
            pumpMainLooper()
            logoutWait++
        }

        assertTrue(logoutDone)
        assertNull("activeOperator must be null after logout", vm.activeOperator.value)
        assertTrue("draftItems must be wiped on logout", vm.draftItems.value.isEmpty())
        assertEquals("customerInfo must be wiped on logout", "", vm.customerInfo.value)
        assertEquals("draftPaidAmountBdt must be wiped on logout", "", vm.draftPaidAmountBdt.value)
        assertFalse("receiptPreviewOpen must be closed on logout", vm.receiptPreviewOpen.value)
        assertNull("selectedInvoiceForReceipt must be cleared on logout", vm.selectedInvoiceForReceipt.value)
        assertEquals("receiptPreviewText must be wiped on logout", "", vm.receiptPreviewText.value)
        assertNull("Session cannot auto-restore after logout", repo.checkAndRestoreSession())
    }
}
