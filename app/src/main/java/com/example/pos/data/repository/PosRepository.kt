package com.example.pos.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.core.security.SecureSessionManager
import com.example.pos.data.audit.AuditActionType
import com.example.pos.data.audit.AuditEntityType
import com.example.pos.data.audit.AuditSanitizer
import com.example.pos.data.db.AggregatedSalesResult
import com.example.pos.data.db.AppSettingsEntity
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.CustomerEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.PosDatabase
import com.example.pos.data.db.ProductEntity
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.excel.ExcelExchangeEngine
import com.example.pos.excel.ImportConflict
import com.example.pos.excel.ImportSummary
import com.example.pos.excel.ParseXlsxResult
import com.example.pos.excel.ParsedTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class PosErrorException(val posError: PosError) : Exception(posError.message, posError.cause)

class PosRepository(
    context: Context,
    private val db: PosDatabase = PosDatabase.getInstance(context),
    private val sessionManager: SecureSessionManager = SecureSessionManager.getInstance(context.applicationContext),
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val companyDao = db.companyHeaderDao()
    private val operatorDao = db.operatorDao()
    private val productDao = db.productDao()
    private val invoiceDao = db.invoiceDao()
    private val settingsDao = db.appSettingsDao()
    private val customerDao = db.customerDao()
    private val paymentDao = db.paymentDao()
    private val auditLogDao = db.auditLogDao()

    val audit: AuditRepository by lazy {
        AuditRepositoryImpl(
            auditLogDao = auditLogDao,
            operatorDao = operatorDao,
            timeProvider = timeProvider
        )
    }

    val auth: AuthRepository by lazy {
        AuthRepositoryImpl(
            operatorDao = operatorDao,
            sessionManager = sessionManager,
            db = db,
            auditLogDao = auditLogDao,
            timeProvider = timeProvider
        )
    }
    val company: CompanyRepository by lazy {
        CompanyRepositoryImpl(
            companyHeaderDao = companyDao,
            operatorDao = operatorDao,
            auditLogDao = auditLogDao,
            db = db,
            timeProvider = timeProvider
        )
    }
    val products: ProductRepository by lazy {
        ProductRepositoryImpl(
            productDao = productDao,
            operatorDao = operatorDao,
            auditLogDao = auditLogDao,
            db = db,
            timeProvider = timeProvider
        )
    }
    val invoices: InvoiceRepository by lazy {
        InvoiceRepositoryImpl(
            invoiceDao = invoiceDao,
            appSettingsDao = settingsDao,
            customerDao = customerDao,
            operatorDao = operatorDao,
            auditLogDao = auditLogDao,
            db = db,
            timeProvider = timeProvider
        )
    }
    val history: HistoryRepository by lazy {
        HistoryRepositoryImpl(
            invoiceDao = invoiceDao,
            paymentDao = paymentDao,
            operatorDao = operatorDao,
            auditLogDao = auditLogDao,
            db = db,
            timeProvider = timeProvider
        )
    }
    val payments: PaymentRepository by lazy {
        PaymentRepositoryImpl(
            paymentDao = paymentDao,
            operatorDao = operatorDao,
            auditLogDao = auditLogDao,
            db = db,
            timeProvider = timeProvider
        )
    }
    val reports: ReportRepository by lazy { ReportRepositoryImpl(invoiceDao) }
    val settings: SettingsRepository by lazy {
        SettingsRepositoryImpl(
            appSettingsDao = settingsDao,
            operatorDao = operatorDao,
            auditLogDao = auditLogDao,
            db = db,
            timeProvider = timeProvider
        )
    }

    val companyHeaderFlow: Flow<CompanyHeaderEntity?> = companyDao.getCompanyHeaderFlow()
    val activeOperatorFlow: Flow<OperatorEntity?> = auth.activeOperator
    val operatorCountFlow: Flow<Int> = auth.operatorCount
    val allActiveProductsFlow: Flow<List<ProductEntity>> = productDao.getAllActiveProductsFlow()
    val allProductsFlow: Flow<List<ProductEntity>> = productDao.getAllProductsFlow()
    val appSettingsFlow: Flow<AppSettingsEntity?> = settingsDao.getSettingsFlow()

    suspend fun getActiveOperator(): OperatorEntity? = withContext(Dispatchers.IO) {
        auth.getActiveOperator()
    }

    suspend fun getOperatorCount(): Int = withContext(Dispatchers.IO) {
        auth.getOperatorCount()
    }

    suspend fun hasRegisteredOperators(): Boolean = withContext(Dispatchers.IO) {
        auth.hasRegisteredOperators()
    }

    suspend fun checkAndRestoreSession(): OperatorEntity? = withContext(Dispatchers.IO) {
        auth.checkAndRestoreSession()
    }

    suspend fun getRememberedPhone(): String = withContext(Dispatchers.IO) {
        auth.getRememberedPhoneHint()
    }

    suspend fun isRememberMeSaved(): Boolean = withContext(Dispatchers.IO) {
        auth.isRememberMeHintEnabled()
    }

    suspend fun getLockoutRemainingSeconds(phone: String): Long = withContext(Dispatchers.IO) {
        auth.getLockoutRemainingSeconds(phone)
    }

    suspend fun getCompanyHeader(): CompanyHeaderEntity? = withContext(Dispatchers.IO) {
        companyDao.getCompanyHeader()
    }

    suspend fun updateCompanyHeader(header: CompanyHeaderEntity) {
        updateCompanyHeaderAudited(header).unwrapOrThrow()
    }

    suspend fun updateCompanyHeaderAudited(
        header: CompanyHeaderEntity,
        logoChangeAction: String? = null
    ): PosResult<Unit> = withContext(Dispatchers.IO) {
        company.updateCompanyHeaderEntity(header, logoChangeAction)
    }

    suspend fun login(phone: String, passwordRaw: String, rememberMe: Boolean): Result<OperatorEntity> =
        withContext(Dispatchers.IO) {
            auth.login(phone, passwordRaw, rememberMe).toKotlinResult()
        }

    suspend fun registerOperatorWithRecovery(
        name: String,
        phone: String,
        address: String,
        passwordRaw: String
    ): Result<RegistrationResult> = withContext(Dispatchers.IO) {
        auth.register(name, phone, address, passwordRaw).toKotlinResult()
    }

    suspend fun registerOperator(
        name: String,
        phone: String,
        address: String,
        passwordRaw: String
    ): Result<OperatorEntity> = withContext(Dispatchers.IO) {
        auth.register(name, phone, address, passwordRaw).toKotlinResult().map { it.operator }
    }

    suspend fun verifyRecoveryCode(phone: String, recoveryCodeRaw: String): Result<OperatorEntity> =
        withContext(Dispatchers.IO) {
            auth.verifyRecoveryCode(phone, recoveryCodeRaw).toKotlinResult()
        }

    suspend fun resetPassword(
        phone: String,
        recoveryCodeRaw: String,
        newPasswordRaw: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        auth.resetPassword(phone, recoveryCodeRaw, newPasswordRaw).toKotlinResult()
    }

    suspend fun regenerateRecoveryCode(userId: String): Result<String> = withContext(Dispatchers.IO) {
        auth.regenerateRecoveryCode(userId).toKotlinResult()
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        auth.logout()
    }

    suspend fun updateOperatorProfile(name: String, phone: String, address: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            auth.updateProfile(name, phone, address).toKotlinResult()
        }

    private fun <T> PosResult<T>.toKotlinResult(): Result<T> = when (this) {
        is PosResult.Success -> Result.success(data)
        is PosResult.Error -> Result.failure(PosErrorException(error))
    }

    private fun <T> PosResult<T>.unwrapOrThrow(): T = when (this) {
        is PosResult.Success -> data
        is PosResult.Error -> throw PosErrorException(error)
    }

    // Product CRUD (all routed through audited ProductRepository)
    suspend fun saveProduct(product: ProductEntity) {
        saveProductAudited(product).unwrapOrThrow()
    }

    suspend fun saveProductAudited(product: ProductEntity): PosResult<ProductEntity> = withContext(Dispatchers.IO) {
        products.saveProductEntity(product)
    }

    suspend fun deleteProduct(itemId: String) {
        deleteProductAudited(itemId).unwrapOrThrow()
    }

    suspend fun deleteProductAudited(itemId: String): PosResult<Unit> = withContext(Dispatchers.IO) {
        products.deleteProduct(itemId)
    }

    suspend fun updateProductSortOrder(itemId: String, newOrder: Int) {
        products.updateSortOrder(itemId, newOrder).unwrapOrThrow()
    }

    suspend fun updateProductReorder(orderedItemIds: List<String>) {
        updateProductReorderAudited(orderedItemIds).unwrapOrThrow()
    }

    suspend fun updateProductReorderAudited(orderedItemIds: List<String>): PosResult<Unit> = withContext(Dispatchers.IO) {
        products.updateProductReorder(orderedItemIds)
    }

    // Invoice & Transaction operations
    fun getInvoicesPaged(
        query: String,
        startDate: Long,
        endDate: Long,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>> {
        return invoiceDao.getInvoicesPagedFlow(query.trim(), startDate, endDate, limit, offset)
    }

    fun getInvoicesPaged(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        sortOption: HistorySortOption,
        limit: Int,
        offset: Int
    ): Flow<List<InvoiceEntity>> {
        return history.getInvoicesPaged(query, orderIdFilter, customerFilter, startDate, endDate, sortOption, limit, offset)
    }

    fun getInvoiceCount(query: String, startDate: Long, endDate: Long): Flow<Int> {
        return invoiceDao.getInvoiceCountFlow(query.trim(), startDate, endDate)
    }

    fun getInvoiceCount(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<Int> {
        return history.getInvoiceCount(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getAggregatedSales(query: String, startDate: Long, endDate: Long): Flow<AggregatedSalesResult> {
        return invoiceDao.getAggregatedSalesFlow(query.trim(), startDate, endDate)
    }

    fun getAggregatedSales(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<AggregatedSalesResult> {
        return history.getAggregatedSales(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getTopSellingItems(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long,
        limit: Int = 5
    ): Flow<List<com.example.pos.data.db.ItemSalesSummary>> {
        return history.getTopSellingItems(query, orderIdFilter, customerFilter, startDate, endDate, limit)
    }

    fun getSalesByDay(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> {
        return history.getSalesByDay(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getSalesByMonth(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> {
        return history.getSalesByMonth(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getSalesByYear(
        query: String,
        orderIdFilter: String,
        customerFilter: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<com.example.pos.data.db.SalesPeriodAggregate>> {
        return history.getSalesByYear(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getPeriodDue(
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = "",
        startDate: Long,
        endDate: Long
    ): Flow<Long> {
        return history.getPeriodDue(query, orderIdFilter, customerFilter, startDate, endDate)
    }

    fun getOverallOutstandingDue(): Flow<Long> {
        return history.getOverallOutstandingDue()
    }

    fun getPaymentMethodBreakdownFiltered(startDate: Long, endDate: Long): Flow<List<com.example.pos.data.db.PaymentMethodSummary>> {
        return payments.getPaymentMethodBreakdownFiltered(startDate, endDate)
    }

    fun getPaymentsForOrder(orderId: String): Flow<List<PaymentRecordEntity>> {
        return payments.getPaymentsForOrder(orderId)
    }

    suspend fun getPaymentsForOrderDirect(orderId: String): List<PaymentRecordEntity> =
        withContext(Dispatchers.IO) {
            payments.getPaymentsForOrderDirect(orderId)
        }

    fun getLinesForOrder(orderId: String): Flow<List<InvoiceLineEntity>> {
        return invoiceDao.getLinesForOrderFlow(orderId)
    }

    suspend fun getLinesForOrderDirect(orderId: String): List<InvoiceLineEntity> =
        withContext(Dispatchers.IO) {
            invoiceDao.getLinesForOrder(orderId)
        }

    suspend fun getLinesForOrders(orderIds: List<String>): List<InvoiceLineEntity> =
        withContext(Dispatchers.IO) {
            history.getLinesForOrders(orderIds)
        }

    suspend fun getInvoicesForExport(startDate: Long, endDate: Long): List<InvoiceEntity> =
        withContext(Dispatchers.IO) {
            invoiceDao.getInvoicesForExport(startDate, endDate)
        }

    suspend fun getInvoicesForExportFiltered(
        startDate: Long,
        endDate: Long,
        query: String = "",
        orderIdFilter: String = "",
        customerFilter: String = ""
    ): List<InvoiceEntity> =
        withContext(Dispatchers.IO) {
            invoiceDao.getInvoicesForExportFiltered(startDate, endDate, query, orderIdFilter, customerFilter)
        }

    suspend fun getMatchingOrderIds(query: String, limit: Int = 8): List<String> =
        withContext(Dispatchers.IO) {
            history.getMatchingOrderIds(query, limit)
        }

    suspend fun updateInvoiceCustomerAndNote(orderId: String, customerInfo: String, footerNote: String): Int =
        withContext(Dispatchers.IO) {
            history.updateInvoiceCustomerAndNote(orderId, customerInfo, footerNote)
        }

    suspend fun updateInvoiceCustomerAndNoteAudited(
        orderId: String,
        customerInfo: String,
        footerNote: String
    ): PosResult<Int> = withContext(Dispatchers.IO) {
        history.updateInvoiceCustomerAndNoteAudited(orderId, customerInfo, footerNote)
    }

    suspend fun settleInvoiceDue(orderId: String, additionalPaymentBdt: Long, method: String = "CASH"): PosResult<InvoiceEntity> =
        withContext(Dispatchers.IO) {
            history.settleInvoiceDue(orderId, additionalPaymentBdt, method)
        }

    suspend fun deleteOrderAudited(orderId: String): PosResult<Unit> = withContext(Dispatchers.IO) {
        history.deleteOrderAudited(orderId)
    }

    suspend fun getInvoiceById(orderId: String): InvoiceEntity? =
        withContext(Dispatchers.IO) {
            invoiceDao.getInvoiceById(orderId)
        }

    suspend fun saveOrder(
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        payment: PaymentRecordEntity? = null
    ) = withContext(Dispatchers.IO) {
        when (val res = invoices.saveOrder(invoice, lines, payment)) {
            is PosResult.Success -> Unit
            is PosResult.Error -> if (res.error is PosError.Unauthenticated) {
                throw PosErrorException(res.error)
            } else {
                throw IllegalArgumentException(res.error.message)
            }
        }
    }

    suspend fun checkoutOrderAtomic(
        invoiceTemplate: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        customerInfoForCrm: String? = null
    ): PosResult<InvoiceEntity> = withContext(Dispatchers.IO) {
        invoices.checkoutOrderAtomic(invoiceTemplate, lines, customerInfoForCrm)
    }

    suspend fun getNextOrderId(): String = withContext(Dispatchers.IO) {
        val settings = settingsDao.getSettings()
        val seq = settings?.nextOrderSequence ?: 10294L
        "INV-$seq"
    }

    suspend fun importInvoices(invoices: List<InvoiceEntity>, lines: List<InvoiceLineEntity>) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: throw PosErrorException(PosError.Unauthenticated())
                for (inv in invoices) {
                    val matchingLines = lines.filter { it.orderId == inv.orderId }
                    invoiceDao.saveFullOrder(
                        inv.copy(updatedByOperatorId = null, updatedByOperatorName = null),
                        matchingLines
                    )
                }
                val now = timeProvider()
                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.XLSX_IMPORTED,
                        entityType = AuditEntityType.DATA_EXCHANGE,
                        entityId = "BATCH-IMPORT-${now}",
                        summaryEn = "Imported ${invoices.size} invoice records",
                        summaryBn = "${AuditSanitizer.localizeCount(invoices.size, "bn")}টি ইনভয়েস ইমপোর্ট করা হয়েছে",
                        metadataJson = AuditSanitizer.buildSnapshotJson(mapOf("importedCount" to invoices.size)),
                        isSuccess = true
                    )
                )
            }
        }

    suspend fun importAndMergeTransactions(parsed: ParseXlsxResult): ImportSummary = withContext(Dispatchers.IO) {
        val actor = operatorDao.getActiveOperator()
            ?: throw PosErrorException(PosError.Unauthenticated())

        var importedCount = 0
        var duplicateSkippedCount = 0
        var conflictCount = 0
        var failedCount = 0
        val invalidCount = parsed.invalidRows
        val conflicts = mutableListOf<ImportConflict>()
        val validationErrors = parsed.validationErrors.toMutableList()

        val toInsert = mutableListOf<ParsedTransaction>()
        val processedGlobalIds = mutableSetOf<String>()
        val processedOrderIds = mutableSetOf<String>()

        for (candidate in parsed.transactions) {
            val inv = candidate.invoice

            // 1. Check duplicate within file
            if (processedGlobalIds.contains(inv.globalTransactionId) || processedOrderIds.contains(inv.orderId)) {
                duplicateSkippedCount++
                continue
            }

            // 2. Query local database by Global ID first, then by Order ID
            val existing = (if (inv.globalTransactionId.isNotBlank()) invoiceDao.getInvoiceByGlobalId(inv.globalTransactionId) else null)
                ?: invoiceDao.getInvoiceById(inv.orderId)

            if (existing == null) {
                // Brand new transaction! Preserve imported creator attribution if present, keep updatedBy null
                toInsert.add(candidate)
                processedGlobalIds.add(inv.globalTransactionId)
                processedOrderIds.add(inv.orderId)
            } else {
                // Existing record found: check if identical vs conflict
                val isIdentical = (existing.rawTotalPaisa == inv.rawTotalPaisa) &&
                    (existing.roundedTotalBdt == inv.roundedTotalBdt) &&
                    (existing.customerInfo.trim() == inv.customerInfo.trim()) &&
                    (existing.operatorName.trim() == inv.operatorName.trim() || inv.operatorName.isBlank())

                if (isIdentical) {
                    duplicateSkippedCount++
                    processedGlobalIds.add(inv.globalTransactionId)
                    processedOrderIds.add(inv.orderId)
                } else {
                    conflictCount++
                    val reason = when {
                        existing.rawTotalPaisa != inv.rawTotalPaisa ->
                            "Amount mismatch: Local ৳${FinancialCalculationEngine.formatPaisaToBdt(existing.rawTotalPaisa)} vs Imported ৳${FinancialCalculationEngine.formatPaisaToBdt(inv.rawTotalPaisa)}"
                        existing.customerInfo.trim() != inv.customerInfo.trim() ->
                            "Customer mismatch: Local '${existing.customerInfo}' vs Imported '${inv.customerInfo}'"
                        else ->
                            "Transaction data mismatch for Order ID ${existing.orderId}"
                    }
                    conflicts.add(
                        ImportConflict(
                            globalTransactionId = inv.globalTransactionId,
                            orderId = inv.orderId,
                            localSummary = "${existing.orderId} | ৳${FinancialCalculationEngine.formatPaisaToBdt(existing.rawTotalPaisa)} | ${existing.customerInfo}",
                            importedSummary = "${inv.orderId} | ৳${FinancialCalculationEngine.formatPaisaToBdt(inv.rawTotalPaisa)} | ${inv.customerInfo}",
                            reason = reason
                        )
                    )
                    processedGlobalIds.add(inv.globalTransactionId)
                    processedOrderIds.add(inv.orderId)
                }
            }
        }

        // 3. Perform single atomic Room transaction for all valid new records + XLSX_IMPORTED audit log
        try {
            db.withTransaction {
                val now = timeProvider()
                for (item in toInsert) {
                    val preservedInvoice = item.invoice.copy(
                        updatedByOperatorId = null,
                        updatedByOperatorName = null
                    )
                    invoiceDao.insertInvoice(preservedInvoice)
                    invoiceDao.deleteLinesForOrder(preservedInvoice.orderId)
                    if (item.lines.isNotEmpty()) {
                        invoiceDao.insertInvoiceLines(item.lines)
                    }
                    val custInfo = preservedInvoice.customerInfo.trim()
                    if (custInfo.isNotBlank() && custInfo != "External Customer") {
                        CustomerRepositoryImpl.recordCustomerOrderWithinTransaction(
                            customerDao = customerDao,
                            auditLogDao = auditLogDao,
                            actor = actor,
                            customerInfo = custInfo,
                            totalPaisa = preservedInvoice.rawTotalPaisa,
                            now = now
                        )
                    }
                }
                importedCount = toInsert.size

                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.XLSX_IMPORTED,
                        entityType = AuditEntityType.DATA_EXCHANGE,
                        entityId = "XLSX-IMPORT-$now",
                        summaryEn = "XLSX import completed ($importedCount imported, $duplicateSkippedCount skipped, $conflictCount conflicts)",
                        summaryBn = "XLSX ইমপোর্ট সম্পন্ন (${AuditSanitizer.localizeCount(importedCount, "bn")} নতুন, ${AuditSanitizer.localizeCount(duplicateSkippedCount, "bn")} বাদ, ${AuditSanitizer.localizeCount(conflictCount, "bn")} কনফ্লিক্ট)",
                        metadataJson = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "importedCount" to importedCount,
                                "skippedCount" to duplicateSkippedCount,
                                "conflictCount" to conflictCount,
                                "invalidCount" to invalidCount
                            )
                        ),
                        isSuccess = true
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            importedCount = 0
            failedCount = toInsert.size
            validationErrors.add("Database transaction error: ${e.message}")
        }

        ImportSummary(
            totalRows = parsed.totalRowsParsed,
            importedCount = importedCount,
            duplicateSkippedCount = duplicateSkippedCount,
            invalidCount = invalidCount,
            conflictCount = conflictCount,
            failedCount = failedCount,
            conflicts = conflicts,
            validationErrors = validationErrors
        )
    }

    suspend fun importSalesFromUri(context: Context, uri: android.net.Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        if (operatorDao.getActiveOperator() == null) {
            return@withContext Result.failure(PosErrorException(PosError.Unauthenticated()))
        }
        val parseResult = ExcelExchangeEngine.parseXlsxFromUri(context, uri)
        if (parseResult.isFailure) {
            return@withContext Result.failure(parseResult.exceptionOrNull() ?: Exception("Import failed."))
        }
        val summary = importAndMergeTransactions(parseResult.getOrThrow())
        Result.success(summary)
    }

    suspend fun importSalesFromStream(stream: java.io.InputStream): Result<ImportSummary> = withContext(Dispatchers.IO) {
        if (operatorDao.getActiveOperator() == null) {
            return@withContext Result.failure(PosErrorException(PosError.Unauthenticated()))
        }
        val parseResult = ExcelExchangeEngine.parseXlsx(stream)
        if (parseResult.isFailure) {
            return@withContext Result.failure(parseResult.exceptionOrNull() ?: Exception("Import failed."))
        }
        val summary = importAndMergeTransactions(parseResult.getOrThrow())
        Result.success(summary)
    }

    suspend fun recordXlsxExportAudit(
        exportType: String,
        recordCount: Int,
        rangeLabel: String? = null,
        fileName: String? = null
    ): PosResult<Unit> = withContext(Dispatchers.IO) {
        try {
            db.withTransaction {
                val actor = operatorDao.getActiveOperator()
                    ?: return@withTransaction PosResult.Error(PosError.Unauthenticated())

                val now = timeProvider()
                auditLogDao.insertAuditLog(
                    AuditLogEntity(
                        eventId = "AUD-" + UUID.randomUUID().toString(),
                        timestamp = now,
                        operatorId = actor.userId,
                        operatorName = actor.operatorName,
                        actionType = AuditActionType.XLSX_EXPORTED,
                        entityType = AuditEntityType.DATA_EXCHANGE,
                        entityId = "XLSX-EXPORT-$now",
                        summaryEn = "Exported $recordCount invoice records ($exportType)",
                        summaryBn = "${AuditSanitizer.localizeCount(recordCount, "bn")}টি লেনদেন XLSX ফাইলে এক্সপোর্ট করা হয়েছে",
                        metadataJson = AuditSanitizer.buildSnapshotJson(
                            mapOf(
                                "exportType" to exportType,
                                "recordCount" to recordCount,
                                "dateRange" to (rangeLabel ?: "ALL"),
                                "fileName" to (fileName ?: "")
                            )
                        ),
                        isSuccess = true
                    )
                )
                PosResult.Success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PosResult.Error(PosError.DatabaseError("Failed to record export audit: ${e.message}", e))
        }
    }

    // Settings (all routed through audited SettingsRepository)
    suspend fun updateLanguage(lang: String) {
        settings.updateLanguage(lang).unwrapOrThrow()
    }

    suspend fun updateLanguageAudited(lang: String): PosResult<Unit> = withContext(Dispatchers.IO) {
        settings.updateLanguage(lang)
    }

    suspend fun updateThemeMode(themeMode: String) {
        settings.updateThemeMode(themeMode).unwrapOrThrow()
    }

    suspend fun updateThemeModeAudited(themeMode: String): PosResult<Unit> = withContext(Dispatchers.IO) {
        settings.updateThemeMode(themeMode)
    }

    suspend fun updatePaperSize(paperSize: String) {
        settings.updatePaperSize(paperSize).unwrapOrThrow()
    }

    suspend fun updatePaperSizeAudited(paperSize: String): PosResult<Unit> = withContext(Dispatchers.IO) {
        settings.updatePaperSize(paperSize)
    }

    suspend fun updateAutoCutter(enabled: Boolean) {
        settings.updateAutoCutter(enabled).unwrapOrThrow()
    }

    suspend fun updateAutoCutterAudited(enabled: Boolean): PosResult<Unit> = withContext(Dispatchers.IO) {
        settings.updateAutoCutter(enabled)
    }

    suspend fun updateBluetoothPrinter(address: String?, name: String?) {
        settings.updateBluetoothPrinter(address, name).unwrapOrThrow()
    }

    suspend fun updateBluetoothPrinterAudited(address: String?, name: String?): PosResult<Unit> = withContext(Dispatchers.IO) {
        settings.updateBluetoothPrinter(address, name)
    }

    suspend fun updateFooterNotes(line1: String, line2: String) {
        settings.updateFooterNotes(line1, line2).unwrapOrThrow()
    }

    suspend fun updateFooterNotesAudited(line1: String, line2: String): PosResult<Unit> = withContext(Dispatchers.IO) {
        settings.updateFooterNotes(line1, line2)
    }

    // Customer Operations (routed through audited CustomerRepository)
    val customersRepo: CustomerRepository by lazy {
        CustomerRepositoryImpl(
            customerDao = customerDao,
            operatorDao = operatorDao,
            auditLogDao = auditLogDao,
            db = db,
            timeProvider = timeProvider
        )
    }

    val allCustomersFlow: Flow<List<CustomerEntity>> = customerDao.getAllCustomersFlow()

    // Database Capacity & Statistics Monitoring
    val totalInvoiceCountFlow: Flow<Int> = invoiceDao.getTotalInvoiceCountFlow()
    val totalInvoiceLinesCountFlow: Flow<Int> = invoiceDao.getTotalInvoiceLinesCountFlow()
    val totalProductCountFlow: Flow<Int> = productDao.getProductCountFlow()
    val totalCustomerCountFlow: Flow<Int> = customerDao.getCustomerCountFlow()

    fun getDatabaseSizeBytes(context: Context): Long {
        return try {
            val dbFile = context.getDatabasePath("pos_invoice_master.db")
            val walFile = context.getDatabasePath("pos_invoice_master.db-wal")
            val shmFile = context.getDatabasePath("pos_invoice_master.db-shm")
            var size = 0L
            if (dbFile.exists()) size += dbFile.length()
            if (walFile.exists()) size += walFile.length()
            if (shmFile.exists()) size += shmFile.length()
            size
        } catch (e: Exception) {
            0L
        }
    }

    fun searchCustomersFlow(query: String, limit: Int = 10): Flow<List<CustomerEntity>> {
        return customerDao.searchCustomersFlow(query.trim(), limit)
    }

    suspend fun searchCustomersDirect(query: String, limit: Int = 10): List<CustomerEntity> =
        withContext(Dispatchers.IO) {
            customerDao.searchCustomers(query.trim(), limit)
        }

    suspend fun getCustomerByInfo(customerInfo: String): CustomerEntity? =
        withContext(Dispatchers.IO) {
            customerDao.getCustomerByInfo(customerInfo.trim())
        }

    suspend fun saveCustomer(customer: CustomerEntity) {
        customersRepo.saveCustomer(customer).unwrapOrThrow()
    }

    suspend fun recordCustomerOrder(customerInfo: String, totalPaisa: Long) {
        customersRepo.recordCustomerOrder(customerInfo, totalPaisa).unwrapOrThrow()
    }
}
