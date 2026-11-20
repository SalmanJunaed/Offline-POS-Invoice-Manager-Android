package com.example.pos

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.PosDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditTrailMigration8To9Test {

    @Test
    fun testMigration8To9PreservesExistingRecordsWithNullAttributionAndZeroFakeBackfill(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_8_to_9_test.db"
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS company_header (id INTEGER PRIMARY KEY NOT NULL, company_name TEXT NOT NULL, branch_name TEXT NOT NULL, branch_number TEXT NOT NULL, address TEXT NOT NULL, phone_number TEXT NOT NULL, logo_uri TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_company_header_branch_number ON company_header(branch_number)")

                        db.execSQL("CREATE TABLE IF NOT EXISTS operators (user_id TEXT PRIMARY KEY NOT NULL, operator_name TEXT NOT NULL, operator_phone TEXT NOT NULL, operator_address TEXT NOT NULL, password_hash TEXT NOT NULL, is_active INTEGER NOT NULL, remember_me INTEGER NOT NULL, recovery_code_hash TEXT NOT NULL DEFAULT '', failed_attempts INTEGER NOT NULL DEFAULT 0, locked_until_millis INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
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

                        // Seed legacy v8 rows
                        db.execSQL("INSERT INTO company_header VALUES (1, 'Legacy Station', 'Main', '01', 'Dhaka', '01700000000', NULL, 1000, 1000)")
                        db.execSQL("INSERT INTO operators VALUES ('OP-LEGACY', 'Legacy Op', '01711110000', 'Bogura', 'hash', 1, 0, 'rec_hash', 0, 0, 1000, 1000)")
                        db.execSQL("INSERT INTO products VALUES ('PROD-LEGACY', 'Octane', 'অকটেন', 'Octane', 'Liquid', 'Liter', 13000, 1, 1, 1000, 1000)")
                        db.execSQL("INSERT INTO invoices VALUES ('INV-V8-1', 'gid-v8-1', '01', 2000, 'OP-LEGACY', 'Legacy Op', '01811112222', 65000, 650, 1, 650, 'Note', 2000, 2000)")
                        db.execSQL("INSERT INTO invoice_lines VALUES (1, 'INV-V8-1', 'PROD-LEGACY', 'Octane', 'Liquid', 'Liter', '5', 13000, 65000, 1, 2000)")
                        db.execSQL("INSERT INTO app_settings VALUES (1, 'bn', 'LIGHT', '58mm', 1, NULL, NULL, 'Line 1', 'Line 2', 10295, 1000, 1000)")
                        db.execSQL("INSERT INTO payment_records VALUES (1, 'INV-V8-1', 'CASH', 65000, NULL, 2000, 2000)")
                        db.execSQL("INSERT INTO customers VALUES ('01811112222', 'Karim', '01811112222', 'Dhaka', '', 1, 65000, 2000, 2000)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        helper.writableDatabase.close()

        val migrated = Room.databaseBuilder(context, PosDatabase::class.java, dbName)
            .addMigrations(PosDatabase.MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()

        // Verify schema integrity + preserved data + NULL attribution (zero fake backfill)
        val header = migrated.companyHeaderDao().getCompanyHeader()
        assertNotNull(header)
        assertEquals("Legacy Station", header!!.companyName)
        assertNull(header.updatedByOperatorId)
        assertNull(header.updatedByOperatorName)

        val product = migrated.productDao().getProductById("PROD-LEGACY")
        assertNotNull(product)
        assertNull(product!!.createdByOperatorId)
        assertNull(product.createdByOperatorName)
        assertNull(product.updatedByOperatorId)
        assertNull(product.updatedByOperatorName)

        val invoice = migrated.invoiceDao().getInvoiceById("INV-V8-1")
        assertNotNull(invoice)
        assertEquals(650L, invoice!!.roundedTotalBdt)
        assertNull(invoice.updatedByOperatorId)
        assertNull(invoice.updatedByOperatorName)

        val payments = migrated.paymentDao().getPaymentsForOrderDirect("INV-V8-1")
        assertEquals(1, payments.size)
        assertNull(payments[0].collectedByOperatorId)
        assertNull(payments[0].collectedByOperatorName)

        val customer = migrated.customerDao().getCustomerByInfo("01811112222")
        assertNotNull(customer)
        assertNull(customer!!.createdByOperatorId)
        assertNull(customer.updatedByOperatorId)

        val settings = migrated.appSettingsDao().getSettings()
        assertNotNull(settings)
        assertNull(settings!!.updatedByOperatorId)
        assertNull(settings.updatedByOperatorName)

        // Verify audit_logs starts empty and supports v9 writes
        assertEquals(0, migrated.auditLogDao().getTotalAuditLogCountFlow().first())
        migrated.auditLogDao().insertAuditLog(
            AuditLogEntity(
                eventId = "AUD-POST-MIG-1",
                timestamp = 5000L,
                operatorId = "OP-LEGACY",
                operatorName = "Legacy Op",
                actionType = AuditActionType.INVOICE_CREATED,
                entityType = AuditEntityType.INVOICE,
                entityId = "INV-V9-1",
                summaryEn = "Created INV-V9-1",
                summaryBn = "INV-V9-1 তৈরি",
                isSuccess = true
            )
        )
        val logs = migrated.auditLogDao().getAllAuditLogsDirect()
        assertEquals(1, logs.size)
        assertTrue(logs[0].isSuccess)

        migrated.close()
        dbFile.delete()
    }
}
