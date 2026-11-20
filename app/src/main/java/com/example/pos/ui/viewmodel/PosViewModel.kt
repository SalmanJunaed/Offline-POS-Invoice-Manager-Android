package com.example.pos.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pos.core.error.PosError
import com.example.pos.core.error.PosResult
import com.example.pos.core.security.AuthInputValidator
import com.example.pos.core.util.CompanyLogoManager
import com.example.pos.data.Localization
import com.example.pos.data.audit.AuditCategoryFilter
import com.example.pos.data.audit.AuditDateFilter
import com.example.pos.data.db.AuditLogEntity
import com.example.pos.data.db.AuditOperatorOption
import com.example.pos.data.db.CompanyHeaderEntity
import com.example.pos.data.db.CustomerEntity
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.OperatorEntity
import com.example.pos.data.db.PaymentRecordEntity
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.db.ItemSalesSummary
import com.example.pos.data.repository.HistorySortOption
import com.example.pos.data.repository.PosErrorException
import com.example.pos.data.repository.PosRepository
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.excel.ExcelExchangeEngine
import com.example.pos.excel.ImportSummary
import com.example.pos.modules.pdf.PdfInvoiceEngine
import com.example.pos.printer.BluetoothDeviceItem
import com.example.pos.printer.BluetoothPrinterManager
import com.example.pos.printer.EscPosPrinterEngine
import com.example.pos.printer.PrinterConnectionState
import com.example.pos.printer.ThermalReceiptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.io.File
import java.math.BigDecimal
import java.util.Calendar
import java.util.UUID

enum class DraftCalculationDriver {
    QUANTITY,
    TOTAL_AMOUNT
}

data class DraftInvoiceItem(
    val tempId: String = UUID.randomUUID().toString(),
    val itemId: String,
    val itemName: String,
    val itemType: String,
    val unit: String,
    val quantityStr: String,
    val unitPriceBdtStr: String,
    val unitPricePaisa: Long,
    val lineTotalPaisa: Long,
    val lineTotalBdtStr: String = FinancialCalculationEngine.formatPaisaToBdtInput(lineTotalPaisa),
    val calculationDriver: DraftCalculationDriver = DraftCalculationDriver.QUANTITY
)

enum class DateFilterMode {
    ALL, TODAY, YESTERDAY, WEEK, MONTH, SIX_MONTHS, YEAR, CUSTOM
}

enum class AnalyticsGrouping {
    DAY, MONTH, YEAR
}

enum class AnalyticsMetric {
    ROUNDED_SALES, RAW_SALES, TRANSACTION_COUNT
}

