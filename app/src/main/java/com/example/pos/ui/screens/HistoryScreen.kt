package com.example.pos.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.Localization
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.ItemSalesSummary
import com.example.pos.data.repository.HistorySortOption
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.ui.components.CompanyLogoView
import com.example.pos.ui.components.ImportSummaryDialog
import com.example.pos.ui.components.TopStationHeader
import com.example.pos.ui.viewmodel.DateFilterMode
import com.example.pos.ui.viewmodel.PosViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier,
    onOpenNewInvoice: () -> Unit = {}
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val companyHeader by viewModel.companyHeader.collectAsState()
    val activeOperator by viewModel.activeOperator.collectAsState()

    val historyQuery by viewModel.historyQuery.collectAsState()
    val historyOrderIdFilter by viewModel.historyOrderIdFilter.collectAsState()
    val historyCustomerFilter by viewModel.historyCustomerFilter.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val sortOption by viewModel.historySortOption.collectAsState()

    val invoices by viewModel.historyInvoices.collectAsState()
    val totalCount by viewModel.totalFilteredCount.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val pageSize by viewModel.pageSize.collectAsState()
    val isLoading by viewModel.isLoadingHistory.collectAsState()

    val expandedOrderId by viewModel.expandedOrderId.collectAsState()
    val expandedLines by viewModel.expandedLines.collectAsState()
    val orderLinesCache by viewModel.orderLinesCache.collectAsState()

    val aggregatedOrders by viewModel.aggregatedOrders.collectAsState()
    val aggregatedRawPaisa by viewModel.aggregatedRawPaisa.collectAsState()
    val aggregatedRoundedBdt by viewModel.aggregatedRoundedBdt.collectAsState()
    val topSellingItems by viewModel.topSellingItems.collectAsState()

    val isReceiptPopupOpen by viewModel.isReceiptPopupOpen.collectAsState()
    val selectedInvoiceForReceipt by viewModel.selectedInvoiceForReceipt.collectAsState()
    val receiptPopupLines by viewModel.receiptPopupLines.collectAsState()
    val receiptPopupText by viewModel.receiptPreviewText.collectAsState()

    val analyticsGrouping by viewModel.analyticsGrouping.collectAsState()
    val analyticsMetric by viewModel.analyticsMetric.collectAsState()
    val analyticsDataPoints by viewModel.analyticsDataPoints.collectAsState()
    val chartDataPoints by viewModel.chartDataPoints.collectAsState()

    var activeHistoryTab by remember { mutableStateOf(0) }

    // UI state for filter dialogs
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var showDateFilterMenu by remember { mutableStateOf(false) }
    var customStartDateText by remember { mutableStateOf("") }
    var customEndDateText by remember { mutableStateOf("") }
    var customDateErrorText by remember { mutableStateOf<String?>(null) }
    var showXlsxSync by remember { mutableStateOf(false) }

    val isDeletingInvoice by viewModel.isDeletingInvoice.collectAsState()
    var invoiceToDelete by remember { mutableStateOf<InvoiceEntity?>(null) }
    var invoiceToEdit by remember { mutableStateOf<InvoiceEntity?>(null) }
    val vmInvoiceToEdit by viewModel.invoiceToEdit.collectAsState()
    val vmInvoiceToDelete by viewModel.invoiceToDelete.collectAsState()
    var selectedInvoiceForDetails by remember { mutableStateOf<InvoiceEntity?>(null) }
    val orderIdSuggestions by viewModel.orderIdSuggestions.collectAsState()

    val totalInvoiceCount by viewModel.totalInvoiceCount.collectAsState()
    val remainingCapacity = (viewModel.supportedCapacity - totalInvoiceCount).coerceAtLeast(0)

    val importSummary by viewModel.importSummary.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSalesToUri(uri)
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importSalesFromUri(uri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Station Header
            TopStationHeader(
            company = companyHeader,
            operator = activeOperator,
            currentLanguage = language,
            currentTheme = themeMode,
            onToggleLanguage = {
                viewModel.setLanguage(if (language == "bn") "en" else "bn")
            },
            onToggleTheme = {
                viewModel.setThemeMode(if (themeMode == "DARK") "LIGHT" else "DARK")
            }
        )

        // Primary Navigation Tabs (Transactions vs Graphs & Analytics)
        PrimaryTabRow(
            selectedTabIndex = activeHistoryTab,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_primary_tabs")
        ) {
            Tab(
                selected = activeHistoryTab == 0,
                onClick = { activeHistoryTab = 0 },
                text = {
                    Text(
                        text = Localization.get(Localization.Key.TRANSACTIONS_TAB_TITLE, language),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_transactions")
            )
            Tab(
                selected = activeHistoryTab == 1,
                onClick = { activeHistoryTab = 1 },
                text = {
                    Text(
                        text = Localization.get(Localization.Key.ANALYTICS_TAB_TITLE, language),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("tab_analytics")
            )
        }

        if (activeHistoryTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
            // Search and Filtering Controls
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Main Search Field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = historyQuery,
                                onValueChange = { viewModel.setHistoryQuery(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("history_search_input"),
                                placeholder = {
                                    Text(
                                        text = Localization.get(Localization.Key.SEARCH_ORDERS_PLACEHOLDER, language),
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (historyQuery.isNotBlank()) {
                                        IconButton(onClick = { viewModel.setHistoryQuery("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Sort Dropdown Button
                            Box {
                                OutlinedButton(
                                    onClick = { showSortMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("sort_menu_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (sortOption) {
                                            HistorySortOption.DATE_DESC -> if (language == "bn") "তারিখ ↓" else "Date ↓"
                                            HistorySortOption.DATE_ASC -> if (language == "bn") "তারিখ ↑" else "Date ↑"
                                            HistorySortOption.AMOUNT_DESC -> if (language == "bn") "টাকা ↓" else "BDT ↓"
                                            HistorySortOption.AMOUNT_ASC -> if (language == "bn") "টাকা ↑" else "BDT ↑"
                                            HistorySortOption.ORDER_ID_DESC -> if (language == "bn") "আইডি Z-A" else "ID Z-A"
                                            HistorySortOption.ORDER_ID_ASC -> if (language == "bn") "আইডি A-Z" else "ID A-Z"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_DATE_DESC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.DATE_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_DATE_ASC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.DATE_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_AMOUNT_DESC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.AMOUNT_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_AMOUNT_ASC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.AMOUNT_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_ORDER_DESC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.ORDER_ID_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.SORT_ORDER_ASC, language)) },
                                        onClick = {
                                            viewModel.setHistorySortOption(HistorySortOption.ORDER_ID_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Dynamic Autocomplete Suggestions for Voucher / Order ID
                        if ((historyQuery.isNotBlank() || historyOrderIdFilter.isNotBlank()) && orderIdSuggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = Localization.get(Localization.Key.SEARCH_VOUCHER_SUGGESTIONS, language),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_suggestions_container"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                orderIdSuggestions.forEach { suggestionId ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.setHistoryQuery(suggestionId)
                                                viewModel.setOrderIdFilter(suggestionId)
                                                viewModel.toggleOrderExpansion(suggestionId)
                                            }
                                            .testTag("suggestion_$suggestionId")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Receipt,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = suggestionId,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Compact Date Filter Dropdown Selector
                        val dateFilterDisplayLabel = when (dateFilter) {
                            DateFilterMode.ALL -> Localization.get(Localization.Key.DATE_FILTER_ALL, language)
                            DateFilterMode.TODAY -> Localization.get(Localization.Key.DATE_FILTER_TODAY, language)
                            DateFilterMode.YESTERDAY -> Localization.get(Localization.Key.DATE_FILTER_YESTERDAY, language)
                            DateFilterMode.WEEK -> Localization.get(Localization.Key.DATE_FILTER_WEEK, language)
                            DateFilterMode.MONTH -> Localization.get(Localization.Key.DATE_FILTER_MONTH, language)
                            DateFilterMode.SIX_MONTHS -> Localization.get(Localization.Key.DATE_FILTER_SIX_MONTHS, language)
                            DateFilterMode.YEAR -> Localization.get(Localization.Key.DATE_FILTER_YEAR, language)
                            DateFilterMode.CUSTOM -> Localization.get(Localization.Key.DATE_FILTER_CUSTOM, language)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box {
                                OutlinedButton(
                                    onClick = { showDateFilterMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("date_filter_dropdown_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Date Filter",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dateFilterDisplayLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showDateFilterMenu,
                                    onDismissRequest = { showDateFilterMenu = false },
                                    modifier = Modifier.testTag("date_filter_menu")
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.DATE_FILTER_ALL, language), fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.setDateFilter(DateFilterMode.ALL)
                                            showDateFilterMenu = false
                                        },
                                        modifier = Modifier.testTag("date_filter_option_all")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.DATE_FILTER_TODAY, language), fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.setDateFilter(DateFilterMode.TODAY)
                                            showDateFilterMenu = false
                                        },
                                        modifier = Modifier.testTag("date_filter_option_today")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.DATE_FILTER_YESTERDAY, language), fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.setDateFilter(DateFilterMode.YESTERDAY)
                                            showDateFilterMenu = false
                                        },
                                        modifier = Modifier.testTag("date_filter_option_yesterday")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.DATE_FILTER_WEEK, language), fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.setDateFilter(DateFilterMode.WEEK)
                                            showDateFilterMenu = false
                                        },
                                        modifier = Modifier.testTag("date_filter_option_week")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.DATE_FILTER_MONTH, language), fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.setDateFilter(DateFilterMode.MONTH)
                                            showDateFilterMenu = false
                                        },
                                        modifier = Modifier.testTag("date_filter_option_month")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.DATE_FILTER_SIX_MONTHS, language), fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.setDateFilter(DateFilterMode.SIX_MONTHS)
                                            showDateFilterMenu = false
                                        },
                                        modifier = Modifier.testTag("date_filter_option_six_months")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.DATE_FILTER_YEAR, language), fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.setDateFilter(DateFilterMode.YEAR)
                                            showDateFilterMenu = false
                                        },
                                        modifier = Modifier.testTag("date_filter_option_year")
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(Localization.get(Localization.Key.DATE_FILTER_CUSTOM, language), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            viewModel.setDateFilter(DateFilterMode.CUSTOM)
                                            showDateFilterMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        modifier = Modifier.testTag("date_filter_option_custom")
                                    )
                                }
                            }

                            if (dateFilter != DateFilterMode.ALL || historyQuery.isNotBlank() || historyOrderIdFilter.isNotBlank() || historyCustomerFilter.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        customStartDateText = ""
                                        customEndDateText = ""
                                        customDateErrorText = null
                                        viewModel.clearAllFilters()
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = Localization.get(Localization.Key.FILTER_CLEAR_ALL, language),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Compact Custom Date Picker (Start Date and optional End Date)
                        if (dateFilter == DateFilterMode.CUSTOM) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("compact_custom_date_section"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customStartDateText,
                                            onValueChange = { newValue ->
                                                customStartDateText = newValue
                                                val parsedStart = DateTimeUtils.parseDateDdMmYyyy(newValue)
                                                val parsedEnd = if (customEndDateText.isNotBlank()) DateTimeUtils.parseDateDdMmYyyy(customEndDateText) else null
                                                if (newValue.isNotBlank() && parsedStart == null) {
                                                    customDateErrorText = "Invalid start date (dd-MM-yyyy)"
                                                } else if (parsedStart != null && customEndDateText.isNotBlank() && parsedEnd == null) {
                                                    customDateErrorText = "Invalid end date (dd-MM-yyyy)"
                                                } else if (parsedStart != null && parsedEnd != null && parsedEnd.before(parsedStart)) {
                                                    customDateErrorText = "End date cannot be earlier than start date"
                                                } else if (parsedStart != null) {
                                                    customDateErrorText = null
                                                    viewModel.setCustomDateRange(parsedStart.time, parsedEnd?.time)
                                                } else if (newValue.isBlank()) {
                                                    customDateErrorText = null
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("custom_date_from_input"),
                                            label = { Text(if (language == "bn") "শুরুর তারিখ" else "Start Date", fontSize = 10.sp) },
                                            placeholder = { Text("dd-MM-yyyy", fontSize = 10.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(6.dp)
                                        )

                                        OutlinedTextField(
                                            value = customEndDateText,
                                            onValueChange = { newValue ->
                                                customEndDateText = newValue
                                                val parsedStart = if (customStartDateText.isNotBlank()) DateTimeUtils.parseDateDdMmYyyy(customStartDateText) else null
                                                val parsedEnd = DateTimeUtils.parseDateDdMmYyyy(newValue)
                                                if (newValue.isNotBlank() && parsedEnd == null) {
                                                    customDateErrorText = "Invalid end date (dd-MM-yyyy)"
                                                } else if (parsedStart == null && customStartDateText.isNotBlank()) {
                                                    customDateErrorText = "Invalid start date (dd-MM-yyyy)"
                                                } else if (parsedStart != null && parsedEnd != null && parsedEnd.before(parsedStart)) {
                                                    customDateErrorText = "End date cannot be earlier than start date"
                                                } else if (parsedStart != null) {
                                                    customDateErrorText = null
                                                    viewModel.setCustomDateRange(parsedStart.time, parsedEnd?.time)
                                                } else if (newValue.isBlank()) {
                                                    if (parsedStart != null) {
                                                        customDateErrorText = null
                                                        viewModel.setCustomDateRange(parsedStart.time, null)
                                                    } else {
                                                        customDateErrorText = null
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("custom_date_to_input"),
                                            label = { Text(if (language == "bn") "শেষ তারিখ (ঐচ্ছিক)" else "End Date (Optional)", fontSize = 10.sp) },
                                            placeholder = { Text("dd-MM-yyyy", fontSize = 10.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                    }

                                    if (customDateErrorText != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = customDateErrorText!!,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 11.sp,
                                            modifier = Modifier.testTag("custom_date_error_text")
                                        )
                                    }

                                    val parsedStart = DateTimeUtils.parseDateDdMmYyyy(customStartDateText)
                                    val parsedEnd = if (customEndDateText.isNotBlank()) DateTimeUtils.parseDateDdMmYyyy(customEndDateText) else null
                                    val isCustomSelectionValid = parsedStart != null && customDateErrorText == null && (customEndDateText.isBlank() || (parsedEnd != null && !parsedEnd.before(parsedStart)))

                                    if (isCustomSelectionValid) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                                try {
                                                    exportFileLauncher.launch("POS_Sales_Export_$timestamp.xlsx")
                                                } catch (e: Exception) {
                                                    viewModel.exportSalesToExcel { /* handled in viewModel */ }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(38.dp)
                                                .testTag("btn_export_custom_xlsx"),
                                            shape = RoundedCornerShape(6.dp),
                                            enabled = !isExporting && !isImporting
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FileDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "এক্সপোর্ট XLSX",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Specific Order ID & Customer Filter Row (Collapsible)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showAdvancedFilters = !showAdvancedFilters },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showAdvancedFilters) {
                                        if (language == "bn") "নির্দিষ্ট ফিল্টার লুকান ▲" else "Hide Detailed Filters ▲"
                                    } else {
                                        if (language == "bn") "অর্ডার বা কাস্টমার নির্দিষ্ট ফিল্টার ▼" else "Order ID / Customer Filters ▼"
                                    },
                                    fontSize = 11.sp
                                )
                            }

                            if (historyQuery.isNotBlank() || historyOrderIdFilter.isNotBlank() || historyCustomerFilter.isNotBlank() || dateFilter != DateFilterMode.ALL) {
                                TextButton(
                                    onClick = { viewModel.clearAllFilters() },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = Localization.get(Localization.Key.FILTER_CLEAR_ALL, language),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = showAdvancedFilters) {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = historyOrderIdFilter,
                                        onValueChange = { viewModel.setOrderIdFilter(it) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("filter_order_id_input"),
                                        placeholder = { Text("Filter Order ID (#INV)", fontSize = 11.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = historyCustomerFilter,
                                        onValueChange = { viewModel.setCustomerFilter(it) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("filter_customer_input"),
                                        placeholder = { Text("Customer / Phone", fontSize = 11.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // XLSX Branch Synchronization (Offline Multi-Branch Exchange - Collapsible)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("xlsx_branch_sync_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showXlsxSync = !showXlsxSync },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Localization.string(Localization.Key.XLSX_SYNC_TITLE, language),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isImporting || isExporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Icon(
                                    imageVector = if (showXlsxSync) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showXlsxSync) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = showXlsxSync) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    text = Localization.string(Localization.Key.XLSX_SYNC_DESC, language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                            exportFileLauncher.launch("POS_Sales_Export_$timeStamp.xlsx")
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_export_xlsx"),
                                        shape = RoundedCornerShape(8.dp),
                                        enabled = !isExporting && !isImporting
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = Localization.string(Localization.Key.BTN_EXPORT_EXCEL, language),
                                            fontSize = 12.sp
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            importFileLauncher.launch(
                                                arrayOf(
                                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                    "application/octet-stream",
                                                    "*/*"
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_import_xlsx"),
                                        shape = RoundedCornerShape(8.dp),
                                        enabled = !isExporting && !isImporting
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FileUpload,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = Localization.string(Localization.Key.BTN_IMPORT_EXCEL, language),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Low Database Capacity Warning (Phase 6)
            if (remainingCapacity <= 1000) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("history_capacity_warning_card"),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Localization.get(Localization.Key.DB_CAPACITY_WARNING_TITLE, language),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Localization.get(Localization.Key.DB_CAPACITY_WARNING_MSG, language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Header for transactions list with pagination info
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val startRecord = if (totalCount == 0) 0 else (currentPage * pageSize + 1)
                    val endRecord = min(totalCount, (currentPage + 1) * pageSize)
                    Column {
                        Text(
                            text = "${Localization.get(Localization.Key.HISTORY_TITLE, language)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (totalCount > 0) {
                                "Showing $startRecord-$endRecord of $totalCount"
                            } else {
                                "0 records"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Page Size Selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Size: ", style = MaterialTheme.typography.labelSmall)
                        listOf(25, 50, 100).forEach { size ->
                            FilterChip(
                                selected = pageSize == size,
                                onClick = { viewModel.setPageSize(size) },
                                label = { Text("$size", fontSize = 11.sp) },
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .height(32.dp)
                            )
                        }
                    }
                }
            }

            // Invoices Paged List
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }
            } else if (invoices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = Localization.get(Localization.Key.NO_RECORDS_FOUND, language),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (historyQuery.isNotBlank() || historyOrderIdFilter.isNotBlank() || historyCustomerFilter.isNotBlank() || dateFilter != DateFilterMode.ALL) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = { viewModel.clearAllFilters() }) {
                                    Text(Localization.get(Localization.Key.FILTER_CLEAR_ALL, language))
                                }
                            }
                        }
                    }
                }
            } else {
                items(invoices, key = { it.orderId }) { invoice ->
                    val isExpanded = expandedOrderId == invoice.orderId
                    val cachedLines = orderLinesCache[invoice.orderId] ?: if (isExpanded) expandedLines else emptyList()

                    HistoryInvoiceCard(
                        invoice = invoice,
                        isExpanded = isExpanded,
                        lines = cachedLines,
                        language = language,
                        onToggleExpand = { viewModel.toggleOrderExpansion(invoice.orderId) },
                        onSelectForDetails = {
                            // Phase 3 Stage A: Direct saved voucher preview on card tap
                            viewModel.openInvoicePreview(invoice)
                        },
                        onViewReceipt = { viewModel.openInvoicePreview(invoice) },
                        onSharePdf = { viewModel.generateAndShareInvoicePdf(context, invoice, cachedLines) },
                        onDelete = { invoiceToDelete = invoice },
                        onEdit = { invoiceToEdit = invoice }
                    )
                }

                // Pagination Navigation Controls
                item {
                    val totalPages = if (totalCount == 0) 1 else ((totalCount - 1) / pageSize) + 1
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.prevPage() },
                                enabled = currentPage > 0,
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("pagination_prev_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Page",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Localization.get(Localization.Key.BTN_PREV_PAGE, language), fontSize = 11.sp)
                            }

                            Text(
                                text = "${Localization.get(Localization.Key.PAGE_LABEL, language)} ${currentPage + 1} / $totalPages",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            OutlinedButton(
                                onClick = { viewModel.nextPage() },
                                enabled = (currentPage + 1) * pageSize < totalCount,
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("pagination_next_btn")
                            ) {
                                Text(Localization.get(Localization.Key.BTN_NEXT_PAGE, language), fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Page",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                item {
                    AnalyticsDashboardView(
                        language = language,
                        dateFilter = dateFilter,
                        analyticsGrouping = analyticsGrouping,
                        analyticsMetric = analyticsMetric,
                        analyticsDataPoints = analyticsDataPoints,
                        chartDataPoints = chartDataPoints,
                        aggregatedOrders = aggregatedOrders,
                        aggregatedRawPaisa = aggregatedRawPaisa,
                        aggregatedRoundedBdt = aggregatedRoundedBdt,
                        topSellingItems = topSellingItems,
                        onSelectDateFilter = { viewModel.setDateFilter(it) },
                        onOpenCustomDateDialog = { showCustomDateDialog = true },
                        onSelectGrouping = { viewModel.setAnalyticsGrouping(it) },
                        onSelectMetric = { viewModel.setAnalyticsMetric(it) }
                    )
                }
            }
        }
    }

    // Floating Action Button for New Invoice (inside BoxScope)
    FloatingActionButton(
            onClick = onOpenNewInvoice,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("fab_new_invoice"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Invoice"
            )
        }
    }

    // Dedicated Invoice Details Dialog (Phase 3)
    if (selectedInvoiceForDetails != null) {
        val target = selectedInvoiceForDetails!!
        val cachedLines = orderLinesCache[target.orderId] ?: (if (expandedOrderId == target.orderId) expandedLines else emptyList())
        InvoiceDetailsDialog(
            invoice = target,
            lines = cachedLines,
            language = language,
            onDismiss = { selectedInvoiceForDetails = null },
            onPreviewPrint = {
                viewModel.openInvoicePreview(target)
                selectedInvoiceForDetails = null
            },
            onSharePdf = {
                viewModel.generateAndShareInvoicePdf(context, target, cachedLines)
            },
            onEdit = {
                val toEdit = target
                selectedInvoiceForDetails = null
                invoiceToEdit = toEdit
            },
            onDelete = {
                val toDel = target
                selectedInvoiceForDetails = null
                invoiceToDelete = toDel
            }
        )
    }

    // Safe Edit Dialog for Invoice Details (from card or direct preview)
    val activeInvoiceToEdit = invoiceToEdit ?: vmInvoiceToEdit
    if (activeInvoiceToEdit != null) {
        val target = activeInvoiceToEdit
        EditInvoiceDialog(
            invoice = target,
            language = language,
            onDismiss = {
                invoiceToEdit = null
                viewModel.closeEditInvoice()
            },
            onSave = { updatedCustomer, updatedNote ->
                viewModel.updateInvoiceDetails(target.orderId, updatedCustomer, updatedNote)
                invoiceToEdit = null
                viewModel.closeEditInvoice()
            }
        )
    }

    // Safe Delete Confirmation Dialog (from card or direct preview)
    val activeInvoiceToDelete = invoiceToDelete ?: vmInvoiceToDelete
    if (activeInvoiceToDelete != null) {
        val target = activeInvoiceToDelete
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingInvoice) {
                    invoiceToDelete = null
                    viewModel.closeDeleteInvoice()
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = Localization.get(Localization.Key.CONFIRM_DELETE_TRANSACTION_TITLE, language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                val isBn = language == "bn"
                Text(
                    text = if (isBn) {
                        "আপনি কি নিশ্চিত যে ভাউচার #${target.orderId} মুছে ফেলতে চান?\nএই পরিবর্তনটি আর পূর্বাবস্থায় ফিরিয়ে আনা যাবে না।"
                    } else {
                        "Are you sure you want to delete voucher #${target.orderId}?\nThis action cannot be undone."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInvoice(target.orderId) {
                            invoiceToDelete = null
                            viewModel.closeDeleteInvoice()
                        }
                    },
                    enabled = !isDeletingInvoice,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_btn")
                ) {
                    Text(Localization.get(Localization.Key.BTN_DELETE_TRANSACTION, language))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        invoiceToDelete = null
                        viewModel.closeDeleteInvoice()
                    },
                    enabled = !isDeletingInvoice
                ) {
                    Text(Localization.get(Localization.Key.CANCEL, language))
                }
            }
        )
    }

    // Custom Date Range Dialog
    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            language = language,
            onDismiss = { showCustomDateDialog = false },
            onApply = { startMs, endMs ->
                viewModel.setCustomDateRange(startMs, endMs)
                showCustomDateDialog = false
            }
        )
    }

    // XLSX Sync & Import Summary Dialog
    importSummary?.let { summary ->
        ImportSummaryDialog(
            summary = summary,
            language = language,
            onDismiss = { viewModel.clearImportSummary() }
        )
    }
}

@Composable
fun KpiSummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color
) {
    Card(
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HistoryInvoiceCard(
    invoice: InvoiceEntity,
    isExpanded: Boolean,
    lines: List<InvoiceLineEntity>,
    language: String,
    onToggleExpand: () -> Unit,
    onSelectForDetails: () -> Unit = onToggleExpand,
    onViewReceipt: () -> Unit,
    onSharePdf: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val formattedDate = remember(invoice.dateTime) { dateFormat.format(Date(invoice.dateTime)) }
    val isBn = language == "bn"
    val dueAmountBdt = invoice.roundedTotalBdt - invoice.paidAmountBdt

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelectForDetails() }
            .testTag("invoice_card_${invoice.orderId}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Order ID, Status Badge, Branch, Total Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "#${invoice.orderId}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    // Material 3 Status Badge (Green Paid / Red Due)
                    val badgeText = if (invoice.isPaid) {
                        if (isBn) "পরিশোধ" else "PAID"
                    } else {
                        if (isBn) "বাকি" else "DUE"
                    }
                    val badgeColor = if (invoice.isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor,
                        modifier = Modifier.testTag("status_badge_${invoice.orderId}")
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Branch: ${invoice.branchCode}",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    }
                }

                // Rounded Total display prominently
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)} ৳",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isBn) "জমা: ${invoice.paidAmountBdt} ৳" else "Paid: ${invoice.paidAmountBdt} ৳",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = if (invoice.isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("paid_amount_${invoice.orderId}")
                            )
                            if (!invoice.isPaid || dueAmountBdt > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isBn) "বাকি: $dueAmountBdt ৳" else "Due: $dueAmountBdt ৳",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("due_amount_${invoice.orderId}")
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sub-row: Customer info & Date Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1.5f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (invoice.customerInfo.isNotBlank()) invoice.customerInfo else "(Walk-in Customer)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dedicated Compact Metadata Row: Operator & Item Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Op: ${invoice.operatorName}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (lines.isNotEmpty()) {
                    Text(
                        text = "${lines.size} ${Localization.get(Localization.Key.ITEMS_COUNT, language)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Expanded Inline Details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = Localization.get(Localization.Key.INLINE_ITEMS_DETAILS, language),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (lines.isEmpty()) {
                        Text(
                            text = "Loading items...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        lines.forEach { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(
                                        text = line.itemName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "${line.quantityStr} ${line.unit} @ ${FinancialCalculationEngine.formatPaisaToBdt(line.unitPricePaisa)} ৳",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${FinancialCalculationEngine.formatPaisaToBdt(line.lineTotalPaisa)} ৳",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Raw Subtotal: ${FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)} ৳",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Rounded Payable: ${FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)} ৳",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val roundingDiff = (invoice.roundedTotalBdt * 100L) - invoice.rawTotalPaisa
                    Text(
                        text = "Rounding Adjustment: ${if (roundingDiff >= 0) "+" else ""}${FinancialCalculationEngine.formatPaisaToBdt(roundingDiff)} ৳",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Global Transaction ID: ${invoice.globalTransactionId}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiptDetailsDialog(
    invoice: InvoiceEntity,
    lines: List<InvoiceLineEntity>,
    receiptText: String,
    language: String,
    logoUri: String? = null,
    onClose: () -> Unit,
    onReprint: () -> Unit,
    onSharePdf: (() -> Unit)? = null,
    onDelete: () -> Unit = {}
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val invoiceTitle = if (language == "bn") "ইনভয়েস #${invoice.orderId}" else "Invoice #${invoice.orderId}"
                        val walkInLabel = if (language == "bn") Localization.get(Localization.Key.VOUCHER_WALK_IN, "bn") else Localization.get(Localization.Key.VOUCHER_WALK_IN, "en")
                        val customerLabel = if (language == "bn") Localization.get(Localization.Key.VOUCHER_CUSTOMER, "bn") else Localization.get(Localization.Key.VOUCHER_CUSTOMER, "en")
                        val customerVal = if (invoice.customerInfo.isNotBlank()) invoice.customerInfo else walkInLabel
                        Text(
                            text = invoiceTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$customerLabel: $customerVal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Scrollable Slip Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val density = LocalDensity.current
                            val logoSizeDp = with(density) { 100.toDp() }
                            // Official canonical logo at top of voucher
                            Box(
                                modifier = Modifier
                                    .size(logoSizeDp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CompanyLogoView(
                                    logoUri = logoUri,
                                    modifier = Modifier.fillMaxSize(),
                                    fallback = {
                                        Icon(
                                            imageVector = Icons.Default.Business,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val is80mm = (receiptText.lines().firstOrNull()?.length ?: 32) >= 40
                            val receiptFontSize = if (is80mm) 9.5.sp else 11.sp
                            val receiptLineHeight = if (is80mm) 13.5.sp else 15.sp

                            if (receiptText.isBlank()) {
                                Text(
                                    text = if (language == "bn") "ভাউচার লোড হচ্ছে..." else "Loading receipt text...",
                                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = receiptFontSize,
                                    lineHeight = receiptLineHeight,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                ReceiptFormattedBody(
                                    bodyText = receiptText,
                                    is80mm = is80mm,
                                    receiptFontSize = receiptFontSize,
                                    receiptLineHeight = receiptLineHeight,
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    separatorColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    explicitLines = lines,
                                    language = language
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions: Arranged VERTICALLY (Delete Voucher & PDF)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("dialog_delete_invoice_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Localization.get(Localization.Key.BTN_DELETE_TRANSACTION, language))
                    }

                    if (onSharePdf != null) {
                        OutlinedButton(
                            onClick = onSharePdf,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("dialog_share_pdf_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "PDF",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (language == "bn") "পিডিএফ" else "PDF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDateRangeDialog(
    language: String,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    var fromDateText by remember { mutableStateOf("") }
    var toDateText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = Localization.get(Localization.Key.SELECT_CUSTOM_DATE_RANGE, language),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = Localization.get(Localization.Key.ENTER_DATE_RANGE_HELP, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = fromDateText,
                    onValueChange = {
                        fromDateText = it
                        errorMessage = null
                    },
                    label = { Text(Localization.get(Localization.Key.CUSTOM_DATE_FROM, language), fontSize = 11.sp) },
                    placeholder = { Text("dd-MM-yyyy (e.g. 18-11-2025)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_date_from_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = toDateText,
                    onValueChange = {
                        toDateText = it
                        errorMessage = null
                    },
                    label = { Text(Localization.get(Localization.Key.CUSTOM_DATE_TO, language), fontSize = 11.sp) },
                    placeholder = { Text("dd-MM-yyyy (e.g. 18-11-2025)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_date_to_input")
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("custom_date_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedFrom = DateTimeUtils.parseDateDdMmYyyy(fromDateText)
                    val parsedTo = DateTimeUtils.parseDateDdMmYyyy(toDateText)
                    if (parsedFrom == null || parsedTo == null) {
                        errorMessage = Localization.get(Localization.Key.INVALID_DATE_FORMAT, language)
                        return@Button
                    }
                    val calFrom = Calendar.getInstance().apply {
                        time = parsedFrom
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val calTo = Calendar.getInstance().apply {
                        time = parsedTo
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    if (calFrom.timeInMillis > calTo.timeInMillis) {
                        errorMessage = Localization.get(Localization.Key.DATE_RANGE_ORDER_ERROR, language)
                        return@Button
                    }
                    onApply(calFrom.timeInMillis, calTo.timeInMillis)
                },
                modifier = Modifier.testTag("apply_custom_date_btn")
            ) {
                Text(Localization.get(Localization.Key.BTN_APPLY, language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.get(Localization.Key.CANCEL, language))
            }
        }
    )
}

@Composable
fun EditInvoiceDialog(
    invoice: InvoiceEntity,
    language: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var customerText by remember { mutableStateOf(invoice.customerInfo) }
    var noteText by remember { mutableStateOf(invoice.footerNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${Localization.get(Localization.Key.EDIT_INVOICE_TITLE, language)} #${invoice.orderId}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = Localization.get(Localization.Key.EDIT_INVOICE_READONLY_NOTICE, language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Total: ৳${invoice.roundedTotalBdt}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = customerText,
                    onValueChange = { customerText = it },
                    label = { Text(Localization.get(Localization.Key.EDIT_INVOICE_CUSTOMER_LABEL, language), fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_invoice_customer_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(Localization.get(Localization.Key.EDIT_INVOICE_NOTE_LABEL, language), fontSize = 11.sp) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_invoice_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(customerText, noteText) },
                modifier = Modifier.testTag("save_invoice_edit_btn")
            ) {
                Text(Localization.get(Localization.Key.SAVE_CHANGES, language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.get(Localization.Key.CANCEL, language))
            }
        }
    )
}

@Composable
fun InvoiceDetailsDialog(
    invoice: InvoiceEntity,
    lines: List<InvoiceLineEntity>,
    language: String,
    onDismiss: () -> Unit,
    onPreviewPrint: () -> Unit,
    onSharePdf: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isBn = language == "bn"
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val formattedDate = remember(invoice.dateTime) { dateFormat.format(Date(invoice.dateTime)) }
    val dueAmountBdt = invoice.roundedTotalBdt - invoice.paidAmountBdt

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("invoice_details_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header: Title and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isBn) "ইনভয়েস বিস্তারিত #${invoice.orderId}" else "Invoice Details #${invoice.orderId}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status Badge & Operator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (invoice.isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("details_status_badge_${invoice.orderId}")
                    ) {
                        Text(
                            text = if (invoice.isPaid) {
                                if (isBn) "পরিশোধিত (PAID)" else "PAID"
                            } else {
                                if (isBn) "বাকি (DUE)" else "DUE"
                            },
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "Op: ${invoice.operatorName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Customer & Footer Note Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "${if (isBn) "ক্রেতা" else "Customer"}: ${if (invoice.customerInfo.isNotBlank()) invoice.customerInfo else if (isBn) "সাধারণ ক্রেতা" else "Walk-in"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                        )
                        if (invoice.footerNote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${if (isBn) "নোট" else "Note"}: ${invoice.footerNote}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Itemized lines
                Text(
                    text = if (isBn) "পণ্য / সেবাসমূহ" else "Itemized Lines",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (lines.isEmpty()) {
                    Text(
                        text = if (isBn) "লোড হচ্ছে..." else "Loading items...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else {
                    lines.forEach { line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text(
                                    text = line.itemName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "${line.quantityStr} ${line.unit} @ ${FinancialCalculationEngine.formatPaisaToBdt(line.unitPricePaisa)} ৳",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${FinancialCalculationEngine.formatPaisaToBdt(line.lineTotalPaisa)} ৳",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Financial Breakdown (Raw subtotal, rounded payable, rounding adjustment, paid, due)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (isBn) "মোট সাবটোটাল:" else "Raw Subtotal:", style = MaterialTheme.typography.bodySmall)
                    Text(text = "${FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa)} ৳", style = MaterialTheme.typography.bodySmall)
                }

                val roundingDiff = (invoice.roundedTotalBdt * 100L) - invoice.rawTotalPaisa
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (isBn) "রাউন্ডিং সমন্বয়:" else "Rounding Adjustment:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "${if (roundingDiff >= 0) "+" else ""}${FinancialCalculationEngine.formatPaisaToBdt(roundingDiff)} ৳",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isBn) "সর্বমোট প্রদেয়:" else "Rounded Payable:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt)} ৳",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Paid & Due Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (isBn) "পরিশোধিত (Paid):" else "Paid Amount:", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        text = "${invoice.paidAmountBdt} ৳",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (invoice.isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (isBn) "বাকি (Due):" else "Remaining Due:", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        text = "$dueAmountBdt ৳",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (dueAmountBdt > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                // 1. Preview / Print button (Mandatory preview first!)
                Button(
                    onClick = onPreviewPrint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("details_preview_print_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBn) "প্রিভিউ ও প্রিন্ট" else "Preview / Print")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Row: PDF Share & Edit Metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSharePdf,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("details_share_pdf_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBn) "পিডিএফ" else "PDF", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("details_edit_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBn) "এডিট" else "Edit", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Delete Button (Requires confirmation dialog)
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("details_delete_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Localization.get(Localization.Key.BTN_DELETE_TRANSACTION, language), fontSize = 12.sp)
                }
            }
        }
    }
}

