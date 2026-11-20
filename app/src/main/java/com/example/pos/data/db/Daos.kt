package com.example.pos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyHeaderDao {
    @Query("SELECT * FROM company_header WHERE id = 1 LIMIT 1")
    fun getCompanyHeaderFlow(): Flow<CompanyHeaderEntity?>

    @Query("SELECT * FROM company_header WHERE id = 1 LIMIT 1")
    suspend fun getCompanyHeader(): CompanyHeaderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(header: CompanyHeaderEntity)
}

@Dao
interface OperatorDao {
    @Query("SELECT * FROM operators WHERE is_active = 1 LIMIT 1")
    fun getActiveOperatorFlow(): Flow<OperatorEntity?>

    @Query("SELECT * FROM operators WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveOperator(): OperatorEntity?

    @Query("SELECT * FROM operators WHERE operator_phone = :phone LIMIT 1")
    suspend fun getOperatorByPhone(phone: String): OperatorEntity?

    @Query("SELECT * FROM operators WHERE user_id = :userId LIMIT 1")
    suspend fun getOperatorById(userId: String): OperatorEntity?

    @Query("SELECT * FROM operators ORDER BY created_at DESC")
    fun getAllOperators(): Flow<List<OperatorEntity>>

    @Query("SELECT * FROM operators ORDER BY created_at DESC")
    suspend fun getAllOperatorsDirect(): List<OperatorEntity>

    @Query("SELECT COUNT(*) FROM operators")
    suspend fun getOperatorCount(): Int

    @Query("SELECT COUNT(*) FROM operators")
    fun getOperatorCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperator(operator: OperatorEntity)

    @Update
    suspend fun updateOperator(operator: OperatorEntity)

    @Query("UPDATE operators SET is_active = 0")
    suspend fun clearAllActiveOperators()

    @Query("UPDATE operators SET is_active = 1, remember_me = :rememberMe, updated_at = :now WHERE user_id = :userId")
    suspend fun setActiveOperator(userId: String, rememberMe: Boolean, now: Long = System.currentTimeMillis()): Int

    @Transaction
    suspend fun switchActiveOperator(
        userId: String,
        rememberMe: Boolean,
        now: Long = System.currentTimeMillis()
    ): OperatorEntity? {
        getOperatorById(userId) ?: return null
        clearAllActiveOperators()
        setActiveOperator(userId, rememberMe, now)
        return getOperatorById(userId)
    }

    @Transaction
    suspend fun insertAndActivateOperator(operator: OperatorEntity): OperatorEntity {
        clearAllActiveOperators()
        insertOperator(operator)
        return getOperatorById(operator.userId) ?: operator
    }

    @Query("UPDATE operators SET password_hash = :passwordHash, failed_attempts = 0, locked_until_millis = 0, updated_at = :now WHERE user_id = :userId")
    suspend fun updatePasswordHash(
        userId: String,
        passwordHash: String,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE operators SET recovery_code_hash = :recoveryCodeHash, updated_at = :now WHERE user_id = :userId")
    suspend fun updateRecoveryCodeHash(
        userId: String,
        recoveryCodeHash: String,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE operators SET failed_attempts = :failedAttempts, locked_until_millis = :lockedUntilMillis, updated_at = :now WHERE user_id = :userId")
    suspend fun updateLoginAttempts(
        userId: String,
        failedAttempts: Int,
        lockedUntilMillis: Long,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE operators SET operator_name = :name, operator_phone = :phone, operator_address = :address, updated_at = :now WHERE user_id = :userId")
    suspend fun updateOperatorProfile(
        userId: String,
        name: String,
        phone: String,
        address: String,
        now: Long = System.currentTimeMillis()
    ): Int
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE is_active = 1 ORDER BY sort_order ASC, item_name ASC")
    fun getAllActiveProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY sort_order ASC, item_name ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE item_id = :itemId LIMIT 1")
    suspend fun getProductById(itemId: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE item_id = :itemId")
    suspend fun deleteProductById(itemId: String)

    @Query("UPDATE products SET sort_order = :newOrder, updated_at = :now WHERE item_id = :itemId")
    suspend fun updateSortOrder(itemId: String, newOrder: Int, now: Long = System.currentTimeMillis())

    @androidx.room.Transaction
    suspend fun updateMultipleSortOrders(orderMap: List<Pair<String, Int>>) {
        val now = System.currentTimeMillis()
        orderMap.forEach { (id, order) ->
            updateSortOrder(id, order, now)
        }
    }

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Query("SELECT COUNT(*) FROM products")
    fun getProductCountFlow(): Flow<Int>
}

data class AggregatedSalesResult(
    val totalOrders: Int,
    val totalRawPaisa: Long,
    val totalRoundedBdt: Long
)

data class SalesPeriodAggregate(
    val periodKey: String,
    val transactionCount: Int,
    val totalRawPaisa: Long,
    val totalRoundedBdt: Long
)

data class ItemSalesSummary(
    val itemName: String,
    val unit: String,
    val orderCount: Int,
    val totalRevenuePaisa: Long
)

@Dao
interface InvoiceDao {

    /**
     * Database-side paginated queries to handle up to 200,000 records smoothly without loading into memory.
     */
    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        ORDER BY date_time DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedFlow(
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY date_time DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedDateDescFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY date_time ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedDateAscFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY rounded_total_bdt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedAmountDescFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY rounded_total_bdt ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedAmountAscFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY order_id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedOrderIdDescFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY order_id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getInvoicesPagedOrderIdAscFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        """
    )
    fun getInvoiceCountFlow(query: String, startDate: Long, endDate: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        """
    )
    fun getInvoiceCountFilteredFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int>

    /**
     * Database-side aggregation for high-speed reporting over hundreds of thousands of rows.
     */
    @Query(
        """
        SELECT 
            COUNT(*) AS totalOrders,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        """
    )
    fun getAggregatedSalesFlow(
        query: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult>

    @Query(
        """
        SELECT 
            COUNT(*) AS totalOrders,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        """
    )
    fun getAggregatedSalesFilteredFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult>

    @Query(
        """
        SELECT 
            l.item_name AS itemName,
            l.unit AS unit,
            COUNT(DISTINCT l.order_id) AS orderCount,
            COALESCE(SUM(l.line_total_paisa), 0) AS totalRevenuePaisa
        FROM invoice_lines l
        INNER JOIN invoices i ON l.order_id = i.order_id
        WHERE i.date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR i.order_id LIKE '%' || :query || '%' OR i.customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR i.order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR i.customer_info LIKE '%' || :customerFilter || '%')
        GROUP BY l.item_name
        ORDER BY totalRevenuePaisa DESC
        LIMIT :limit
        """
    )
    fun getTopSellingItemsFlow(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int = 5
    ): Flow<List<ItemSalesSummary>>

    /**
     * Outstanding due for invoices created within the selected date range.
     * Evaluated using invoice-level outstanding balance: SUM(rounded_total_bdt - paid_amount_bdt).
     * Excludes fully paid invoices and clamps negative balances to zero.
     */
    @Query(
        """
        SELECT 
            COALESCE(SUM(CASE WHEN rounded_total_bdt > paid_amount_bdt THEN rounded_total_bdt - paid_amount_bdt ELSE 0 END), 0)
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        AND rounded_total_bdt > paid_amount_bdt
        """
    )
    fun getPeriodDueFilteredFlow(
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        startDate: Long,
        endDate: Long
    ): Flow<Long>

    /**
     * Overall outstanding due for all invoices across all time, independent of date filter.
     */
    @Query(
        """
        SELECT 
            COALESCE(SUM(CASE WHEN rounded_total_bdt > paid_amount_bdt THEN rounded_total_bdt - paid_amount_bdt ELSE 0 END), 0)
        FROM invoices
        WHERE rounded_total_bdt > paid_amount_bdt
        """
    )
    fun getOverallOutstandingDueFlow(): Flow<Long>

    /**
     * Aggregated sales grouped by calendar day (YYYY-MM-DD) for graph analytics.
     * Evaluated entirely database-side with zero heavy in-memory dataset mapping.
     */
    @Query(
        """
        SELECT 
            strftime('%Y-%m-%d', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    fun getSalesByDayFlow(
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        startDate: Long = 0L,
        endDate: Long = Long.MAX_VALUE
    ): Flow<List<SalesPeriodAggregate>>

    /**
     * Aggregated sales grouped by month (YYYY-MM) for monthly graph analytics.
     */
    @Query(
        """
        SELECT 
            strftime('%Y-%m', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    fun getSalesByMonthFlow(
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        startDate: Long = 0L,
        endDate: Long = Long.MAX_VALUE
    ): Flow<List<SalesPeriodAggregate>>

    /**
     * Aggregated sales grouped by year (YYYY) for yearly graph analytics.
     */
    @Query(
        """
        SELECT 
            strftime('%Y', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    fun getSalesByYearFlow(
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        startDate: Long = 0L,
        endDate: Long = Long.MAX_VALUE
    ): Flow<List<SalesPeriodAggregate>>

    @Query(
        """
        SELECT 
            strftime('%Y-%m-%d', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    suspend fun getSalesByDayDirect(startDate: Long, endDate: Long): List<SalesPeriodAggregate>

    @Query(
        """
        SELECT 
            strftime('%Y-%m', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    suspend fun getSalesByMonthDirect(startDate: Long, endDate: Long): List<SalesPeriodAggregate>

    @Query(
        """
        SELECT 
            strftime('%Y', date_time / 1000, 'unixepoch', 'localtime') AS periodKey,
            COUNT(*) AS transactionCount,
            COALESCE(SUM(raw_total_paisa), 0) AS totalRawPaisa,
            COALESCE(SUM(rounded_total_bdt), 0) AS totalRoundedBdt
        FROM invoices
        WHERE date_time BETWEEN :startDate AND :endDate
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """
    )
    suspend fun getSalesByYearDirect(startDate: Long, endDate: Long): List<SalesPeriodAggregate>

    @Query("SELECT * FROM invoices WHERE order_id = :orderId LIMIT 1")
    suspend fun getInvoiceById(orderId: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE global_transaction_id = :globalId LIMIT 1")
    suspend fun getInvoiceByGlobalId(globalId: String): InvoiceEntity?

    @Query("SELECT * FROM invoice_lines WHERE order_id = :orderId ORDER BY sort_order ASC, id ASC")
    fun getLinesForOrderFlow(orderId: String): Flow<List<InvoiceLineEntity>>

    @Query("SELECT * FROM invoice_lines WHERE order_id = :orderId ORDER BY sort_order ASC, id ASC")
    suspend fun getLinesForOrder(orderId: String): List<InvoiceLineEntity>

    @Query("SELECT * FROM invoice_lines WHERE order_id IN (:orderIds) ORDER BY sort_order ASC, id ASC")
    suspend fun getLinesForOrders(orderIds: List<String>): List<InvoiceLineEntity>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate 
        ORDER BY date_time DESC 
        LIMIT :limit
        """
    )
    suspend fun getInvoicesForExport(startDate: Long, endDate: Long, limit: Int = 200000): List<InvoiceEntity>

    @Query(
        """
        SELECT * FROM invoices 
        WHERE date_time BETWEEN :startDate AND :endDate
        AND (:query = '' OR order_id LIKE '%' || :query || '%' OR customer_info LIKE '%' || :query || '%')
        AND (:orderIdFilter = '' OR order_id LIKE '%' || :orderIdFilter || '%')
        AND (:customerFilter = '' OR customer_info LIKE '%' || :customerFilter || '%')
        ORDER BY date_time DESC 
        LIMIT :limit
        """
    )
    suspend fun getInvoicesForExportFiltered(
        startDate: Long,
        endDate: Long,
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        limit: Int = 200000
    ): List<InvoiceEntity>

    @Query(
        """
        SELECT order_id FROM invoices 
        WHERE order_id LIKE '%' || :query || '%' 
        ORDER BY date_time DESC 
        LIMIT :limit
        """
    )
    suspend fun getMatchingOrderIds(query: String, limit: Int = 8): List<String>

    @Query("UPDATE invoices SET customer_info = :customerInfo, footer_note = :footerNote, updated_at = :updatedAt WHERE order_id = :orderId")
    suspend fun updateInvoiceCustomerAndNote(
        orderId: String,
        customerInfo: String,
        footerNote: String,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query(
        """
        UPDATE invoices
        SET customer_info = :customerInfo,
            footer_note = :footerNote,
            updated_by_operator_id = :updatedByOperatorId,
            updated_by_operator_name = :updatedByOperatorName,
            updated_at = :updatedAt
        WHERE order_id = :orderId
        """
    )
    suspend fun updateInvoiceCustomerAndNoteWithOperator(
        orderId: String,
        customerInfo: String,
        footerNote: String,
        updatedByOperatorId: String?,
        updatedByOperatorName: String?,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE invoices SET paid_amount_bdt = :newPaidBdt, is_paid = :isPaid, updated_at = :updatedAt WHERE order_id = :orderId")
    suspend fun updateInvoicePayment(
        orderId: String,
        newPaidBdt: Long,
        isPaid: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query(
        """
        UPDATE invoices
        SET paid_amount_bdt = :newPaidBdt,
            is_paid = :isPaid,
            updated_by_operator_id = :updatedByOperatorId,
            updated_by_operator_name = :updatedByOperatorName,
            updated_at = :updatedAt
        WHERE order_id = :orderId
        """
    )
    suspend fun updateInvoicePaymentWithOperator(
        orderId: String,
        newPaidBdt: Long,
        isPaid: Boolean,
        updatedByOperatorId: String?,
        updatedByOperatorName: String?,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Transaction
    suspend fun settleInvoiceDueAtomic(
        orderId: String,
        additionalPaymentBdt: Long,
        paymentRecord: PaymentRecordEntity
    ): InvoiceEntity? {
        val invoice = getInvoiceById(orderId) ?: return null
        val newPaid = invoice.paidAmountBdt + additionalPaymentBdt
        val isPaid = newPaid >= invoice.roundedTotalBdt
        insertPayment(paymentRecord)
        val now = paymentRecord.timestamp
        if (!paymentRecord.collectedByOperatorId.isNullOrBlank()) {
            updateInvoicePaymentWithOperator(
                orderId = orderId,
                newPaidBdt = newPaid,
                isPaid = isPaid,
                updatedByOperatorId = paymentRecord.collectedByOperatorId,
                updatedByOperatorName = paymentRecord.collectedByOperatorName,
                updatedAt = now
            )
        } else {
            updateInvoicePayment(orderId, newPaid, isPaid, now)
        }
        return getInvoiceById(orderId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<InvoiceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceLines(lines: List<InvoiceLineEntity>)

    @Query("DELETE FROM invoice_lines WHERE order_id = :orderId")
    suspend fun deleteLinesForOrder(orderId: String)

    @Query("DELETE FROM invoices WHERE order_id = :orderId")
    suspend fun deleteInvoice(orderId: String)

    @Transaction
    suspend fun saveFullOrder(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>) {
        insertInvoice(invoice)
        deleteLinesForOrder(invoice.orderId)
        insertInvoiceLines(lines)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentRecordEntity)

    @Query("UPDATE app_settings SET next_order_sequence = next_order_sequence + 1 WHERE id = 1")
    suspend fun incrementOrderSequence()

    @Transaction
    suspend fun saveFullOrderAtomic(
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        payment: PaymentRecordEntity?
    ) {
        insertInvoice(invoice)
        deleteLinesForOrder(invoice.orderId)
        insertInvoiceLines(lines)
        if (payment != null) {
            insertPayment(payment)
        }
        incrementOrderSequence()
    }

    @Query("DELETE FROM payment_records WHERE order_id = :orderId")
    suspend fun deletePaymentsForOrder(orderId: String)

    @Transaction
    suspend fun deleteFullOrder(orderId: String) {
        deleteLinesForOrder(orderId)
        deletePaymentsForOrder(orderId)
        deleteInvoice(orderId)
    }

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun getTotalInvoiceCount(): Int

    @Query("SELECT COUNT(*) FROM invoices")
    fun getTotalInvoiceCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM invoice_lines")
    fun getTotalInvoiceLinesCountFlow(): Flow<Int>
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AppSettingsEntity)

    @Query("UPDATE app_settings SET language = :lang, updated_at = :now, updated_by_operator_id = :opId, updated_by_operator_name = :opName WHERE id = 1")
    suspend fun updateLanguageWithOperator(
        lang: String,
        opId: String?,
        opName: String?,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE app_settings SET language = :lang WHERE id = 1")
    suspend fun updateLanguage(lang: String)

    @Query("UPDATE app_settings SET theme_mode = :themeMode, updated_at = :now, updated_by_operator_id = :opId, updated_by_operator_name = :opName WHERE id = 1")
    suspend fun updateThemeModeWithOperator(
        themeMode: String,
        opId: String?,
        opName: String?,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE app_settings SET theme_mode = :themeMode WHERE id = 1")
    suspend fun updateThemeMode(themeMode: String)

    @Query("UPDATE app_settings SET paper_size = :paperSize, updated_at = :now, updated_by_operator_id = :opId, updated_by_operator_name = :opName WHERE id = 1")
    suspend fun updatePaperSizeWithOperator(
        paperSize: String,
        opId: String?,
        opName: String?,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE app_settings SET paper_size = :paperSize WHERE id = 1")
    suspend fun updatePaperSize(paperSize: String)

    @Query("UPDATE app_settings SET auto_cutter_enabled = :enabled, updated_at = :now, updated_by_operator_id = :opId, updated_by_operator_name = :opName WHERE id = 1")
    suspend fun updateAutoCutterWithOperator(
        enabled: Boolean,
        opId: String?,
        opName: String?,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE app_settings SET auto_cutter_enabled = :enabled WHERE id = 1")
    suspend fun updateAutoCutter(enabled: Boolean)

    @Query("UPDATE app_settings SET bluetooth_printer_address = :address, bluetooth_printer_name = :name, updated_at = :now, updated_by_operator_id = :opId, updated_by_operator_name = :opName WHERE id = 1")
    suspend fun updateBluetoothPrinterWithOperator(
        address: String?,
        name: String?,
        opId: String?,
        opName: String?,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE app_settings SET bluetooth_printer_address = :address, bluetooth_printer_name = :name WHERE id = 1")
    suspend fun updateBluetoothPrinter(address: String?, name: String?)

    @Query("UPDATE app_settings SET footer_note_line1 = :line1, footer_note_line2 = :line2, updated_at = :now, updated_by_operator_id = :opId, updated_by_operator_name = :opName WHERE id = 1")
    suspend fun updateFooterNotesWithOperator(
        line1: String,
        line2: String,
        opId: String?,
        opName: String?,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE app_settings SET footer_note_line1 = :line1, footer_note_line2 = :line2 WHERE id = 1")
    suspend fun updateFooterNotes(line1: String, line2: String)

    @Query("UPDATE app_settings SET next_order_sequence = next_order_sequence + 1 WHERE id = 1")
    suspend fun incrementOrderSequence()
}

data class DailySalesSummary(
    val dateString: String,
    val orderCount: Int,
    val totalBdt: Long
)

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payment_records WHERE order_id = :orderId ORDER BY timestamp ASC, id ASC")
    fun getPaymentsForOrder(orderId: String): Flow<List<PaymentRecordEntity>>

    @Query("SELECT * FROM payment_records WHERE order_id = :orderId ORDER BY timestamp ASC, id ASC")
    suspend fun getPaymentsForOrderDirect(orderId: String): List<PaymentRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentRecordEntity)

    @Query("DELETE FROM payment_records WHERE order_id = :orderId")
    suspend fun deletePaymentsForOrder(orderId: String)

    @Query("SELECT payment_method, COUNT(*) as count, SUM(amount_paid_paisa) as totalPaisa FROM payment_records GROUP BY payment_method")
    fun getPaymentMethodBreakdown(): Flow<List<PaymentMethodSummary>>

    @Query(
        """
        SELECT payment_method, COUNT(*) as count, COALESCE(SUM(amount_paid_paisa), 0) as totalPaisa 
        FROM payment_records 
        WHERE timestamp BETWEEN :startDate AND :endDate 
        GROUP BY payment_method
        """
    )
    fun getPaymentMethodBreakdownFiltered(startDate: Long, endDate: Long): Flow<List<PaymentMethodSummary>>
}

data class PaymentMethodSummary(
    val payment_method: String,
    val count: Int,
    val totalPaisa: Long
)

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers ORDER BY updated_at DESC")
    fun getAllCustomersFlow(): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT * FROM customers 
        WHERE customer_info LIKE '%' || :query || '%' 
           OR customer_name LIKE '%' || :query || '%' 
           OR phone_number LIKE '%' || :query || '%'
        ORDER BY updated_at DESC 
        LIMIT :limit
        """
    )
    fun searchCustomersFlow(query: String, limit: Int = 20): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT * FROM customers 
        WHERE customer_info LIKE '%' || :query || '%' 
           OR customer_name LIKE '%' || :query || '%' 
           OR phone_number LIKE '%' || :query || '%'
        ORDER BY updated_at DESC 
        LIMIT :limit
        """
    )
    suspend fun searchCustomers(query: String, limit: Int = 20): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE customer_info = :customerInfo LIMIT 1")
    suspend fun getCustomerByInfo(customerInfo: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE customer_info = :customerInfo")
    suspend fun deleteCustomer(customerInfo: String)

    @Query(
        """
        UPDATE customers 
        SET total_orders = total_orders + 1,
            total_spent_paisa = total_spent_paisa + :orderTotalPaisa,
            updated_at = :now
        WHERE customer_info = :customerInfo
        """
    )
    suspend fun recordCustomerOrder(customerInfo: String, orderTotalPaisa: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun getCustomerCount(): Int

    @Query("SELECT COUNT(*) FROM customers")
    fun getCustomerCountFlow(): Flow<Int>
}

data class AuditOperatorOption(
    val operatorId: String,
    val operatorName: String
)

/**
 * Strictly append-only DAO for employee activity & security audit trail.
 * Exposes NO @Update or @Delete mutation methods.
 */
@Dao
interface AuditLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(entry: AuditLogEntity): Long

    @Query(
        """
        SELECT * FROM audit_logs
        WHERE timestamp BETWEEN :startDate AND :endDate
        AND (:operatorFilter = '' OR (:operatorFilter = '__SYSTEM__' AND operator_id IN ('SYSTEM', 'LEGACY', 'UNAUTHENTICATED')) OR operator_id = :operatorFilter)
        AND (
            :categoryFilter = ''
            OR (:categoryFilter = 'AUTH' AND (entity_type = 'OPERATOR' OR action_type LIKE 'AUTH_%'))
            OR (:categoryFilter = 'EXCHANGE' AND entity_type = 'DATA_EXCHANGE')
            OR (:categoryFilter = 'COMPANY' AND entity_type = 'COMPANY_HEADER')
            OR (:categoryFilter = 'SETTINGS' AND entity_type = 'APP_SETTINGS')
            OR entity_type = :categoryFilter
        )
        AND (
            :query = ''
            OR operator_name LIKE '%' || :query || '%'
            OR operator_id LIKE '%' || :query || '%'
            OR entity_id LIKE '%' || :query || '%'
            OR summary_en LIKE '%' || :query || '%'
            OR summary_bn LIKE '%' || :query || '%'
            OR action_type LIKE '%' || :query || '%'
        )
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun getAuditLogsPagedFlow(
        operatorFilter: String,
        categoryFilter: String,
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<AuditLogEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM audit_logs
        WHERE timestamp BETWEEN :startDate AND :endDate
        AND (:operatorFilter = '' OR (:operatorFilter = '__SYSTEM__' AND operator_id IN ('SYSTEM', 'LEGACY', 'UNAUTHENTICATED')) OR operator_id = :operatorFilter)
        AND (
            :categoryFilter = ''
            OR (:categoryFilter = 'AUTH' AND (entity_type = 'OPERATOR' OR action_type LIKE 'AUTH_%'))
            OR (:categoryFilter = 'EXCHANGE' AND entity_type = 'DATA_EXCHANGE')
            OR (:categoryFilter = 'COMPANY' AND entity_type = 'COMPANY_HEADER')
            OR (:categoryFilter = 'SETTINGS' AND entity_type = 'APP_SETTINGS')
            OR entity_type = :categoryFilter
        )
        AND (
            :query = ''
            OR operator_name LIKE '%' || :query || '%'
            OR operator_id LIKE '%' || :query || '%'
            OR entity_id LIKE '%' || :query || '%'
            OR summary_en LIKE '%' || :query || '%'
            OR summary_bn LIKE '%' || :query || '%'
            OR action_type LIKE '%' || :query || '%'
        )
        """
    )
    fun getAuditLogsCountFlow(
        operatorFilter: String,
        categoryFilter: String,
        query: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int>

    @Query("SELECT * FROM audit_logs WHERE entity_type = :entityType AND entity_id = :entityId ORDER BY timestamp DESC, id DESC")
    fun getAuditLogsForEntityFlow(entityType: String, entityId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE entity_type = :entityType AND entity_id = :entityId ORDER BY timestamp DESC, id DESC")
    suspend fun getAuditLogsForEntity(entityType: String, entityId: String): List<AuditLogEntity>

    @Query("SELECT COUNT(*) FROM audit_logs")
    fun getTotalAuditLogCountFlow(): Flow<Int>

    @Query("SELECT DISTINCT operator_id AS operatorId, operator_name AS operatorName FROM audit_logs WHERE operator_id != 'SYSTEM' ORDER BY operator_name ASC")
    fun getDistinctAuditOperatorsFlow(): Flow<List<AuditOperatorOption>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC, id DESC")
    suspend fun getAllAuditLogsDirect(): List<AuditLogEntity>
}