class PosViewModel private constructor(
    application: Application,
    private val repository: PosRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, PosRepository(application))

    companion object {
        @VisibleForTesting
        internal fun createForTest(
            application: Application,
            repository: PosRepository
        ): PosViewModel = PosViewModel(application, repository)
    }

    @VisibleForTesting
    internal fun cancelScopeForTest() {
        viewModelScope.cancel()
    }

    // Language & Theme State
    private val _language = MutableStateFlow("bn")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow("LIGHT")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Session State
    val activeOperator: StateFlow<OperatorEntity?> = repository.activeOperatorFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val companyHeader: StateFlow<CompanyHeaderEntity?> = repository.companyHeaderFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allProducts: StateFlow<List<ProductEntity>> = repository.allProductsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allActiveProducts: StateFlow<List<ProductEntity>> = repository.allActiveProductsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val appSettings = repository.appSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Active Invoice Draft
    private val _draftOrderId = MutableStateFlow("INV-10294")
    val draftOrderId: StateFlow<String> = _draftOrderId.asStateFlow()

    private val _customerInfo = MutableStateFlow("")
    val customerInfo: StateFlow<String> = _customerInfo.asStateFlow()

    private val _customerInfoError = MutableStateFlow<String?>(null)
    val customerInfoError: StateFlow<String?> = _customerInfoError.asStateFlow()

    // Customer search suggestions for fast autocomplete
    private val _customerSuggestions = MutableStateFlow<List<CustomerEntity>>(emptyList())
    val customerSuggestions: StateFlow<List<CustomerEntity>> = _customerSuggestions.asStateFlow()

    // All registered customers list
    val allCustomers: StateFlow<List<CustomerEntity>> = repository.allCustomersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _draftItems = MutableStateFlow<List<DraftInvoiceItem>>(emptyList())
    val draftItems: StateFlow<List<DraftInvoiceItem>> = _draftItems.asStateFlow()

    // Real-time calculation state
    val rawSubtotalPaisa: StateFlow<Long> = _draftItems.combine(_customerInfo) { items, _ ->
        FinancialCalculationEngine.calculateRawTotal(items.map { it.lineTotalPaisa })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val roundedTotalBdt: StateFlow<Long> = rawSubtotalPaisa.combine(_draftItems) { raw, _ ->
        FinancialCalculationEngine.calculateRoundedTotalBdt(raw)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val roundingDifferencePaisa: StateFlow<Long> = rawSubtotalPaisa.combine(roundedTotalBdt) { raw, rounded ->
        FinancialCalculationEngine.calculateRoundingDifferencePaisa(raw, rounded)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _draftPaidAmountBdt = MutableStateFlow<String>("")
    val draftPaidAmountBdt: StateFlow<String> = _draftPaidAmountBdt.asStateFlow()

    fun setDraftPaidAmount(amount: String) {
        _draftPaidAmountBdt.value = amount
    }

    val isDraftFullPaid: StateFlow<Boolean> = combine(
        roundedTotalBdt,
        _draftPaidAmountBdt
    ) { roundedTotal, paidText ->
        if (roundedTotal <= 0L) return@combine false
        val totalPaisa = roundedTotal * 100L
        val paidPaisa = if (paidText.isBlank()) totalPaisa else FinancialCalculationEngine.parseBdtToPaisa(paidText)
        val duePaisa = maxOf(0L, totalPaisa - paidPaisa)
        paidPaisa >= totalPaisa && duePaisa <= 0L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val effectiveDraftPaidBdt: StateFlow<Long> = combine(
        roundedTotalBdt,
        _draftPaidAmountBdt
    ) { roundedTotal, paidText ->
        if (paidText.isBlank()) roundedTotal else FinancialCalculationEngine.parseBdtToPaisa(paidText) / 100L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val draftDueBdt: StateFlow<Long> = combine(
        roundedTotalBdt,
        effectiveDraftPaidBdt
    ) { roundedTotal, paidBdt ->
        maxOf(0L, roundedTotal - paidBdt)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // Sales History & Reports Filters
    private val _historyQuery = MutableStateFlow("")
    val historyQuery: StateFlow<String> = _historyQuery.asStateFlow()

    private val _historyOrderIdFilter = MutableStateFlow("")
    val historyOrderIdFilter: StateFlow<String> = _historyOrderIdFilter.asStateFlow()

    private val _historyCustomerFilter = MutableStateFlow("")
    val historyCustomerFilter: StateFlow<String> = _historyCustomerFilter.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilterMode.ALL)
    val dateFilter: StateFlow<DateFilterMode> = _dateFilter.asStateFlow()

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate: StateFlow<Long?> = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate: StateFlow<Long?> = _customEndDate.asStateFlow()

    private val _historySortOption = MutableStateFlow(HistorySortOption.DATE_DESC)
    val historySortOption: StateFlow<HistorySortOption> = _historySortOption.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pageSize = MutableStateFlow(25)
    val pageSize: StateFlow<Int> = _pageSize.asStateFlow()

    private val _totalFilteredCount = MutableStateFlow(0)
    val totalFilteredCount: StateFlow<Int> = _totalFilteredCount.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    private val _orderLinesCache = MutableStateFlow<Map<String, List<InvoiceLineEntity>>>(emptyMap())
    val orderLinesCache: StateFlow<Map<String, List<InvoiceLineEntity>>> = _orderLinesCache.asStateFlow()

    private val _expandedOrderId = MutableStateFlow<String?>(null)
    val expandedOrderId: StateFlow<String?> = _expandedOrderId.asStateFlow()

    private val _expandedLines = MutableStateFlow<List<InvoiceLineEntity>>(emptyList())
    val expandedLines: StateFlow<List<InvoiceLineEntity>> = _expandedLines.asStateFlow()

    // Invoices list for history (paged slice)
    private val _historyInvoices = MutableStateFlow<List<InvoiceEntity>>(emptyList())
    val historyInvoices: StateFlow<List<InvoiceEntity>> = _historyInvoices.asStateFlow()

    // Top selling products database aggregation for report
    private val _topSellingItems = MutableStateFlow<List<ItemSalesSummary>>(emptyList())
    val topSellingItems: StateFlow<List<ItemSalesSummary>> = _topSellingItems.asStateFlow()

    // Aggregations
    private val _aggregatedOrders = MutableStateFlow(0)
    val aggregatedOrders: StateFlow<Int> = _aggregatedOrders.asStateFlow()

    private val _aggregatedRawPaisa = MutableStateFlow(0L)
    val aggregatedRawPaisa: StateFlow<Long> = _aggregatedRawPaisa.asStateFlow()

    private val _aggregatedRoundedBdt = MutableStateFlow(0L)
    val aggregatedRoundedBdt: StateFlow<Long> = _aggregatedRoundedBdt.asStateFlow()

    // Multi-Branch Sync & Import/Export State
    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val exportDateRange: StateFlow<Pair<Long, Long>?> = _exportDateRange.asStateFlow()

    fun setExportDateRange(range: Pair<Long, Long>?) {
        _exportDateRange.value = range
    }

    private val _orderIdSuggestions = MutableStateFlow<List<String>>(emptyList())
    val orderIdSuggestions: StateFlow<List<String>> = _orderIdSuggestions.asStateFlow()

    fun clearImportSummary() {
        _importSummary.value = null
    }

    // Analytics Graph State
    private val _analyticsGrouping = MutableStateFlow(AnalyticsGrouping.DAY)
    val analyticsGrouping: StateFlow<AnalyticsGrouping> = _analyticsGrouping.asStateFlow()

    private val _analyticsMetric = MutableStateFlow(AnalyticsMetric.ROUNDED_SALES)
    val analyticsMetric: StateFlow<AnalyticsMetric> = _analyticsMetric.asStateFlow()

    private val _analyticsDataPoints = MutableStateFlow<List<com.example.pos.data.db.SalesPeriodAggregate>>(emptyList())
    val analyticsDataPoints: StateFlow<List<com.example.pos.data.db.SalesPeriodAggregate>> = _analyticsDataPoints.asStateFlow()

    val chartDataPoints: StateFlow<List<com.example.pos.domain.model.ChartDataPoint>> = combine(
        _analyticsDataPoints,
        _analyticsMetric,
        language
    ) { aggregates, metric, lang ->
        aggregates.map { agg ->
            val label = com.example.pos.core.util.DateTimeUtils.formatPeriodKeyForDisplay(agg.periodKey, lang)
            val value = when (metric) {
                AnalyticsMetric.ROUNDED_SALES -> agg.totalRoundedBdt
                AnalyticsMetric.RAW_SALES -> agg.totalRawPaisa / 100L
                AnalyticsMetric.TRANSACTION_COUNT -> agg.transactionCount.toLong()
            }
            com.example.pos.domain.model.ChartDataPoint(label = label, value = value)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Analytics Due & Payment Breakdown State
    private val _periodDueBdt = MutableStateFlow(0L)
    val periodDueBdt: StateFlow<Long> = _periodDueBdt.asStateFlow()

    val overallDueBdt: StateFlow<Long> = repository.getOverallOutstandingDue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _paymentBreakdown = MutableStateFlow<List<com.example.pos.data.db.PaymentMethodSummary>>(emptyList())
    val paymentBreakdown: StateFlow<List<com.example.pos.data.db.PaymentMethodSummary>> = _paymentBreakdown.asStateFlow()

    val totalCollectionBdt: StateFlow<Long> = _paymentBreakdown.map { list ->
        list.sumOf { it.totalPaisa } / 100L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // Detailed Receipt Popup Dialog State
    private val _isReceiptPopupOpen = MutableStateFlow(false)
    val isReceiptPopupOpen: StateFlow<Boolean> = _isReceiptPopupOpen.asStateFlow()

    private val _selectedInvoiceForReceipt = MutableStateFlow<InvoiceEntity?>(null)
    val selectedInvoiceForReceipt: StateFlow<InvoiceEntity?> = _selectedInvoiceForReceipt.asStateFlow()

    private val _receiptPopupLines = MutableStateFlow<List<InvoiceLineEntity>>(emptyList())
    val receiptPopupLines: StateFlow<List<InvoiceLineEntity>> = _receiptPopupLines.asStateFlow()

    private val _receiptPreviewOpen = MutableStateFlow(false)
    val receiptPreviewOpen: StateFlow<Boolean> = _receiptPreviewOpen.asStateFlow()

    private val _receiptPreviewText = MutableStateFlow("")
    val receiptPreviewText: StateFlow<String> = _receiptPreviewText.asStateFlow()

    private val _closeDetailsDialogEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeDetailsDialogEvent: SharedFlow<Unit> = _closeDetailsDialogEvent.asSharedFlow()

    private val _closeDetailsOnPrintSuccess = MutableStateFlow(false)
    val closeDetailsOnPrintSuccess: StateFlow<Boolean> = _closeDetailsOnPrintSuccess.asStateFlow()

    // UI Snackbars / Alerts
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Printing concurrency guard
    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

    // Navigation trigger post-preview
    private val _navigateHomeAfterPreview = MutableStateFlow(false)
    val navigateHomeAfterPreview: StateFlow<Boolean> = _navigateHomeAfterPreview.asStateFlow()

    fun resetNavigateHomeAfterPreview() {
        _navigateHomeAfterPreview.value = false
    }

    // Direct preview action dialog states (Edit metadata & Delete confirmation)
    private val _invoiceToEdit = MutableStateFlow<InvoiceEntity?>(null)
    val invoiceToEdit: StateFlow<InvoiceEntity?> = _invoiceToEdit.asStateFlow()

    private val _invoiceToDelete = MutableStateFlow<InvoiceEntity?>(null)
    val invoiceToDelete: StateFlow<InvoiceEntity?> = _invoiceToDelete.asStateFlow()

    fun openEditInvoice(invoice: InvoiceEntity) {
        _invoiceToEdit.value = invoice
    }

    fun closeEditInvoice() {
        _invoiceToEdit.value = null
    }

    fun openDeleteInvoice(invoice: InvoiceEntity) {
        _invoiceToDelete.value = invoice
    }

    fun closeDeleteInvoice() {
        _invoiceToDelete.value = null
    }

    // Transaction checkout in-flight guard
    private val _isProcessingOrder = MutableStateFlow(false)
    val isProcessingOrder: StateFlow<Boolean> = _isProcessingOrder.asStateFlow()

    // Printer hardware states
    private val _pairedPrinters = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val pairedPrinters: StateFlow<List<Pair<String, String>>> = _pairedPrinters.asStateFlow()

    val printerConnectionState: StateFlow<PrinterConnectionState> = BluetoothPrinterManager.connectionState
    val pairedPrinterDevices: StateFlow<List<BluetoothDeviceItem>> = BluetoothPrinterManager.pairedDevices
    val discoveredPrinterDevices: StateFlow<List<BluetoothDeviceItem>> = BluetoothPrinterManager.discoveredDevices
    val isBluetoothScanning: StateFlow<Boolean> = BluetoothPrinterManager.isScanning
    val isBluetoothEnabled: Boolean get() = BluetoothPrinterManager.isBluetoothEnabled
    val isBluetoothAvailable: Boolean get() = BluetoothPrinterManager.isBluetoothAvailable

    // Database Capacity & Storage Monitoring
    val supportedCapacity = 200_000

    val totalInvoiceCount: StateFlow<Int> = repository.totalInvoiceCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalInvoiceLinesCount: StateFlow<Int> = repository.totalInvoiceLinesCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalProductCount: StateFlow<Int> = repository.totalProductCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCustomerCount: StateFlow<Int> = repository.totalCustomerCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _databaseSizeBytes = MutableStateFlow(0L)
    val databaseSizeBytes: StateFlow<Long> = _databaseSizeBytes.asStateFlow()

    private val _isDeletingInvoice = MutableStateFlow(false)
    val isDeletingInvoice: StateFlow<Boolean> = _isDeletingInvoice.asStateFlow()

    // Session initialization & operator availability state
    private val _isSessionChecking = MutableStateFlow(true)
    val isSessionChecking: StateFlow<Boolean> = _isSessionChecking.asStateFlow()

    private val _operatorCount = MutableStateFlow(0)
    val operatorCount: StateFlow<Int> = _operatorCount.asStateFlow()

    private val _hasRegisteredOperators = MutableStateFlow(false)
    val hasRegisteredOperators: StateFlow<Boolean> = _hasRegisteredOperators.asStateFlow()

    // In-memory-only registration & one-time recovery confirmation state (never persisted in prefs/DB)
    private val _isRegistering = MutableStateFlow(false)
    val isRegistering: StateFlow<Boolean> = _isRegistering.asStateFlow()

    private val _pendingRecoveryCode = MutableStateFlow<String?>(null)
    val pendingRecoveryCode: StateFlow<String?> = _pendingRecoveryCode.asStateFlow()

    private val _pendingRecoveryOperatorName = MutableStateFlow<String?>(null)
    val pendingRecoveryOperatorName: StateFlow<String?> = _pendingRecoveryOperatorName.asStateFlow()

    // Login, Lockout & Offline Recovery UI states (transient in-memory only)
    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _loginLockoutSeconds = MutableStateFlow(0L)
    val loginLockoutSeconds: StateFlow<Long> = _loginLockoutSeconds.asStateFlow()

    private val _rememberedPhoneHint = MutableStateFlow("")
    val rememberedPhoneHint: StateFlow<String> = _rememberedPhoneHint.asStateFlow()

    private val _rememberMeHintEnabled = MutableStateFlow(false)
    val rememberMeHintEnabled: StateFlow<Boolean> = _rememberMeHintEnabled.asStateFlow()

    private val _isVerifyingRecovery = MutableStateFlow(false)
    val isVerifyingRecovery: StateFlow<Boolean> = _isVerifyingRecovery.asStateFlow()

    private val _isResettingPassword = MutableStateFlow(false)
    val isResettingPassword: StateFlow<Boolean> = _isResettingPassword.asStateFlow()

    private val _isRegeneratingRecoveryCode = MutableStateFlow(false)
    val isRegeneratingRecoveryCode: StateFlow<Boolean> = _isRegeneratingRecoveryCode.asStateFlow()

    // Strictly transient in-memory display buffer for regenerated recovery code (never persisted)
    private val _managedRecoveryCode = MutableStateFlow<String?>(null)
    val managedRecoveryCode: StateFlow<String?> = _managedRecoveryCode.asStateFlow()

    private val _loginBannerMessage = MutableStateFlow<String?>(null)
    val loginBannerMessage: StateFlow<String?> = _loginBannerMessage.asStateFlow()

    @Volatile
    private var cachedRememberedPhone: String = ""

    @Volatile
    private var cachedRememberMeSaved: Boolean = false

    // Employee Activity & Audit Trail State
    private val _auditOperatorFilter = MutableStateFlow("")
    val auditOperatorFilter: StateFlow<String> = _auditOperatorFilter.asStateFlow()

    private val _auditCategoryFilter = MutableStateFlow(AuditCategoryFilter.ALL)
    val auditCategoryFilter: StateFlow<AuditCategoryFilter> = _auditCategoryFilter.asStateFlow()

    private val _auditDateFilter = MutableStateFlow(AuditDateFilter.ALL)
    val auditDateFilter: StateFlow<AuditDateFilter> = _auditDateFilter.asStateFlow()

    private val _auditCustomStartDate = MutableStateFlow<Long?>(null)
    val auditCustomStartDate: StateFlow<Long?> = _auditCustomStartDate.asStateFlow()

    private val _auditCustomEndDate = MutableStateFlow<Long?>(null)
    val auditCustomEndDate: StateFlow<Long?> = _auditCustomEndDate.asStateFlow()

    private val _auditQuery = MutableStateFlow("")
    val auditQuery: StateFlow<String> = _auditQuery.asStateFlow()

    private val _auditCurrentPage = MutableStateFlow(0)
    val auditCurrentPage: StateFlow<Int> = _auditCurrentPage.asStateFlow()

    val auditPageSize = 30

    val auditOperators: StateFlow<List<AuditOperatorOption>> = repository.audit
        .getDistinctAuditOperators()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private data class AuditQueryParams(
        val operatorFilter: String,
        val categoryFilter: AuditCategoryFilter,
        val dateFilter: AuditDateFilter,
        val customStart: Long?,
        val customEnd: Long?,
        val query: String,
        val page: Int
    )

    private val auditQueryParamsFlow = combine(
        combine(_auditOperatorFilter, _auditCategoryFilter, _auditDateFilter) { op, cat, date ->
            Triple(op, cat, date)
        },
        combine(_auditCustomStartDate, _auditCustomEndDate, _auditQuery, _auditCurrentPage) { cs, ce, q, p ->
            listOf(cs, ce, q, p)
        }
    ) { (op, cat, date), extras ->
        AuditQueryParams(
            operatorFilter = op,
            categoryFilter = cat,
            dateFilter = date,
            customStart = extras[0] as Long?,
            customEnd = extras[1] as Long?,
            query = extras[2] as String,
            page = extras[3] as Int
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val auditLogs: StateFlow<List<AuditLogEntity>> = auditQueryParamsFlow
        .flatMapLatest { params ->
            val (startDate, endDate) = calculateAuditDateRange(
                params.dateFilter,
                params.customStart,
                params.customEnd
            )
            repository.audit.getAuditLogsPaged(
                operatorFilter = params.operatorFilter,
                categoryFilter = params.categoryFilter,
                query = params.query,
                startDate = startDate,
                endDate = endDate,
                limit = auditPageSize,
                offset = params.page * auditPageSize
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val auditTotalCount: StateFlow<Int> = auditQueryParamsFlow
        .flatMapLatest { params ->
            val (startDate, endDate) = calculateAuditDateRange(
                params.dateFilter,
                params.customStart,
                params.customEnd
            )
            repository.audit.getAuditLogsCount(
                operatorFilter = params.operatorFilter,
                categoryFilter = params.categoryFilter,
                query = params.query,
                startDate = startDate,
                endDate = endDate
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        // Observe settings changes for language and theme
        viewModelScope.launch {
            repository.appSettingsFlow.collect { settings ->
                if (settings != null) {
                    _language.value = settings.language
                    _themeMode.value = settings.themeMode
                }
            }
        }

        // Continuously mirror Room operator count changes
        viewModelScope.launch {
            repository.operatorCountFlow.collect { count ->
                _operatorCount.value = count
                _hasRegisteredOperators.value = count > 0
            }
        }

        // Check and restore active session AFTER DB initialization, then load initial POS state
        viewModelScope.launch {
            try {
                repository.checkAndRestoreSession()
                val initialCount = repository.getOperatorCount()
                _operatorCount.value = initialCount
                _hasRegisteredOperators.value = initialCount > 0
                refreshRememberedAuthHints()
                if (cachedRememberedPhone.isNotBlank()) {
                    _loginLockoutSeconds.value = runCatching {
                        repository.getLockoutRemainingSeconds(cachedRememberedPhone)
                    }.getOrDefault(0L)
                }
            } finally {
                _isSessionChecking.value = false
            }
            refreshNextOrderId()
            refreshHistory()
            refreshPairedPrinters()
            refreshDatabaseSize()
        }
    }

    private suspend fun refreshRememberedAuthHints() {
        val phone = runCatching { repository.getRememberedPhone() }.getOrDefault("")
        val remember = runCatching { repository.isRememberMeSaved() }.getOrDefault(false)
        cachedRememberedPhone = phone
        cachedRememberMeSaved = remember
        _rememberedPhoneHint.value = phone
        _rememberMeHintEnabled.value = remember
    }

    fun getRememberedPhone(): String {
        return cachedRememberedPhone
    }

    fun isRememberMeSaved(): Boolean {
        return cachedRememberMeSaved
    }

    fun clearLoginBanner() {
        _loginBannerMessage.value = null
    }

    fun refreshLockoutStatus(phone: String) {
        val normalized = AuthInputValidator.normalizePhone(phone)
        if (normalized.isBlank()) {
            _loginLockoutSeconds.value = 0L
            return
        }
        viewModelScope.launch {
            val remaining = runCatching {
                repository.getLockoutRemainingSeconds(normalized)
            }.getOrDefault(0L)
            _loginLockoutSeconds.value = remaining
        }
    }

    fun tickOrRefreshLockout(phone: String) {
        val current = _loginLockoutSeconds.value
        if (current > 1L) {
            _loginLockoutSeconds.value = current - 1L
        } else {
            refreshLockoutStatus(phone)
        }
    }

    fun refreshDatabaseSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _databaseSizeBytes.value = repository.getDatabaseSizeBytes(getApplication())
        }
    }

    fun refreshPairedPrinters() {
        BluetoothPrinterManager.refreshPairedDevices(getApplication())
        _pairedPrinters.value = EscPosPrinterEngine.getPairedDevices()
    }

    fun startBluetoothScanning() {
        BluetoothPrinterManager.startDiscovery(getApplication())
    }

    fun stopBluetoothScanning() {
        BluetoothPrinterManager.stopDiscovery(getApplication())
    }

    fun connectPrinter(address: String, name: String? = null) {
        viewModelScope.launch {
            val res = BluetoothPrinterManager.connect(address, name)
            if (res.isSuccess) {
                val isBn = language.value == "bn"
                _toastMessage.value = if (isBn) "প্রিন্টার সংযুক্ত: ${name ?: address}" else "Printer connected: ${name ?: address}"
            } else {
                _toastMessage.value = res.exceptionOrNull()?.message ?: "Failed to connect"
            }
        }
    }

    fun disconnectPrinter() {
        viewModelScope.launch {
            BluetoothPrinterManager.disconnect()
            val isBn = language.value == "bn"
            _toastMessage.value = if (isBn) "প্রিন্টার সংযোগ বিচ্ছিন্ন করা হয়েছে" else "Printer disconnected"
        }
    }

    fun refreshNextOrderId() {
        viewModelScope.launch {
            _draftOrderId.value = repository.getNextOrderId()
        }
    }

    fun setCustomerInfo(info: String) {
        _customerInfo.value = info
        val validation = com.example.pos.domain.CustomerValidator.validate(info)
        _customerInfoError.value = validation.errorMessage

        // Query customer suggestions asynchronously
        val trimmed = info.trim()
        if (trimmed.length >= 2) {
            viewModelScope.launch {
                _customerSuggestions.value = repository.searchCustomersDirect(trimmed, 6)
            }
        } else {
            _customerSuggestions.value = emptyList()
        }
    }

    fun selectCustomerSuggestion(customer: CustomerEntity) {
        _customerInfo.value = customer.customerInfo
        _customerInfoError.value = null
        _customerSuggestions.value = emptyList()
    }

    fun clearCustomerSuggestions() {
        _customerSuggestions.value = emptyList()
    }

    fun saveCustomerProfile(
        customerInfo: String,
        name: String,
        phone: String,
        address: String,
        notes: String,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val validation = com.example.pos.domain.CustomerValidator.validate(customerInfo)
        if (!validation.isValid || validation.sanitizedValue.isBlank()) {
            onComplete(false, validation.errorMessage ?: "Invalid customer info")
            return
        }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val existing = repository.getCustomerByInfo(validation.sanitizedValue)
                val customer = CustomerEntity(
                    customerInfo = validation.sanitizedValue,
                    customerName = name.trim(),
                    phoneNumber = phone.trim(),
                    address = address.trim(),
                    notes = notes.trim(),
                    totalOrders = existing?.totalOrders ?: 0,
                    totalSpentPaisa = existing?.totalSpentPaisa ?: 0L,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                repository.saveCustomer(customer)
                _toastMessage.value = "Customer saved successfully"
                onComplete(true, null)
            } catch (e: PosErrorException) {
                val msg = if (e.posError is PosError.Unauthenticated) {
                    Localization.get(Localization.Key.ERR_AUTH_REQUIRED_ACTION, _language.value)
                } else {
                    e.message
                }
                _toastMessage.value = msg
                onComplete(false, msg)
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }

    fun addItemFromCatalog(product: ProductEntity) {
        val qtyStr = "1"
        val qty = FinancialCalculationEngine.parseQuantity(qtyStr)
        val lineTotal = FinancialCalculationEngine.calculateLineTotal(product.defaultUnitPricePaisa, qty)
        val priceBdtStr = FinancialCalculationEngine.formatPaisaToBdtInput(product.defaultUnitPricePaisa)

        // Select the appropriate display name based on active app language
        val snapshotItemName = product.getDisplayName(_language.value)

        val newItem = DraftInvoiceItem(
            itemId = product.itemId,
            itemName = snapshotItemName,
            itemType = product.itemType,
            unit = product.unit,
            quantityStr = qtyStr,
            unitPriceBdtStr = priceBdtStr,
            unitPricePaisa = product.defaultUnitPricePaisa,
            lineTotalPaisa = lineTotal,
            lineTotalBdtStr = FinancialCalculationEngine.formatPaisaToBdtInput(lineTotal),
            calculationDriver = DraftCalculationDriver.QUANTITY
        )
        _draftItems.value = _draftItems.value + newItem
    }

    fun addCustomItem(name: String, priceBdtText: String, qtyText: String, unitText: String, itemType: String = "Product") {
        val cleanName = name.trim().ifEmpty { "Custom Service" }
        val pricePaisa = FinancialCalculationEngine.parseBdtToPaisa(priceBdtText).coerceAtLeast(0L)
        val cleanQty = qtyText.trim().ifEmpty { "1" }
        val qty = FinancialCalculationEngine.parseQuantity(cleanQty)
        val cleanUnit = unitText.trim().ifEmpty { "Unit" }
        val cleanType = itemType.trim().ifEmpty { "Product" }
        val lineTotal = FinancialCalculationEngine.calculateLineTotal(pricePaisa, qty)

        val newItem = DraftInvoiceItem(
            itemId = "custom-" + UUID.randomUUID().toString().take(6),
            itemName = cleanName,
            itemType = cleanType,
            unit = cleanUnit,
            quantityStr = cleanQty,
            unitPriceBdtStr = FinancialCalculationEngine.formatPaisaToBdtInput(pricePaisa),
            unitPricePaisa = pricePaisa,
            lineTotalPaisa = lineTotal,
            lineTotalBdtStr = FinancialCalculationEngine.formatPaisaToBdtInput(lineTotal),
            calculationDriver = DraftCalculationDriver.QUANTITY
        )
        _draftItems.value = _draftItems.value + newItem
    }

    fun updateItemQuantity(tempId: String, newQtyText: String) {
        val currentList = _draftItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.tempId == tempId }
        if (index != -1) {
            val item = currentList[index]
            val qty = FinancialCalculationEngine.parseQuantity(newQtyText)
            val newTotal = FinancialCalculationEngine.calculateLineTotal(item.unitPricePaisa, qty)
            currentList[index] = item.copy(
                quantityStr = newQtyText,
                lineTotalPaisa = newTotal,
                lineTotalBdtStr = FinancialCalculationEngine.formatPaisaToBdtInput(newTotal),
                calculationDriver = DraftCalculationDriver.QUANTITY
            )
            _draftItems.value = currentList
        }
    }

    fun updateItemTotalAmount(tempId: String, newTotalBdtText: String) {
        val currentList = _draftItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.tempId == tempId }
        if (index != -1) {
            val item = currentList[index]
            if (!FinancialCalculationEngine.isLiquidOrFuelType(item.itemType)) return

            val newTotalPaisa = FinancialCalculationEngine.parseBdtToPaisa(newTotalBdtText).coerceAtLeast(0L)
            val updatedQtyStr = if (item.unitPricePaisa > 0L) {
                FinancialCalculationEngine.formatQuantity(
                    FinancialCalculationEngine.calculateQuantityFromTotal(newTotalPaisa, item.unitPricePaisa)
                )
            } else {
                if (newTotalPaisa == 0L) "0" else item.quantityStr
            }

            currentList[index] = item.copy(
                quantityStr = updatedQtyStr,
                lineTotalPaisa = newTotalPaisa,
                lineTotalBdtStr = newTotalBdtText,
                calculationDriver = DraftCalculationDriver.TOTAL_AMOUNT
            )
            _draftItems.value = currentList
        }
    }

    fun updateItemUnitPrice(tempId: String, newPriceBdtText: String) {
        val currentList = _draftItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.tempId == tempId }
        if (index != -1) {
            val item = currentList[index]
            val newPricePaisa = FinancialCalculationEngine.parseBdtToPaisa(newPriceBdtText).coerceAtLeast(0L)
            val isLiquid = FinancialCalculationEngine.isLiquidOrFuelType(item.itemType)

            if (isLiquid && item.calculationDriver == DraftCalculationDriver.TOTAL_AMOUNT) {
                val updatedQtyStr = if (newPricePaisa > 0L) {
                    FinancialCalculationEngine.formatQuantity(
                        FinancialCalculationEngine.calculateQuantityFromTotal(item.lineTotalPaisa, newPricePaisa)
                    )
                } else {
                    if (item.lineTotalPaisa == 0L) "0" else item.quantityStr
                }
                currentList[index] = item.copy(
                    unitPriceBdtStr = newPriceBdtText,
                    unitPricePaisa = newPricePaisa,
                    quantityStr = updatedQtyStr
                )
            } else {
                val qty = FinancialCalculationEngine.parseQuantity(item.quantityStr)
                val newTotal = FinancialCalculationEngine.calculateLineTotal(newPricePaisa, qty)
                currentList[index] = item.copy(
                    unitPriceBdtStr = newPriceBdtText,
                    unitPricePaisa = newPricePaisa,
                    lineTotalPaisa = newTotal,
                    lineTotalBdtStr = FinancialCalculationEngine.formatPaisaToBdtInput(newTotal)
                )
            }
            _draftItems.value = currentList
        }
    }

    fun removeItem(tempId: String) {
        _draftItems.value = _draftItems.value.filterNot { it.tempId == tempId }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val current = _draftItems.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val moved = current.removeAt(fromIndex)
            current.add(toIndex, moved)
            _draftItems.value = current
        }
    }

    fun clearDraft() {
        _draftItems.value = emptyList()
        _customerInfo.value = ""
        _draftPaidAmountBdt.value = ""
        refreshNextOrderId()
    }

    fun generateCurrentReceiptText(): String {
        val header = companyHeader.value
        val settings = appSettings.value
        val lines = _draftItems.value.mapIndexed { idx, item ->
            InvoiceLineEntity(
                orderId = _draftOrderId.value,
                itemId = item.itemId,
                itemName = item.itemName,
                itemType = item.itemType,
                unit = item.unit,
                quantityStr = item.quantityStr,
                unitPricePaisa = item.unitPricePaisa,
                lineTotalPaisa = item.lineTotalPaisa,
                sortOrder = idx + 1
            )
        }
        val rawTotal = FinancialCalculationEngine.calculateRawTotal(lines.map { it.lineTotalPaisa })
        val roundedTotal = FinancialCalculationEngine.calculateRoundedTotalBdt(rawTotal)

        val operator = activeOperator.value
        val invoice = InvoiceEntity(
            orderId = _draftOrderId.value,
            dateTime = System.currentTimeMillis(),
            operatorId = operator?.userId ?: "",
            operatorName = operator?.operatorName ?: "",
            customerInfo = _customerInfo.value.ifBlank { "N/A" },
            rawTotalPaisa = rawTotal,
            roundedTotalBdt = roundedTotal,
            isPaid = isDraftFullPaid.value,
            paidAmountBdt = effectiveDraftPaidBdt.value,
            footerNote = "${settings?.footerNoteLine1}\n${settings?.footerNoteLine2}"
        )
        return EscPosPrinterEngine.generateReceiptText(
            header = header,
            invoice = invoice,
            lines = lines,
            paperSize = settings?.paperSize ?: "58mm",
            footerLine1 = settings?.footerNoteLine1 ?: "",
            footerLine2 = settings?.footerNoteLine2 ?: "",
            isFullPaid = isDraftFullPaid.value,
            lang = _language.value
        )
    }

    fun openReceiptPreview() {
        if (_draftItems.value.isEmpty()) {
            _toastMessage.value = Localization.get(Localization.Key.EMPTY_INVOICE_PROMPT, _language.value)
            return
        }
        _receiptPreviewText.value = generateCurrentReceiptText()
        _receiptPreviewOpen.value = true
    }

    fun checkoutPaid(printDirectly: Boolean = true) {
        val total = roundedTotalBdt.value
        checkoutOrder(isPaid = true, paidAmountBdt = total, printDirectly = printDirectly)
    }

    fun checkoutDue(depositBdt: Long, printDirectly: Boolean = true) {
        val total = roundedTotalBdt.value
        val validDeposit = depositBdt.coerceIn(0L, total)
        val isPaid = (validDeposit == total)
        checkoutOrder(isPaid = isPaid, paidAmountBdt = validDeposit, printDirectly = printDirectly)
    }

    fun checkoutAndSave(isPaid: Boolean = true, paidAmountBdt: Long? = null, printDirectly: Boolean = true) {
        val total = roundedTotalBdt.value
        val actualPaid = paidAmountBdt ?: if (isPaid) total else 0L
        val effectiveIsPaid = (actualPaid == total)
        checkoutOrder(isPaid = effectiveIsPaid, paidAmountBdt = actualPaid, printDirectly = printDirectly)
    }

    private fun checkoutOrder(
        isPaid: Boolean,
        paidAmountBdt: Long,
        printDirectly: Boolean
    ) {
        if (_draftItems.value.isEmpty()) {
            _toastMessage.value = Localization.get(Localization.Key.EMPTY_INVOICE_PROMPT, _language.value)
            return
        }

        val currentOperator = activeOperator.value
        if (currentOperator == null) {
            val isBn = _language.value == "bn"
            _toastMessage.value = if (isBn) {
                "অর্ডার সম্পন্ন করতে অনুগ্রহ করে লগইন করুন।"
            } else {
                "Please sign in as an operator before completing checkout."
            }
            return
        }

        if (_isProcessingOrder.value) {
            return
        }
        _isProcessingOrder.value = true

        viewModelScope.launch {
            try {
                val operator = activeOperator.value ?: repository.getActiveOperator()
                if (operator == null) {
                    val isBn = _language.value == "bn"
                    _toastMessage.value = if (isBn) {
                        "সক্রিয় অপারেটর সেশন নেই। অনুগ্রহ করে পুনরায় লগইন করুন।"
                    } else {
                        "Active operator session required to save invoice."
                    }
                    return@launch
                }

                val orderId = _draftOrderId.value
                val now = System.currentTimeMillis()
                val settings = appSettings.value

                val header = companyHeader.value
                val branchCode = header?.branchNumber?.ifBlank { "01" } ?: "01"
                val globalId = com.example.pos.core.util.IdGenerator.generateGlobalTransactionId()

                val lines = _draftItems.value.mapIndexed { idx, it ->
                    InvoiceLineEntity(
                        orderId = orderId,
                        itemId = it.itemId,
                        itemName = it.itemName,
                        itemType = it.itemType,
                        unit = it.unit,
                        quantityStr = it.quantityStr,
                        unitPricePaisa = it.unitPricePaisa,
                        lineTotalPaisa = it.lineTotalPaisa,
                        sortOrder = idx + 1
                    )
                }
                val rawTotal = FinancialCalculationEngine.calculateRawTotal(lines.map { it.lineTotalPaisa })
                val roundedTotal = FinancialCalculationEngine.calculateRoundedTotalBdt(rawTotal)

                val validatedPaidAmountBdt = paidAmountBdt.coerceIn(0L, roundedTotal)
                val finalIsPaid = (validatedPaidAmountBdt == roundedTotal)

                val invoiceTemplate = InvoiceEntity(
                    orderId = orderId,
                    globalTransactionId = globalId,
                    branchCode = branchCode,
                    dateTime = now,
                    operatorId = operator.userId,
                    operatorName = operator.operatorName,
                    customerInfo = _customerInfo.value.ifBlank { "01700000000" },
                    rawTotalPaisa = rawTotal,
                    roundedTotalBdt = roundedTotal,
                    isPaid = finalIsPaid,
                    paidAmountBdt = validatedPaidAmountBdt,
                    footerNote = "${settings?.footerNoteLine1}\n${settings?.footerNoteLine2}",
                    createdAt = now,
                    updatedAt = now
                )

                val customerToRecord = _customerInfo.value.trim().ifBlank { "01700000000" }
                val checkoutResult = repository.checkoutOrderAtomic(
                    invoiceTemplate = invoiceTemplate,
                    lines = lines,
                    customerInfoForCrm = customerToRecord
                )

                when (checkoutResult) {
                    is PosResult.Error -> {
                        val isBn = _language.value == "bn"
                        _toastMessage.value = when (checkoutResult.error) {
                            is PosError.Unauthenticated -> Localization.get(Localization.Key.ERR_AUTH_REQUIRED_ACTION, _language.value)
                            else -> if (isBn) "লেনদেন ব্যর্থ: ${checkoutResult.error.message}" else "Transaction failed: ${checkoutResult.error.message}"
                        }
                    }
                    is PosResult.Success -> {
                        val savedInvoice = checkoutResult.data
                        val paperSize = settings?.paperSize ?: "58mm"
                        val receiptText = ThermalReceiptFormatter.generateReceiptText(
                            header = companyHeader.value,
                            invoice = savedInvoice,
                            lines = lines,
                            paperSize = paperSize,
                            footerLine1 = settings?.footerNoteLine1 ?: "",
                            footerLine2 = settings?.footerNoteLine2 ?: "",
                            isFullPaid = finalIsPaid,
                            lang = _language.value
                        )

                        // Mandatory Invoice Preview: Open preview after atomic save; do NOT print automatically!
                        _selectedInvoiceForReceipt.value = savedInvoice
                        _receiptPopupLines.value = lines
                        _receiptPreviewText.value = receiptText
                        _receiptPreviewOpen.value = true
                        _isReceiptPopupOpen.value = true
                        _navigateHomeAfterPreview.value = true

                        val isBn = _language.value == "bn"
                        _toastMessage.value = if (isBn) "ইনভয়েস সফলভাবে সংরক্ষিত হয়েছে!" else "Invoice saved successfully!"

                        // Clear draft and generate new order ID for next sale
                        _draftItems.value = emptyList()
                        _customerInfo.value = ""
                        _draftPaidAmountBdt.value = ""
                        refreshNextOrderId()
                        refreshHistory()
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Transaction failed: ${e.message}"
            } finally {
                _isProcessingOrder.value = false
            }
        }
    }

    private var historyInvoicesJob: Job? = null
    private var historyCountJob: Job? = null
    private var historyAggJob: Job? = null
    private var historyTopItemsJob: Job? = null
    private var historyAnalyticsJob: Job? = null
    private var historyPeriodDueJob: Job? = null
    private var historyPaymentBreakdownJob: Job? = null

    // Sales History & Reports
    fun setHistoryQuery(query: String) {
        _historyQuery.value = query
        _currentPage.value = 0
        refreshHistory()
        updateOrderIdSuggestions(query)
    }

    fun setOrderIdFilter(orderId: String) {
        _historyOrderIdFilter.value = orderId
        _currentPage.value = 0
        refreshHistory()
        updateOrderIdSuggestions(orderId)
    }

    fun updateOrderIdSuggestions(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _orderIdSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val matches = repository.getMatchingOrderIds(trimmed, limit = 8)
                _orderIdSuggestions.value = matches
            } catch (e: Exception) {
                _orderIdSuggestions.value = emptyList()
            }
        }
    }

    fun updateInvoiceDetails(orderId: String, customerInfo: String, footerNote: String) {
        viewModelScope.launch {
            try {
                when (
                    val result = repository.updateInvoiceCustomerAndNoteAudited(
                        orderId = orderId,
                        customerInfo = customerInfo.trim(),
                        footerNote = footerNote.trim()
                    )
                ) {
                    is PosResult.Error -> {
                        val msg = if (result.error is PosError.Unauthenticated) {
                            Localization.get(Localization.Key.ERR_AUTH_REQUIRED_ACTION, _language.value)
                        } else {
                            "Update failed: ${result.error.message}"
                        }
                        _toastMessage.value = msg
                    }
                    is PosResult.Success -> {
                        if (result.data > 0) {
                            val isBn = language.value == "bn"
                            _toastMessage.value = if (isBn) "ভাউচারের বিবরণ সফলভাবে আপডেট করা হয়েছে।" else "Voucher details updated successfully."
                            val freshInv = repository.getInvoiceById(orderId)
                            val curSelected = _selectedInvoiceForReceipt.value
                            if (curSelected != null && curSelected.orderId == orderId) {
                                val updatedInv = freshInv ?: curSelected.copy(
                                    customerInfo = customerInfo.trim(),
                                    footerNote = footerNote.trim(),
                                    updatedByOperatorId = activeOperator.value?.userId,
                                    updatedByOperatorName = activeOperator.value?.operatorName,
                                    updatedAt = System.currentTimeMillis()
                                )
                                _selectedInvoiceForReceipt.value = updatedInv
                                val header = companyHeader.value
                                val settings = appSettings.value
                                val paperSize = settings?.paperSize ?: "58mm"
                                val text = ThermalReceiptFormatter.generateReceiptText(
                                    header = header,
                                    invoice = updatedInv,
                                    lines = _receiptPopupLines.value,
                                    paperSize = paperSize,
                                    footerLine1 = settings?.footerNoteLine1 ?: "",
                                    footerLine2 = settings?.footerNoteLine2 ?: "",
                                    isFullPaid = updatedInv.isPaid,
                                    lang = _language.value
                                )
                                _receiptPreviewText.value = text
                            }
                            refreshHistory()
                        }
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Update failed: ${e.message}"
            }
        }
    }

    fun settleInvoiceDue(
        orderId: String,
        additionalPaymentBdt: Long,
        method: String = "CASH",
        openPreviewAfterSettlement: Boolean = true,
        onSuccess: ((InvoiceEntity) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                when (val result = repository.settleInvoiceDue(orderId, additionalPaymentBdt, method)) {
                    is PosResult.Success -> {
                        val isBn = language.value == "bn"
                        _toastMessage.value = if (isBn) "বকেয়া সফলভাবে পরিশোধ করা হয়েছে।" else "Due settled successfully."
                        refreshHistory()
                        if (openPreviewAfterSettlement) {
                            openInvoicePreview(result.data, closeDetailsOnPrint = true)
                        }
                        onSuccess?.invoke(result.data)
                    }
                    is PosResult.Error -> {
                        val isBn = language.value == "bn"
                        _toastMessage.value = if (isBn) "বকেয়া পরিশোধ ব্যর্থ হয়েছে: ${result.error.message}" else "Settlement failed: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Payment settlement failed: ${e.message}"
            }
        }
    }

    fun setCustomerFilter(customer: String) {
        _historyCustomerFilter.value = customer
        _currentPage.value = 0
        refreshHistory()
    }

    fun setDateFilter(filter: DateFilterMode) {
        _dateFilter.value = filter
        _currentPage.value = 0
        refreshHistory()
    }

    fun setCustomDateRange(startDate: Long, endDate: Long? = null) {
        val calStart = Calendar.getInstance().apply {
            timeInMillis = startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val actualStart = calStart.timeInMillis

        val actualEnd = if (endDate != null) {
            val calEnd = Calendar.getInstance().apply {
                timeInMillis = endDate
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            if (calEnd.timeInMillis < actualStart) {
                actualStart + 86400000L - 1
            } else {
                calEnd.timeInMillis
            }
        } else {
            actualStart + 86400000L - 1
        }

        _customStartDate.value = actualStart
        _customEndDate.value = actualEnd
        _dateFilter.value = DateFilterMode.CUSTOM
        _currentPage.value = 0
        refreshHistory()
    }

    fun setHistorySortOption(sort: HistorySortOption) {
        _historySortOption.value = sort
        _currentPage.value = 0
        refreshHistory()
    }

    fun clearAllFilters() {
        _historyQuery.value = ""
        _historyOrderIdFilter.value = ""
        _historyCustomerFilter.value = ""
        _orderIdSuggestions.value = emptyList()
        _dateFilter.value = DateFilterMode.ALL
        _customStartDate.value = null
        _customEndDate.value = null
        _historySortOption.value = HistorySortOption.DATE_DESC
        _currentPage.value = 0
        refreshHistory()
    }

    fun setPageSize(size: Int) {
        if (size > 0 && size != _pageSize.value) {
            _pageSize.value = size
            _currentPage.value = 0
            refreshHistory()
        }
    }

    fun nextPage() {
        val total = _totalFilteredCount.value
        val nextOffset = (_currentPage.value + 1) * _pageSize.value
        if (nextOffset < total) {
            _currentPage.value = _currentPage.value + 1
            fetchInvoicesPage()
        }
    }

    fun prevPage() {
        if (_currentPage.value > 0) {
            _currentPage.value = _currentPage.value - 1
            fetchInvoicesPage()
        }
    }

    fun goToPage(page: Int) {
        val total = _totalFilteredCount.value
        val maxPage = if (total == 0) 0 else (total - 1) / _pageSize.value
        val target = page.coerceIn(0, maxPage)
        if (target != _currentPage.value) {
            _currentPage.value = target
            fetchInvoicesPage()
        }
    }

    private fun fetchInvoicesPage() {
        val (startDate, endDate) = calculateDateRange(_dateFilter.value, _customStartDate.value, _customEndDate.value)
        val query = _historyQuery.value
        val orderId = _historyOrderIdFilter.value
        val customer = _historyCustomerFilter.value
        val sort = _historySortOption.value
        val size = _pageSize.value
        val offset = _currentPage.value * size

        _isLoadingHistory.value = true
        historyInvoicesJob?.cancel()
        historyInvoicesJob = viewModelScope.launch {
            repository.getInvoicesPaged(query, orderId, customer, startDate, endDate, sort, size, offset)
                .collect { list ->
                    _historyInvoices.value = list
                    _isLoadingHistory.value = false
                    if (list.isNotEmpty()) {
                        val ids = list.map { it.orderId }
                        val lines = repository.getLinesForOrders(ids)
                        val grouped = lines.groupBy { it.orderId }
                        _orderLinesCache.value = _orderLinesCache.value + grouped
                    }
                }
        }
    }

    fun refreshHistory() {
        val (startDate, endDate) = calculateDateRange(_dateFilter.value, _customStartDate.value, _customEndDate.value)
        val query = _historyQuery.value
        val orderId = _historyOrderIdFilter.value
        val customer = _historyCustomerFilter.value
        val sort = _historySortOption.value
        val size = _pageSize.value
        val offset = _currentPage.value * size

        _isLoadingHistory.value = true

        historyInvoicesJob?.cancel()
        historyInvoicesJob = viewModelScope.launch {
            repository.getInvoicesPaged(query, orderId, customer, startDate, endDate, sort, size, offset)
                .collect { list ->
                    _historyInvoices.value = list
                    _isLoadingHistory.value = false
                    if (list.isNotEmpty()) {
                        val ids = list.map { it.orderId }
                        val lines = repository.getLinesForOrders(ids)
                        val grouped = lines.groupBy { it.orderId }
                        _orderLinesCache.value = _orderLinesCache.value + grouped
                    }
                }
        }

        historyCountJob?.cancel()
        historyCountJob = viewModelScope.launch {
            repository.getInvoiceCount(query, orderId, customer, startDate, endDate).collect { count ->
                _totalFilteredCount.value = count
            }
        }

        historyAggJob?.cancel()
        historyAggJob = viewModelScope.launch {
            repository.getAggregatedSales(query, orderId, customer, startDate, endDate).collect { agg ->
                _aggregatedOrders.value = agg.totalOrders
                _aggregatedRawPaisa.value = agg.totalRawPaisa
                _aggregatedRoundedBdt.value = agg.totalRoundedBdt
            }
        }

        historyTopItemsJob?.cancel()
        historyTopItemsJob = viewModelScope.launch {
            repository.getTopSellingItems(query, orderId, customer, startDate, endDate, 5).collect { topList ->
                _topSellingItems.value = topList
            }
        }

        historyAnalyticsJob?.cancel()
        historyAnalyticsJob = viewModelScope.launch {
            val flow = when (_analyticsGrouping.value) {
                AnalyticsGrouping.DAY -> repository.getSalesByDay(query, orderId, customer, startDate, endDate)
                AnalyticsGrouping.MONTH -> repository.getSalesByMonth(query, orderId, customer, startDate, endDate)
                AnalyticsGrouping.YEAR -> repository.getSalesByYear(query, orderId, customer, startDate, endDate)
            }
            flow.collect { aggregates ->
                _analyticsDataPoints.value = aggregates
            }
        }

        historyPeriodDueJob?.cancel()
        historyPeriodDueJob = viewModelScope.launch {
            repository.getPeriodDue(query, orderId, customer, startDate, endDate).collect { due ->
                _periodDueBdt.value = due
            }
        }

        historyPaymentBreakdownJob?.cancel()
        historyPaymentBreakdownJob = viewModelScope.launch {
            repository.getPaymentMethodBreakdownFiltered(startDate, endDate).collect { breakdown ->
                _paymentBreakdown.value = breakdown
            }
        }
    }

    fun setAnalyticsGrouping(grouping: AnalyticsGrouping) {
        _analyticsGrouping.value = grouping
        refreshHistory()
    }

    fun setAnalyticsMetric(metric: AnalyticsMetric) {
        _analyticsMetric.value = metric
    }

    private fun calculateDateRange(
        filter: DateFilterMode,
        customStart: Long? = null,
        customEnd: Long? = null
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        return when (filter) {
            DateFilterMode.ALL -> Pair(0L, now + 86400000L)
            DateFilterMode.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, start + 86400000L - 1)
            }
            DateFilterMode.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, start + 86400000L - 1)
            }
            DateFilterMode.WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now + 86400000L)
            }
            DateFilterMode.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now + 86400000L)
            }
            DateFilterMode.SIX_MONTHS -> {
                cal.add(Calendar.MONTH, -6)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now + 86400000L)
            }
            DateFilterMode.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now + 86400000L)
            }
            DateFilterMode.CUSTOM -> {
                val start = customStart ?: 0L
                val end = customEnd ?: if (customStart != null) (customStart + 86400000L - 1) else (now + 86400000L)
                Pair(start, end)
            }
        }
    }

    fun toggleOrderExpansion(orderId: String) {
        if (_expandedOrderId.value == orderId) {
            _expandedOrderId.value = null
            _expandedLines.value = emptyList()
        } else {
            _expandedOrderId.value = orderId
            val cached = _orderLinesCache.value[orderId]
            if (cached != null && cached.isNotEmpty()) {
                _expandedLines.value = cached
            } else {
                viewModelScope.launch {
                    val lines = repository.getLinesForOrderDirect(orderId)
                    _expandedLines.value = lines
                    _orderLinesCache.value = _orderLinesCache.value + (orderId to lines)
                }
            }
        }
    }

    fun fetchLinesForOrder(orderId: String) {
        if (_orderLinesCache.value[orderId] != null) return
        viewModelScope.launch {
            val lines = repository.getLinesForOrderDirect(orderId)
            _orderLinesCache.value = _orderLinesCache.value + (orderId to lines)
        }
    }

    fun openInvoicePreview(invoice: InvoiceEntity, closeDetailsOnPrint: Boolean = false) {
        _closeDetailsOnPrintSuccess.value = closeDetailsOnPrint
        viewModelScope.launch {
            val freshInvoice = repository.getInvoiceById(invoice.orderId) ?: invoice
            val lines = repository.getLinesForOrderDirect(freshInvoice.orderId)
            val header = companyHeader.value
            val settings = appSettings.value
            val paperSize = settings?.paperSize ?: "58mm"
            val text = ThermalReceiptFormatter.generateReceiptText(
                header = header,
                invoice = freshInvoice,
                lines = lines,
                paperSize = paperSize,
                footerLine1 = settings?.footerNoteLine1 ?: "",
                footerLine2 = settings?.footerNoteLine2 ?: "",
                isFullPaid = freshInvoice.isPaid,
                lang = _language.value
            )
            _selectedInvoiceForReceipt.value = freshInvoice
            _receiptPopupLines.value = lines
            _receiptPreviewText.value = text
            _receiptPreviewOpen.value = true
            _isReceiptPopupOpen.value = true
            _navigateHomeAfterPreview.value = false
        }
    }

    fun openReceiptPopup(invoice: InvoiceEntity) {
        openInvoicePreview(invoice)
    }

    fun closeReceiptPreview() {
        _receiptPreviewOpen.value = false
        _isReceiptPopupOpen.value = false
        _selectedInvoiceForReceipt.value = null
        _receiptPopupLines.value = emptyList()
        _receiptPreviewText.value = ""
    }

    fun closeReceiptPopup() {
        closeReceiptPreview()
    }

    fun reprintInvoice(invoice: InvoiceEntity) {
        // Mandatory preview before printing: open invoice preview first
        openInvoicePreview(invoice)
    }

    fun printInvoiceFromPreview(
        targetInvoice: InvoiceEntity? = _selectedInvoiceForReceipt.value,
        onSuccess: (() -> Unit)? = null
    ) {
        val invoice = targetInvoice ?: return
        if (_isPrinting.value) return
        _isPrinting.value = true

        viewModelScope.launch {
            try {
                val settings = appSettings.value
                val address = settings?.bluetoothPrinterAddress
                val lines = if (_receiptPopupLines.value.isNotEmpty() && _selectedInvoiceForReceipt.value?.orderId == invoice.orderId) {
                    _receiptPopupLines.value
                } else {
                    repository.getLinesForOrderDirect(invoice.orderId)
                }
                val isBn = _language.value == "bn"

                if (address != null) {
                    val header = companyHeader.value
                    val paperSize = settings?.paperSize ?: "58mm"
                    val autoCutter = settings?.autoCutterEnabled ?: true
                    val logoBitmap = EscPosPrinterEngine.loadLogoBitmapForThermal(
                        getApplication(),
                        header?.logoUri,
                        paperSize
                    )
                    val payload = ThermalReceiptFormatter.buildEscPosReceipt(
                        header = header,
                        invoice = invoice,
                        lines = lines,
                        paperSize = paperSize,
                        autoCutter = autoCutter,
                        footerLine1 = settings?.footerNoteLine1 ?: "",
                        footerLine2 = settings?.footerNoteLine2 ?: "",
                        logoBitmap = logoBitmap,
                        isFullPaid = invoice.isPaid,
                        lang = _language.value
                    )
                    val res = BluetoothPrinterManager.print(address, settings?.bluetoothPrinterName, payload)
                    if (res.isSuccess) {
                        _toastMessage.value = if (isBn) "ভাউচার #${invoice.orderId} প্রিন্ট সফল!" else "Receipt printed for #${invoice.orderId}!"
                        closeReceiptPreview()
                        _closeDetailsOnPrintSuccess.value = false
                        _closeDetailsDialogEvent.tryEmit(Unit)
                        onSuccess?.invoke()
                    } else {
                        _toastMessage.value = if (isBn) "প্রিন্টার ত্রুটি: ${res.exceptionOrNull()?.message}" else "Printer error: ${res.exceptionOrNull()?.message}"
                    }
                } else {
                    _toastMessage.value = if (isBn) "কোনো ব্লুটুথ প্রিন্টার যুক্ত নেই।" else "No Bluetooth printer configured."
                }
            } catch (e: Exception) {
                _toastMessage.value = "Print error: ${e.message}"
            } finally {
                _isPrinting.value = false
            }
        }
    }

    fun getPaymentsForOrder(orderId: String): Flow<List<PaymentRecordEntity>> {
        return repository.getPaymentsForOrder(orderId)
    }

    fun deleteInvoice(orderId: String, onSuccess: (() -> Unit)? = null) {
        if (_isDeletingInvoice.value) return
        _isDeletingInvoice.value = true
        viewModelScope.launch {
            try {
                when (val result = repository.deleteOrderAudited(orderId)) {
                    is PosResult.Success -> {
                        if (_selectedInvoiceForReceipt.value?.orderId == orderId) {
                            _isReceiptPopupOpen.value = false
                            _receiptPreviewOpen.value = false
                            _selectedInvoiceForReceipt.value = null
                        }
                        val isBn = language.value == "bn"
                        _toastMessage.value = if (isBn) "ভাউচার #$orderId সফলভাবে মুছে ফেলা হয়েছে।" else "Voucher #$orderId deleted successfully."
                        refreshHistory()
                        refreshDatabaseSize()
                        onSuccess?.invoke()
                    }
                    is PosResult.Error -> {
                        val isBn = language.value == "bn"
                        _toastMessage.value = if (result.error is PosError.Unauthenticated) {
                            Localization.get(Localization.Key.ERR_AUTH_REQUIRED_ACTION, _language.value)
                        } else if (isBn) {
                            "ভাউচার মুছতে সমস্যা হয়েছে: ${result.error.message}"
                        } else {
                            "Failed to delete voucher: ${result.error.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                val isBn = language.value == "bn"
                _toastMessage.value = if (isBn) "ভাউচার মুছতে সমস্যা হয়েছে: ${e.message}" else "Failed to delete voucher: ${e.message}"
            } finally {
                _isDeletingInvoice.value = false
            }
        }
    }

    // PDF Invoice Generation, Sharing & Saving
    fun generateAndShareInvoicePdf(context: Context, invoice: InvoiceEntity, lines: List<InvoiceLineEntity>? = null) {
        viewModelScope.launch {
            try {
                val itemLines = if (!lines.isNullOrEmpty()) lines else repository.getLinesForOrderDirect(invoice.orderId)
                val result = PdfInvoiceEngine.generateInvoicePdf(
                    context = context,
                    company = companyHeader.value,
                    invoice = invoice,
                    lines = itemLines
                )
                when (result) {
                    is PosResult.Success -> {
                        val shareIntent = PdfInvoiceEngine.getSharePdfIntent(context, result.data)
                        context.startActivity(Intent.createChooser(shareIntent, "Share Invoice PDF"))
                    }
                    is PosResult.Error -> {
                        _toastMessage.value = "PDF error: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Failed to generate PDF: ${e.message}"
            }
        }
    }

    fun saveInvoicePdfToUri(context: Context, uri: Uri, invoice: InvoiceEntity, lines: List<InvoiceLineEntity>? = null) {
        viewModelScope.launch {
            try {
                val itemLines = if (!lines.isNullOrEmpty()) lines else repository.getLinesForOrderDirect(invoice.orderId)
                val result = PdfInvoiceEngine.saveInvoicePdfToUri(
                    context = context,
                    uri = uri,
                    company = companyHeader.value,
                    invoice = invoice,
                    lines = itemLines
                )
                when (result) {
                    is PosResult.Success -> {
                        _toastMessage.value = if (language.value == "bn") "পিডিএফ সফলভাবে সংরক্ষণ করা হয়েছে!" else "Invoice PDF saved successfully!"
                    }
                    is PosResult.Error -> {
                        _toastMessage.value = "PDF save error: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Failed to save PDF: ${e.message}"
            }
        }
    }

    fun generateAndShareCurrentDraftPdf(context: Context) {
        viewModelScope.launch {
            try {
                if (_draftItems.value.isEmpty()) {
                    _toastMessage.value = Localization.get(Localization.Key.EMPTY_INVOICE_PROMPT, _language.value)
                    return@launch
                }
                val operator = activeOperator.value ?: repository.getActiveOperator()
                if (operator == null) {
                    val isBn = _language.value == "bn"
                    _toastMessage.value = if (isBn) {
                        "পিডিএফ তৈরি করতে অনুগ্রহ করে লগইন করুন।"
                    } else {
                        "Please sign in as an operator to generate a draft PDF."
                    }
                    return@launch
                }
                val orderId = _draftOrderId.value
                val now = System.currentTimeMillis()
                val settings = appSettings.value
                val header = companyHeader.value
                val branchCode = header?.branchNumber?.ifBlank { "01" } ?: "01"
                val globalId = com.example.pos.core.util.IdGenerator.generateGlobalTransactionId()

                val lines = _draftItems.value.mapIndexed { idx, it ->
                    InvoiceLineEntity(
                        orderId = orderId,
                        itemId = it.itemId,
                        itemName = it.itemName,
                        itemType = it.itemType,
                        unit = it.unit,
                        quantityStr = it.quantityStr,
                        unitPricePaisa = it.unitPricePaisa,
                        lineTotalPaisa = it.lineTotalPaisa,
                        sortOrder = idx + 1
                    )
                }
                val rawTotal = FinancialCalculationEngine.calculateRawTotal(lines.map { it.lineTotalPaisa })
                val roundedTotal = FinancialCalculationEngine.calculateRoundedTotalBdt(rawTotal)
                val invoice = InvoiceEntity(
                    orderId = orderId,
                    globalTransactionId = globalId,
                    branchCode = branchCode,
                    dateTime = now,
                    operatorId = operator.userId,
                    operatorName = operator.operatorName,
                    customerInfo = _customerInfo.value.ifBlank { "01700000000" },
                    rawTotalPaisa = rawTotal,
                    roundedTotalBdt = roundedTotal,
                    footerNote = "${settings?.footerNoteLine1 ?: ""}\n${settings?.footerNoteLine2 ?: ""}".trim()
                )

                val result = PdfInvoiceEngine.generateInvoicePdf(
                    context = context,
                    company = header,
                    invoice = invoice,
                    lines = lines
                )
                when (result) {
                    is PosResult.Success -> {
                        val shareIntent = PdfInvoiceEngine.getSharePdfIntent(context, result.data)
                        context.startActivity(Intent.createChooser(shareIntent, "Share Invoice PDF"))
                    }
                    is PosResult.Error -> {
                        _toastMessage.value = "PDF error: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Failed to generate PDF: ${e.message}"
            }
        }
    }

    // Excel Export & Import
    fun exportSalesToExcel(
        customRange: Pair<Long, Long>? = null,
        onSuccess: (File) -> Unit
    ) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val (startDate, endDate) = customRange ?: _exportDateRange.value ?: calculateDateRange(_dateFilter.value, _customStartDate.value, _customEndDate.value)
                val query = _historyQuery.value
                val orderId = _historyOrderIdFilter.value
                val customer = _historyCustomerFilter.value
                val invoices = repository.getInvoicesForExportFiltered(startDate, endDate, query, orderId, customer)
                val branchCode = companyHeader.value?.branchNumber ?: "01"
                val result = ExcelExchangeEngine.exportToXlsx(getApplication(), invoices, branchCode) { orderIdKey ->
                    repository.getLinesForOrderDirect(orderIdKey)
                }
                result.onSuccess { file ->
                    repository.recordXlsxExportAudit(
                        exportType = if (customRange != null || _exportDateRange.value != null) "RANGE_FILE" else "FULL_OR_FILTERED_FILE",
                        recordCount = invoices.size,
                        rangeLabel = if (customRange != null || _exportDateRange.value != null) "CUSTOM" else _dateFilter.value.name,
                        fileName = file.name
                    )
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "এক্সেল ফাইল এক্সপোর্ট সম্পন্ন: ${file.name}" else "Exported to: ${file.name}"
                    refreshDatabaseSize()
                    onSuccess(file)
                }.onFailure { err ->
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "এক্সপোর্ট ব্যর্থ হয়েছে: ${err.message}" else "Export failed: ${err.message}"
                }
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportSalesToUri(
        uri: Uri,
        customRange: Pair<Long, Long>? = null
    ) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val (startDate, endDate) = customRange ?: _exportDateRange.value ?: calculateDateRange(_dateFilter.value, _customStartDate.value, _customEndDate.value)
                val query = _historyQuery.value
                val orderId = _historyOrderIdFilter.value
                val customer = _historyCustomerFilter.value
                val invoices = repository.getInvoicesForExportFiltered(startDate, endDate, query, orderId, customer)
                val branchCode = companyHeader.value?.branchNumber ?: "01"
                val result = ExcelExchangeEngine.exportToUri(getApplication(), uri, invoices, branchCode) { orderIdKey ->
                    repository.getLinesForOrderDirect(orderIdKey)
                }
                result.onSuccess {
                    repository.recordXlsxExportAudit(
                        exportType = if (customRange != null || _exportDateRange.value != null) "RANGE_URI" else "FULL_OR_FILTERED_URI",
                        recordCount = invoices.size,
                        rangeLabel = if (customRange != null || _exportDateRange.value != null) "CUSTOM" else _dateFilter.value.name,
                        fileName = uri.lastPathSegment
                    )
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "বিক্রয় লেনদেন সফলভাবে এক্সেল ফাইলে এক্সপোর্ট হয়েছে!" else "Sales transactions successfully exported to chosen file!"
                    refreshDatabaseSize()
                }.onFailure { err ->
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "এক্সপোর্ট ব্যর্থ হয়েছে: ${err.message}" else "Export failed: ${err.message}"
                }
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun importSalesFromUri(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val result = repository.importSalesFromUri(getApplication(), uri)
                result.onSuccess { summary ->
                    _importSummary.value = summary
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) {
                        "সিঙ্ক সম্পন্ন: ${summary.importedCount} নতুন যুক্ত, ${summary.duplicateSkippedCount} বাদ, ${summary.conflictCount} কনফ্লিক্ট।"
                    } else {
                        "Sync complete: ${summary.importedCount} imported, ${summary.duplicateSkippedCount} skipped, ${summary.conflictCount} conflicts."
                    }
                    refreshHistory()
                    refreshDatabaseSize()
                }.onFailure { err ->
                    val isBn = language.value == "bn"
                    _toastMessage.value = if (isBn) "ইমপোর্ট ব্যর্থ হয়েছে: ${err.message}" else "Import failed: ${err.message}"
                }
            } finally {
                _isImporting.value = false
            }
        }
    }

    // Product CRUD
    fun saveProduct(
        itemId: String?,
        name: String,
        nameBn: String = "",
        nameEn: String = "",
        type: String,
        unit: String,
        priceBdtText: String,
        sortOrder: Int
    ) {
        viewModelScope.launch {
            val id = itemId ?: ("item-" + UUID.randomUUID().toString().take(6))
            val pricePaisa = FinancialCalculationEngine.parseBdtToPaisa(priceBdtText)
            val trimmedName = name.trim()
            val trimmedBn = nameBn.trim()
            val trimmedEn = nameEn.trim()
            val finalName = when {
                trimmedName.isNotBlank() -> trimmedName
                trimmedEn.isNotBlank() -> trimmedEn
                trimmedBn.isNotBlank() -> trimmedBn
                else -> "Item"
            }
            val entity = ProductEntity(
                itemId = id,
                itemName = finalName,
                nameBn = trimmedBn.ifBlank { if (trimmedName.any { it in '\u0980'..'\u09FF' }) trimmedName else "" },
                nameEn = trimmedEn.ifBlank { if (!trimmedName.any { it in '\u0980'..'\u09FF' }) trimmedName else "" },
                itemType = type,
                unit = unit.trim(),
                defaultUnitPricePaisa = pricePaisa,
                sortOrder = sortOrder,
                isActive = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            when (val res = repository.saveProductAudited(entity)) {
                is PosResult.Success -> _toastMessage.value = "Product saved!"
                is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
            }
        }
    }

    fun deleteProduct(itemId: String) {
        viewModelScope.launch {
            when (val res = repository.deleteProductAudited(itemId)) {
                is PosResult.Success -> _toastMessage.value = "Product removed"
                is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
            }
        }
    }

    fun reorderProducts(orderedItemIds: List<String>) {
        viewModelScope.launch {
            when (val res = repository.updateProductReorderAudited(orderedItemIds)) {
                is PosResult.Success -> _toastMessage.value = "Order updated!"
                is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
            }
        }
    }

    fun moveProduct(fromIndex: Int, toIndex: Int, currentProducts: List<ProductEntity>) {
        if (fromIndex in currentProducts.indices && toIndex in currentProducts.indices && fromIndex != toIndex) {
            val mutableList = currentProducts.toMutableList()
            val item = mutableList.removeAt(fromIndex)
            mutableList.add(toIndex, item)
            reorderProducts(mutableList.map { it.itemId })
        }
    }

    // Settings Updates
    fun updateCompanyHeader(
        companyName: String,
        branchName: String,
        branchNumber: String,
        phone: String,
        address: String
    ) {
        viewModelScope.launch {
            val current = companyHeader.value
            val updated = current?.copy(
                companyName = companyName.trim(),
                branchName = branchName.trim(),
                branchNumber = branchNumber.trim(),
                phoneNumber = phone.trim(),
                address = address.trim(),
                updatedAt = System.currentTimeMillis()
            ) ?: CompanyHeaderEntity(
                id = 1,
                companyName = companyName.trim(),
                branchName = branchName.trim(),
                branchNumber = branchNumber.trim(),
                phoneNumber = phone.trim(),
                address = address.trim(),
                logoUri = null,
                updatedAt = System.currentTimeMillis()
            )
            when (val res = repository.updateCompanyHeaderAudited(updated, logoChangeAction = null)) {
                is PosResult.Success -> _toastMessage.value = "Company header updated!"
                is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
            }
        }
    }

    /**
     * Configures the company logo from a user-selected device Uri (e.g. Photo Picker).
     * Saves a permanent local copy in private storage so it survives restarts offline.
     */
    fun setCompanyLogoUri(sourceUri: Uri) {
        viewModelScope.launch {
            if (repository.getActiveOperator() == null) {
                _toastMessage.value = Localization.get(Localization.Key.ERR_AUTH_REQUIRED_ACTION, _language.value)
                return@launch
            }
            val app = getApplication<Application>()
            val savedPath = CompanyLogoManager.copyLogoFromUri(app, sourceUri)
            if (savedPath != null) {
                val current = companyHeader.value
                val updated = current?.copy(
                    logoUri = savedPath,
                    updatedAt = System.currentTimeMillis()
                ) ?: CompanyHeaderEntity(
                    id = 1,
                    companyName = "মেসার্স রাকিব ফিলিং স্টেশন\nএন্ড\nএল পি জি অটোগ্যাস ফিলিং স্টেশন",
                    branchName = "প্রধান শাখা",
                    branchNumber = "01",
                    address = "ঢাকা, বাংলাদেশ",
                    phoneNumber = "01700000000",
                    logoUri = savedPath,
                    updatedAt = System.currentTimeMillis()
                )
                when (val res = repository.updateCompanyHeaderAudited(updated, logoChangeAction = "CUSTOM_CONFIGURED")) {
                    is PosResult.Success -> _toastMessage.value = Localization.get(Localization.Key.LOGO_ADDED_SUCCESS, _language.value)
                    is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
                }
            } else {
                _toastMessage.value = Localization.get(Localization.Key.LOGO_ERROR_READ, _language.value)
            }
        }
    }

    /**
     * Configures or restores the official 512x512 company logo asset.
     */
    fun useOfficialDefaultLogo() {
        viewModelScope.launch {
            if (repository.getActiveOperator() == null) {
                _toastMessage.value = Localization.get(Localization.Key.ERR_AUTH_REQUIRED_ACTION, _language.value)
                return@launch
            }
            val app = getApplication<Application>()
            val savedPath = CompanyLogoManager.copyDefaultOfficialLogo(app)
            if (savedPath != null) {
                val current = companyHeader.value
                val updated = current?.copy(
                    logoUri = savedPath,
                    updatedAt = System.currentTimeMillis()
                ) ?: CompanyHeaderEntity(
                    id = 1,
                    companyName = "মেসার্স রাকিব ফিলিং স্টেশন\nএন্ড\nএল পি জি অটোগ্যাস ফিলিং স্টেশন",
                    branchName = "প্রধান শাখা",
                    branchNumber = "01",
                    address = "ঢাকা, বাংলাদেশ",
                    phoneNumber = "01700000000",
                    logoUri = savedPath,
                    updatedAt = System.currentTimeMillis()
                )
                when (val res = repository.updateCompanyHeaderAudited(updated, logoChangeAction = "OFFICIAL_DEFAULT")) {
                    is PosResult.Success -> _toastMessage.value = Localization.get(Localization.Key.LOGO_ADDED_SUCCESS, _language.value)
                    is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
                }
            } else {
                _toastMessage.value = Localization.get(Localization.Key.LOGO_ERROR_READ, _language.value)
            }
        }
    }

    /**
     * Removes the configured company logo and gracefully resets to default.
     */
    fun removeCompanyLogo() {
        viewModelScope.launch {
            if (repository.getActiveOperator() == null) {
                _toastMessage.value = Localization.get(Localization.Key.ERR_AUTH_REQUIRED_ACTION, _language.value)
                return@launch
            }
            val app = getApplication<Application>()
            CompanyLogoManager.removeLogo(app)
            val current = companyHeader.value
            if (current != null) {
                val updated = current.copy(
                    logoUri = null,
                    updatedAt = System.currentTimeMillis()
                )
                when (val res = repository.updateCompanyHeaderAudited(updated, logoChangeAction = "REMOVED")) {
                    is PosResult.Success -> _toastMessage.value = Localization.get(Localization.Key.LOGO_REMOVED_SUCCESS, _language.value)
                    is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
                }
            } else {
                _toastMessage.value = Localization.get(Localization.Key.LOGO_REMOVED_SUCCESS, _language.value)
            }
        }
    }

    fun updateOperatorProfile(name: String, phone: String, address: String) {
        viewModelScope.launch {
            val lang = _language.value
            val trimmedName = name.trim()
            val normalizedPhone = AuthInputValidator.normalizePhone(phone)
            val res = repository.updateOperatorProfile(trimmedName, normalizedPhone, address.trim())
            if (res.isSuccess) {
                refreshRememberedAuthHints()
                _toastMessage.value = Localization.get(Localization.Key.OPERATOR_PROFILE_SAVED_SUCCESS, lang)
            } else {
                val posErr = (res.exceptionOrNull() as? PosErrorException)?.posError
                _toastMessage.value = when (posErr) {
                    is PosError.UserAlreadyExists -> Localization.get(Localization.Key.REG_ERR_DUPLICATE_PHONE, lang)
                    is PosError.ValidationError -> when {
                        trimmedName.isBlank() -> Localization.get(Localization.Key.REG_ERR_NAME_REQUIRED, lang)
                        trimmedName.length < AuthInputValidator.MIN_NAME_LENGTH ->
                            Localization.get(Localization.Key.REG_ERR_NAME_TOO_SHORT, lang)
                        trimmedName.length > AuthInputValidator.MAX_NAME_LENGTH ->
                            Localization.get(Localization.Key.REG_ERR_NAME_TOO_LONG, lang)
                        normalizedPhone.isBlank() -> Localization.get(Localization.Key.REG_ERR_PHONE_REQUIRED, lang)
                        else -> Localization.get(Localization.Key.REG_ERR_PHONE_INVALID, lang)
                    }
                    else -> Localization.get(Localization.Key.OPERATOR_PROFILE_UPDATE_FAILED, lang)
                }
            }
        }
    }

    fun updateFooterNotes(line1: String, line2: String) {
        viewModelScope.launch {
            when (val res = repository.updateFooterNotesAudited(line1, line2)) {
                is PosResult.Success -> _toastMessage.value = "Footer notes saved!"
                is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
            }
        }
    }

    fun setLanguage(lang: String) {
        val prevLang = _language.value
        _language.value = lang
        viewModelScope.launch {
            when (val res = repository.updateLanguageAudited(lang)) {
                is PosResult.Error -> {
                    _language.value = prevLang
                    _toastMessage.value = handleAuditedErrorMessage(res.error, prevLang)
                    return@launch
                }
                is PosResult.Success -> Unit
            }
        }
        if (_receiptPreviewOpen.value) {
            _receiptPreviewText.value = generateCurrentReceiptText()
        }
        val curSelected = _selectedInvoiceForReceipt.value
        if (_isReceiptPopupOpen.value && curSelected != null) {
            viewModelScope.launch {
                val lines = _receiptPopupLines.value.ifEmpty {
                    repository.getLinesForOrderDirect(curSelected.orderId)
                }
                val header = companyHeader.value
                val settings = appSettings.value
                val paperSize = settings?.paperSize ?: "58mm"
                _receiptPreviewText.value = EscPosPrinterEngine.generateReceiptText(
                    header = header,
                    invoice = curSelected,
                    lines = lines,
                    paperSize = paperSize,
                    footerLine1 = settings?.footerNoteLine1 ?: "",
                    footerLine2 = settings?.footerNoteLine2 ?: "",
                    lang = lang
                )
            }
        }
    }

    fun setThemeMode(mode: String) {
        val prevMode = _themeMode.value
        _themeMode.value = mode
        viewModelScope.launch {
            when (val res = repository.updateThemeModeAudited(mode)) {
                is PosResult.Error -> {
                    _themeMode.value = prevMode
                    _toastMessage.value = handleAuditedErrorMessage(res.error)
                }
                is PosResult.Success -> Unit
            }
        }
    }

    fun setPaperSize(size: String) {
        viewModelScope.launch {
            when (val res = repository.updatePaperSizeAudited(size)) {
                is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
                is PosResult.Success -> Unit
            }
        }
    }

    fun setAutoCutter(enabled: Boolean) {
        viewModelScope.launch {
            when (val res = repository.updateAutoCutterAudited(enabled)) {
                is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
                is PosResult.Success -> Unit
            }
        }
    }

    fun setBluetoothPrinter(address: String?, name: String?) {
        viewModelScope.launch {
            when (val res = repository.updateBluetoothPrinterAudited(address, name)) {
                is PosResult.Success -> _toastMessage.value = "Printer set to: ${name ?: address ?: "None"}"
                is PosResult.Error -> _toastMessage.value = handleAuditedErrorMessage(res.error)
            }
        }
    }

    private fun handleAuditedErrorMessage(error: PosError, lang: String = _language.value): String {
        return when (error) {
            is PosError.Unauthenticated -> Localization.get(Localization.Key.ERR_AUTH_REQUIRED_ACTION, lang)
            else -> error.message
        }
    }

    // Employee Activity History filter & pagination controls
    fun setAuditOperatorFilter(operatorId: String) {
        _auditOperatorFilter.value = operatorId
        _auditCurrentPage.value = 0
    }

    fun setAuditCategoryFilter(category: AuditCategoryFilter) {
        _auditCategoryFilter.value = category
        _auditCurrentPage.value = 0
    }

    fun setAuditDateFilter(filter: AuditDateFilter) {
        _auditDateFilter.value = filter
        if (filter != AuditDateFilter.CUSTOM) {
            _auditCustomStartDate.value = null
            _auditCustomEndDate.value = null
        }
        _auditCurrentPage.value = 0
    }

    fun setAuditCustomDateRange(startDate: Long, endDate: Long) {
        val calStart = Calendar.getInstance().apply {
            timeInMillis = minOf(startDate, endDate)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calEnd = Calendar.getInstance().apply {
            timeInMillis = maxOf(startDate, endDate)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        _auditCustomStartDate.value = calStart.timeInMillis
        _auditCustomEndDate.value = calEnd.timeInMillis
        _auditDateFilter.value = AuditDateFilter.CUSTOM
        _auditCurrentPage.value = 0
    }

    fun setAuditQuery(query: String) {
        _auditQuery.value = query
        _auditCurrentPage.value = 0
    }

    fun nextAuditPage() {
        val total = auditTotalCount.value
        val nextOffset = (_auditCurrentPage.value + 1) * auditPageSize
        if (nextOffset < total) {
            _auditCurrentPage.value = _auditCurrentPage.value + 1
        }
    }

    fun prevAuditPage() {
        if (_auditCurrentPage.value > 0) {
            _auditCurrentPage.value = _auditCurrentPage.value - 1
        }
    }

    fun resetAuditFilters() {
        _auditOperatorFilter.value = ""
        _auditCategoryFilter.value = AuditCategoryFilter.ALL
        _auditDateFilter.value = AuditDateFilter.ALL
        _auditCustomStartDate.value = null
        _auditCustomEndDate.value = null
        _auditQuery.value = ""
        _auditCurrentPage.value = 0
    }

    private fun calculateAuditDateRange(
        filter: AuditDateFilter,
        customStart: Long?,
        customEnd: Long?
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        return when (filter) {
            AuditDateFilter.ALL -> 0L to Long.MAX_VALUE
            AuditDateFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                start to (start + 86400000L - 1L)
            }
            AuditDateFilter.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                start to (start + 86400000L - 1L)
            }
            AuditDateFilter.LAST_7_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to Long.MAX_VALUE
            }
            AuditDateFilter.LAST_30_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to Long.MAX_VALUE
            }
            AuditDateFilter.CUSTOM -> {
                val start = customStart ?: 0L
                val end = customEnd ?: Long.MAX_VALUE
                start to end
            }
        }
    }

    fun testPrint() {
        viewModelScope.launch {
            val settings = appSettings.value
            val header = companyHeader.value
            val paperSize = settings?.paperSize ?: "58mm"
            val autoCutter = settings?.autoCutterEnabled ?: true
            val isBn = language.value == "bn"

            val address = settings?.bluetoothPrinterAddress
            val companyName = header?.companyName?.ifBlank { "POS Station" } ?: "POS Station"

            val testSlipText = ThermalReceiptFormatter.generateTestSlipText(
                companyName = companyName,
                paperSize = paperSize,
                autoCutter = autoCutter
            )

            if (address != null) {
                val payload = ThermalReceiptFormatter.buildEscPosTestSlip(
                    companyName = companyName,
                    paperSize = paperSize,
                    autoCutter = autoCutter
                )
                val res = BluetoothPrinterManager.print(
                    deviceAddress = address,
                    deviceName = settings.bluetoothPrinterName,
                    payload = payload
                )
                if (res.isSuccess) {
                    _toastMessage.value = if (isBn) "টেস্ট রসিদ প্রিন্ট সম্পন্ন!" else "Test print sent successfully!"
                } else {
                    _toastMessage.value = if (isBn) "প্রিন্টার সমস্যা: ${res.exceptionOrNull()?.message}" else "Printer error: ${res.exceptionOrNull()?.message}"
                }
            } else {
                _toastMessage.value = if (isBn) "কোনো প্রিন্টার যুক্ত নেই। টেস্ট স্লিপ প্রিভিউ দেখানো হচ্ছে।" else "No printer configured. Previewing test slip."
                _receiptPreviewText.value = testSlipText
                _receiptPreviewOpen.value = true
            }
        }
    }

    // Auth Operations
    fun login(phone: String, pass: String, remember: Boolean, onResult: (Boolean, String?) -> Unit) {
        if (_isLoggingIn.value) return
        _isLoggingIn.value = true
        _loginBannerMessage.value = null

        viewModelScope.launch {
            try {
                val normalizedPhone = AuthInputValidator.normalizePhone(phone)
                val res = repository.login(normalizedPhone, pass, remember)
                refreshRememberedAuthHints()
                if (res.isSuccess) {
                    _loginLockoutSeconds.value = 0L
                    _loginBannerMessage.value = null
                    refreshNextOrderId()
                    refreshHistory()
                    onResult(true, null)
                } else {
                    val ex = res.exceptionOrNull()
                    val lang = _language.value
                    val posErr = (ex as? PosErrorException)?.posError
                    val msg = when (posErr) {
                        is PosError.AccountLocked -> {
                            _loginLockoutSeconds.value = posErr.remainingSeconds.coerceAtLeast(1L)
                            Localization.get(Localization.Key.LOCKOUT_DESC, lang)
                        }
                        is PosError.InvalidCredentials -> {
                            _loginLockoutSeconds.value = 0L
                            Localization.get(Localization.Key.LOGIN_ERR_INVALID_CREDENTIALS, lang)
                        }
                        is PosError.ValidationError -> {
                            when {
                                normalizedPhone.isBlank() -> Localization.get(Localization.Key.REG_ERR_PHONE_REQUIRED, lang)
                                pass.isBlank() -> Localization.get(Localization.Key.REG_ERR_PASSWORD_REQUIRED, lang)
                                else -> Localization.get(Localization.Key.LOGIN_ERR_GENERIC, lang)
                            }
                        }
                        else -> Localization.get(Localization.Key.LOGIN_ERR_GENERIC, lang)
                    }
                    onResult(false, msg)
                }
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    fun registerWithRecovery(
        name: String,
        phone: String,
        address: String,
        pass: String,
        onResult: (Boolean, String?, String?) -> Unit
    ) {
        if (_isRegistering.value) return
        _isRegistering.value = true

        viewModelScope.launch {
            try {
                val normalizedPhone = AuthInputValidator.normalizePhone(phone)
                val res = repository.registerOperatorWithRecovery(name, normalizedPhone, address, pass)
                val count = repository.getOperatorCount()
                _operatorCount.value = count
                _hasRegisteredOperators.value = count > 0
                refreshRememberedAuthHints()
                if (res.isSuccess) {
                    val reg = res.getOrNull()
                    _pendingRecoveryCode.value = reg?.recoveryCode
                    _pendingRecoveryOperatorName.value = reg?.operator?.operatorName
                    _loginLockoutSeconds.value = 0L
                    _loginBannerMessage.value = null
                    refreshNextOrderId()
                    refreshHistory()
                    onResult(true, reg?.recoveryCode, null)
                } else {
                    val ex = res.exceptionOrNull()
                    val lang = _language.value
                    val errorMsg = when ((ex as? PosErrorException)?.posError) {
                        is PosError.UserAlreadyExists -> Localization.get(Localization.Key.REG_ERR_DUPLICATE_PHONE, lang)
                        is PosError.DatabaseError -> Localization.get(Localization.Key.REG_ERR_GENERIC, lang)
                        else -> ex?.message ?: Localization.get(Localization.Key.REG_ERR_GENERIC, lang)
                    }
                    onResult(false, null, errorMsg)
                }
            } finally {
                _isRegistering.value = false
            }
        }
    }

    fun acknowledgeRecoveryCode() {
        _pendingRecoveryCode.value = null
        _pendingRecoveryOperatorName.value = null
    }

    fun register(name: String, phone: String, address: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        registerWithRecovery(name, phone, address, pass) { success, _, err ->
            onResult(success, err)
        }
    }

    fun verifyRecoveryCode(
        phone: String,
        recoveryCode: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (_isVerifyingRecovery.value) return
        _isVerifyingRecovery.value = true

        viewModelScope.launch {
            try {
                val normalizedPhone = AuthInputValidator.normalizePhone(phone)
                val lang = _language.value
                val res = repository.verifyRecoveryCode(normalizedPhone, recoveryCode)
                if (res.isSuccess) {
                    onResult(true, null)
                } else {
                    val posErr = (res.exceptionOrNull() as? PosErrorException)?.posError
                    val msg = when (posErr) {
                        is PosError.ValidationError -> {
                            when {
                                normalizedPhone.isBlank() -> Localization.get(Localization.Key.REG_ERR_PHONE_REQUIRED, lang)
                                recoveryCode.isBlank() -> Localization.get(Localization.Key.RECOVERY_ERR_CODE_REQUIRED, lang)
                                else -> Localization.get(Localization.Key.RECOVERY_ERR_INVALID_OR_UNAVAILABLE, lang)
                            }
                        }
                        // Unify RecordNotFound, RecoveryNotConfigured, InvalidRecoveryCode to avoid enumerating phone numbers
                        else -> Localization.get(Localization.Key.RECOVERY_ERR_INVALID_OR_UNAVAILABLE, lang)
                    }
                    onResult(false, msg)
                }
            } finally {
                _isVerifyingRecovery.value = false
            }
        }
    }

    fun resetPassword(
        phone: String,
        recoveryCode: String,
        newPass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (_isResettingPassword.value) return
        _isResettingPassword.value = true

        viewModelScope.launch {
            try {
                val normalizedPhone = AuthInputValidator.normalizePhone(phone)
                val lang = _language.value
                val res = repository.resetPassword(normalizedPhone, recoveryCode, newPass)
                refreshRememberedAuthHints()
                if (res.isSuccess) {
                    _loginLockoutSeconds.value = 0L
                    _loginBannerMessage.value = Localization.get(Localization.Key.RECOVERY_PASSWORD_UPDATED_SUCCESS, lang)
                    onResult(true, null)
                } else {
                    val posErr = (res.exceptionOrNull() as? PosErrorException)?.posError
                    val msg = when (posErr) {
                        is PosError.ValidationError -> {
                            when {
                                newPass.isBlank() -> Localization.get(Localization.Key.REG_ERR_PASSWORD_REQUIRED, lang)
                                newPass.length < AuthInputValidator.MIN_PASSWORD_LENGTH ->
                                    Localization.get(Localization.Key.REG_ERR_PASSWORD_MIN, lang)
                                else -> Localization.get(Localization.Key.REG_ERR_PASSWORD_MAX, lang)
                            }
                        }
                        else -> Localization.get(Localization.Key.RECOVERY_ERR_INVALID_OR_UNAVAILABLE, lang)
                    }
                    onResult(false, msg)
                }
            } finally {
                _isResettingPassword.value = false
            }
        }
    }

    /**
     * Compile-compatible legacy overload for untouched UI screens in Step 07B.
     * Unauthenticated password reset without recovery code verification is strictly rejected.
     */
    fun resetPassword(phone: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        onResult(false, "Recovery code verification is required to reset password.")
    }

    fun clearManagedRecoveryCode() {
        _managedRecoveryCode.value = null
    }

    fun regenerateRecoveryCode(onResult: (Boolean, String?, String?) -> Unit) {
        if (_isRegeneratingRecoveryCode.value) return
        val lang = _language.value
        val current = activeOperator.value
        if (current == null) {
            onResult(false, null, Localization.get(Localization.Key.RECOVERY_ERR_REGEN_FAILED, lang))
            return
        }
        _isRegeneratingRecoveryCode.value = true
        viewModelScope.launch {
            try {
                val activeOp = activeOperator.value ?: repository.getActiveOperator()
                if (activeOp == null) {
                    onResult(false, null, Localization.get(Localization.Key.RECOVERY_ERR_REGEN_FAILED, lang))
                    return@launch
                }
                val res = repository.regenerateRecoveryCode(activeOp.userId)
                if (res.isSuccess) {
                    val code = res.getOrNull()
                    if (!code.isNullOrBlank()) {
                        _managedRecoveryCode.value = code
                        onResult(true, code, null)
                    } else {
                        onResult(false, null, Localization.get(Localization.Key.RECOVERY_ERR_REGEN_FAILED, lang))
                    }
                } else {
                    onResult(false, null, Localization.get(Localization.Key.RECOVERY_ERR_REGEN_FAILED, lang))
                }
            } catch (_: Exception) {
                onResult(false, null, Localization.get(Localization.Key.RECOVERY_ERR_REGEN_FAILED, lang))
            } finally {
                _isRegeneratingRecoveryCode.value = false
            }
        }
    }

    fun logout(onComplete: (() -> Unit)? = null) {
        // Immediately wipe in-memory draft, customer, recovery, and receipt preview state
        _draftItems.value = emptyList()
        _customerInfo.value = ""
        _customerInfoError.value = null
        _customerSuggestions.value = emptyList()
        _draftPaidAmountBdt.value = ""
        _isReceiptPopupOpen.value = false
        _receiptPreviewOpen.value = false
        _selectedInvoiceForReceipt.value = null
        _receiptPopupLines.value = emptyList()
        _receiptPreviewText.value = ""
        _navigateHomeAfterPreview.value = false
        _closeDetailsOnPrintSuccess.value = false
        _invoiceToEdit.value = null
        _invoiceToDelete.value = null
        _importSummary.value = null
        _pendingRecoveryCode.value = null
        _pendingRecoveryOperatorName.value = null
        _managedRecoveryCode.value = null
        _isRegistering.value = false
        _isLoggingIn.value = false
        _isVerifyingRecovery.value = false
        _isResettingPassword.value = false
        _isRegeneratingRecoveryCode.value = false
        _loginLockoutSeconds.value = 0L
        _loginBannerMessage.value = null
        _historyQuery.value = ""
        _historyOrderIdFilter.value = ""
        _historyCustomerFilter.value = ""
        _orderIdSuggestions.value = emptyList()
        _dateFilter.value = DateFilterMode.ALL
        _customStartDate.value = null
        _customEndDate.value = null
        _historySortOption.value = HistorySortOption.DATE_DESC
        _currentPage.value = 0
        _expandedOrderId.value = null
        _expandedLines.value = emptyList()
        _orderLinesCache.value = emptyMap()
        _exportDateRange.value = null
        _analyticsGrouping.value = AnalyticsGrouping.DAY
        _analyticsMetric.value = AnalyticsMetric.ROUNDED_SALES
        resetAuditFilters()

        viewModelScope.launch {
            try {
                repository.logout()
                val count = repository.getOperatorCount()
                _operatorCount.value = count
                _hasRegisteredOperators.value = count > 0
                refreshRememberedAuthHints()
                if (cachedRememberedPhone.isNotBlank()) {
                    _loginLockoutSeconds.value = runCatching {
                        repository.getLockoutRemainingSeconds(cachedRememberedPhone)
                    }.getOrDefault(0L)
                }
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
