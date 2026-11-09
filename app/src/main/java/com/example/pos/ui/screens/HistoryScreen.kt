package com.example.pos.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
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
import com.example.pos.core.util.BengaliTransliterationEngine
import com.example.pos.core.util.DateTimeUtils
import com.example.pos.data.Localization
import com.example.pos.data.db.InvoiceEntity
import com.example.pos.data.db.InvoiceLineEntity
import com.example.pos.data.db.ItemSalesSummary
import com.example.pos.data.repository.HistorySortOption
import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.core.util.CompanyLogoManager
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

fun formatHistoryDateTimeBn(timeMillis: Long): String {
    val rawFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
    val formatted = rawFormat.format(Date(timeMillis))
    var s = formatted
    val monthMap = listOf(
        "January" to "জানুয়ারি", "February" to "ফেব্রুয়ারি", "March" to "মার্চ",
        "April" to "এপ্রিল", "May" to "মে", "June" to "জুন",
        "July" to "জুলাই", "August" to "আগস্ট", "September" to "সেপ্টেম্বর",
        "October" to "অক্টোবর", "November" to "নভেম্বর", "December" to "ডিসেম্বর",
        "Jan" to "জানু", "Feb" to "ফেব", "Mar" to "মার্চ", "Apr" to "এপ্রিল",
        "Jun" to "জুন", "Jul" to "জুলাই", "Aug" to "আগস্ট", "Sep" to "সেপ্টে",
        "Oct" to "অক্টো", "Nov" to "নভে", "Dec" to "ডিসে"
    )
    for ((enM, bnM) in monthMap) {
        s = s.replace(enM, bnM)
    }
    s = s.replace("AM", "পূর্বাহ্ন").replace("PM", "অপরাহ্ন")
        .replace("am", "পূর্বাহ্ন").replace("pm", "অপরাহ্ন")
    return DateTimeUtils.toBengaliNumerals(s)
}

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

    val periodDueBdt by viewModel.periodDueBdt.collectAsState()
    val overallDueBdt by viewModel.overallDueBdt.collectAsState()
    val paymentBreakdown by viewModel.paymentBreakdown.collectAsState()
    val totalCollectionBdt by viewModel.totalCollectionBdt.collectAsState()

    var activeHistoryTab by remember { mutableStateOf(0) }

    // UI state for filter dialogs
    var showSortMenu by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var showDateFilterMenu by remember { mutableStateOf(false) }
    var customStartDateText by remember { mutableStateOf("") }
    var customEndDateText by remember { mutableStateOf("") }
    var customDateErrorText by remember { mutableStateOf<String?>(null) }

    val isDeletingInvoice by viewModel.isDeletingInvoice.collectAsState()
    var invoiceToDelete by remember { mutableStateOf<InvoiceEntity?>(null) }
    var invoiceToEdit by remember { mutableStateOf<InvoiceEntity?>(null) }
    val vmInvoiceToEdit by viewModel.invoiceToEdit.collectAsState()
    val vmInvoiceToDelete by viewModel.invoiceToDelete.collectAsState()
    var selectedInvoiceForDetails by remember { mutableStateOf<InvoiceEntity?>(null) }
    val orderIdSuggestions by viewModel.orderIdSuggestions.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.closeDetailsDialogEvent.collect {
            selectedInvoiceForDetails = null
            activeHistoryTab = 0
        }
    }

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
                            val searchInteractionSource = remember { MutableInteractionSource() }
                            BasicTextField(
                                value = historyQuery,
                                onValueChange = { viewModel.setHistoryQuery(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("history_search_input"),
                                interactionSource = searchInteractionSource,
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    OutlinedTextFieldDefaults.DecorationBox(
                                        value = historyQuery,
                                        innerTextField = innerTextField,
                                        enabled = true,
                                        singleLine = true,
                                        visualTransformation = VisualTransformation.None,
                                        interactionSource = searchInteractionSource,
                                        placeholder = {
                                            Text(
                                                text = Localization.get(Localization.Key.SEARCH_ORDERS_PLACEHOLDER, language),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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
                                                IconButton(
                                                    onClick = { viewModel.setHistoryQuery("") },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = "মুছুন",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        container = {
                                            OutlinedTextFieldDefaults.Container(
                                                enabled = true,
                                                isError = false,
                                                interactionSource = searchInteractionSource,
                                                shape = RoundedCornerShape(8.dp),
                                                colors = OutlinedTextFieldDefaults.colors()
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Sort Dropdown Button
                            Box {
                                OutlinedButton(
                                    onClick = { showSortMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(42.dp)
                                        .testTag("sort_menu_btn")
                                 ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "সর্ট",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (sortOption) {
                                            HistorySortOption.DATE_DESC -> "তারিখ ↓"
                                            HistorySortOption.DATE_ASC -> "তারিখ ↑"
                                            HistorySortOption.AMOUNT_DESC -> "টাকা ↓"
                                            HistorySortOption.AMOUNT_ASC -> "টাকা ↑"
                                            HistorySortOption.ORDER_ID_DESC -> "আইডি Z-A"
                                            HistorySortOption.ORDER_ID_ASC -> "আইডি A-Z"
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
                                                text = DateTimeUtils.toBengaliNumerals(suggestionId),
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
                                        contentDescription = "তারিখ ফিল্টার",
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
                                                    customDateErrorText = "শুরুর তারিখটি সঠিক নয় (দিন-মাস-সাল)"
                                                } else if (parsedStart != null && customEndDateText.isNotBlank() && parsedEnd == null) {
                                                    customDateErrorText = "শেষের তারিখটি সঠিক নয় (দিন-মাস-সাল)"
                                                } else if (parsedStart != null && parsedEnd != null && parsedEnd.before(parsedStart)) {
                                                    customDateErrorText = "শেষ তারিখ শুরুর তারিখের আগের হতে পারে না"
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
                                            label = { Text("শুরুর তারিখ", fontSize = 10.sp) },
                                            placeholder = { Text("দিন-মাস-সাল", fontSize = 10.sp) },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        showDatePickerDialog(context, customStartDateText) { selected ->
                                                            customStartDateText = selected
                                                            val parsedStart = DateTimeUtils.parseDateDdMmYyyy(selected)
                                                            val parsedEnd = if (customEndDateText.isNotBlank()) DateTimeUtils.parseDateDdMmYyyy(customEndDateText) else null
                                                            if (parsedStart != null && parsedEnd != null && parsedEnd.before(parsedStart)) {
                                                                customDateErrorText = "শেষ তারিখ শুরুর তারিখের আগের হতে পারে না"
                                                            } else if (parsedStart != null) {
                                                                customDateErrorText = null
                                                                viewModel.setCustomDateRange(parsedStart.time, parsedEnd?.time)
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CalendarToday,
                                                        contentDescription = "শুরুর তারিখ নির্বাচন করুন",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
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
                                                    customDateErrorText = "শেষের তারিখটি সঠিক নয় (দিন-মাস-সাল)"
                                                } else if (parsedStart == null && customStartDateText.isNotBlank()) {
                                                    customDateErrorText = "শুরুর তারিখটি সঠিক নয় (দিন-মাস-সাল)"
                                                } else if (parsedStart != null && parsedEnd != null && parsedEnd.before(parsedStart)) {
                                                    customDateErrorText = "শেষ তারিখ শুরুর তারিখের আগের হতে পারে না"
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
                                            label = { Text("শেষ তারিখ (ঐচ্ছিক)", fontSize = 10.sp) },
                                            placeholder = { Text("দিন-মাস-সাল", fontSize = 10.sp) },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        showDatePickerDialog(context, customEndDateText) { selected ->
                                                            customEndDateText = selected
                                                            val parsedStart = if (customStartDateText.isNotBlank()) DateTimeUtils.parseDateDdMmYyyy(customStartDateText) else null
                                                            val parsedEnd = DateTimeUtils.parseDateDdMmYyyy(selected)
                                                            if (parsedStart != null && parsedEnd != null && parsedEnd.before(parsedStart)) {
                                                                customDateErrorText = "শেষ তারিখ শুরুর তারিখের আগের হতে পারে না"
                                                            } else if (parsedStart != null) {
                                                                customDateErrorText = null
                                                                viewModel.setCustomDateRange(parsedStart.time, parsedEnd?.time)
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CalendarToday,
                                                        contentDescription = "শেষ তারিখ নির্বাচন করুন",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
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
                        val startRecordBn = DateTimeUtils.toBengaliNumerals(startRecord.toString())
                        val endRecordBn = DateTimeUtils.toBengaliNumerals(endRecord.toString())
                        val totalCountBn = DateTimeUtils.toBengaliNumerals(totalCount.toString())
                        Text(
                            text = if (totalCount > 0) {
                                "দেখাচ্ছে $startRecordBn-$endRecordBn, মোট $totalCountBn টি"
                            } else {
                                "০ টি রেকর্ড"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Page Size Selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("পৃষ্ঠা সাইজ: ", style = MaterialTheme.typography.labelSmall)
                        listOf(25, 50, 100).forEach { size ->
                            FilterChip(
                                selected = pageSize == size,
                                onClick = { viewModel.setPageSize(size) },
                                label = { Text(DateTimeUtils.toBengaliNumerals(size.toString()), fontSize = 11.sp) },
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
                            selectedInvoiceForDetails = invoice
                            viewModel.fetchLinesForOrder(invoice.orderId)
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
                                    contentDescription = "পূর্ববর্তী পৃষ্ঠা",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Localization.get(Localization.Key.BTN_PREV_PAGE, language), fontSize = 11.sp)
                            }

                            val currentPgBn = DateTimeUtils.toBengaliNumerals((currentPage + 1).toString())
                            val totalPgsBn = DateTimeUtils.toBengaliNumerals(totalPages.toString())
                            Text(
                                text = "${Localization.get(Localization.Key.PAGE_LABEL, language)} $currentPgBn / $totalPgsBn",
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
                                    contentDescription = "পরবর্তী পৃষ্ঠা",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
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
            periodDueBdt = periodDueBdt,
            overallDueBdt = overallDueBdt,
            paymentBreakdown = paymentBreakdown,
            totalCollectionBdt = totalCollectionBdt,
            onSelectDateFilter = { viewModel.setDateFilter(it) },
            onOpenCustomDateDialog = { showCustomDateDialog = true },
            onSelectGrouping = { viewModel.setAnalyticsGrouping(it) },
            onSelectMetric = { viewModel.setAnalyticsMetric(it) },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
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
                contentDescription = "নতুন ইনভয়েস"
            )
        }
    }

    // Dedicated Invoice Details Dialog (Phase 3)
    val detailsInvoice = selectedInvoiceForDetails?.let { current ->
        invoices.find { it.orderId == current.orderId } ?: current
    }
    if (detailsInvoice != null) {
        val target = detailsInvoice
        val cachedLines = orderLinesCache[target.orderId] ?: (if (expandedOrderId == target.orderId) expandedLines else emptyList())
        InvoiceDetailsDialog(
            invoice = target,
            lines = cachedLines,
            language = language,
            onDismiss = { selectedInvoiceForDetails = null },
            onPreviewPrint = {
                viewModel.openInvoicePreview(target)
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
            },
            onSettleDue = { amountToPay ->
                viewModel.settleInvoiceDue(target.orderId, amountToPay) { updated ->
                    selectedInvoiceForDetails = updated
                }
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
                val orderIdBn = DateTimeUtils.toBengaliNumerals(target.orderId)
                Text(
                    text = "আপনি কি নিশ্চিত যে ভাউচার #$orderIdBn মুছে ফেলতে চান?\nএই পরিবর্তনটি আর পূর্বাবস্থায় ফিরিয়ে আনা যাবে না।",
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
    val formattedDate = remember(invoice.dateTime) { formatHistoryDateTimeBn(invoice.dateTime) }
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
                            text = "#${DateTimeUtils.toBengaliNumerals(invoice.orderId)}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    // Material 3 Status Badge (Green Paid / Red Due)
                    val badgeText = if (invoice.isPaid) "পরিশোধিত" else "বকেয়া"
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
                            text = "শাখা: ${DateTimeUtils.toBengaliNumerals(invoice.branchCode)}",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    }
                }

                // Rounded Total display prominently
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt))} ৳",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "জমা: ${DateTimeUtils.toBengaliNumerals(invoice.paidAmountBdt.toString())} ৳",
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
                                    text = "বাকি: ${DateTimeUtils.toBengaliNumerals(dueAmountBdt.toString())} ৳",
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
                            contentDescription = "প্রসারিত করুন",
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
                    val customerDisplayName = if (invoice.customerInfo.isNotBlank()) {
                        DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(invoice.customerInfo))
                    } else {
                        "(ওয়াক-ইন কাস্টমার)"
                    }
                    Text(
                        text = customerDisplayName,
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
                    val opBn = BengaliTransliterationEngine.transliterate(invoice.operatorName)
                    Text(
                        text = "অপারেটর: $opBn",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (lines.isNotEmpty()) {
                    val itemsCountBn = DateTimeUtils.toBengaliNumerals(lines.size.toString())
                    Text(
                        text = "$itemsCountBn টি পণ্য",
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
                            text = "পণ্য লোড হচ্ছে...",
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
                                        text = BengaliTransliterationEngine.transliterate(line.itemName),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                    val qtyBn = DateTimeUtils.toBengaliNumerals(line.quantityStr)
                                    val unitBn = localizeUnit(line.unit)
                                    val rateBn = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(line.unitPricePaisa))
                                    Text(
                                        text = "$qtyBn $unitBn @ $rateBn ৳",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val lineTotalBn = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(line.lineTotalPaisa))
                                Text(
                                    text = "$lineTotalBn ৳",
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
                        val rawSubtotalBn = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa))
                        val roundedPayableBn = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt))
                        Text(
                            text = "মোট সাবটোটাল: $rawSubtotalBn ৳",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "সর্বমোট প্রদেয়: $roundedPayableBn ৳",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val roundingDiff = (invoice.roundedTotalBdt * 100L) - invoice.rawTotalPaisa
                    val roundingSign = if (roundingDiff >= 0) "+" else ""
                    val roundingDiffBn = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(roundingDiff))
                    Text(
                        text = "রাউন্ডিং সমন্বয়: $roundingSign$roundingDiffBn ৳",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    val gtxIdBn = DateTimeUtils.toBengaliNumerals(invoice.globalTransactionId)
                    Text(
                        text = "গ্লোবাল ট্রানজেকশন আইডি: $gtxIdBn",
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
                        val invoiceTitle = "ইনভয়েস #${DateTimeUtils.toBengaliNumerals(invoice.orderId)}"
                        val customerVal = if (invoice.customerInfo.isNotBlank()) {
                            DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(invoice.customerInfo))
                        } else {
                            "ওয়াক-ইন কাস্টমার"
                        }
                        Text(
                            text = invoiceTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "গ্রাহক: $customerVal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "বন্ধ করুন")
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
                            val context = LocalContext.current
                            val logoSizeDp = 48.dp
                            val isLogoConfigured: Boolean = remember(logoUri, context) {
                                CompanyLogoManager.hasConfiguredLogo(context, logoUri)
                            }
                            // Reserved logo area at top of voucher
                            Box(
                                modifier = Modifier
                                    .size(logoSizeDp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .padding(if (isLogoConfigured) 3.dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLogoConfigured) {
                                    CompanyLogoView(
                                        logoUri = logoUri,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = "লোগো",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val is80mm = (receiptText.lines().firstOrNull()?.length ?: 32) >= 40
                            val receiptFontSize = if (is80mm) 9.5.sp else 11.sp
                            val receiptLineHeight = if (is80mm) 13.5.sp else 15.sp

                            if (receiptText.isBlank()) {
                                Text(
                                    text = "ভাউচার লোড হচ্ছে...",
                                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = receiptFontSize,
                                    lineHeight = receiptLineHeight,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                val invoiceIsPaid = invoice.isPaid || (invoice.roundedTotalBdt - invoice.paidAmountBdt <= 0L)
                                ReceiptFormattedBody(
                                    bodyText = receiptText,
                                    is80mm = is80mm,
                                    receiptFontSize = receiptFontSize,
                                    receiptLineHeight = receiptLineHeight,
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    separatorColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    explicitLines = lines,
                                    language = language,
                                    isFullPaid = invoiceIsPaid
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
                                contentDescription = "পিডিএফ",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পিডিএফ")
                        }
                    }
                }
            }
        }
    }
}

fun showDatePickerDialog(
    context: Context,
    initialDateText: String,
    onDateSelected: (String) -> Unit
) {
    val cal = Calendar.getInstance()
    val parsed = DateTimeUtils.parseDateDdMmYyyy(initialDateText)
    if (parsed != null) {
        cal.time = parsed
    }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    val day = cal.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDay ->
            val formatted = String.format(
                Locale.US,
                "%02d-%02d-%04d",
                selectedDay,
                selectedMonth + 1,
                selectedYear
            )
            onDateSelected(formatted)
        },
        year,
        month,
        day
    ).show()
}

@Composable
fun CustomDateRangeDialog(
    language: String,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    val context = LocalContext.current
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = fromDateText,
                        onValueChange = {
                            fromDateText = it
                            errorMessage = null
                        },
                        label = { Text(Localization.get(Localization.Key.CUSTOM_DATE_FROM, language), fontSize = 10.sp) },
                        placeholder = { Text("dd-MM-yyyy", fontSize = 10.sp) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    showDatePickerDialog(context, fromDateText) { selected ->
                                        fromDateText = selected
                                        errorMessage = null
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "শুরুর তারিখ নির্বাচন করুন",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_date_from_input")
                    )

                    OutlinedTextField(
                        value = toDateText,
                        onValueChange = {
                            toDateText = it
                            errorMessage = null
                        },
                        label = { Text(Localization.get(Localization.Key.CUSTOM_DATE_TO, language), fontSize = 10.sp) },
                        placeholder = { Text("দিন-মাস-সাল", fontSize = 10.sp) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    showDatePickerDialog(context, toDateText) { selected ->
                                        toDateText = selected
                                        errorMessage = null
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "শেষ তারিখ নির্বাচন করুন",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_date_to_input")
                    )
                }
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
                text = "${Localization.get(Localization.Key.EDIT_INVOICE_TITLE, language)} #${DateTimeUtils.toBengaliNumerals(invoice.orderId)}",
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
                            text = "সর্বমোট: ৳${DateTimeUtils.toBengaliNumerals(invoice.roundedTotalBdt.toString())}",
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
    onDelete: () -> Unit,
    onSettleDue: (Long) -> Unit = {}
) {
    val formattedDate = remember(invoice.dateTime) { formatHistoryDateTimeBn(invoice.dateTime) }
    val dueAmountBdt = invoice.roundedTotalBdt - invoice.paidAmountBdt

    var showSettleDueDialog by remember { mutableStateOf(false) }
    var settleAmountInput by remember(invoice.orderId, dueAmountBdt) { mutableStateOf(dueAmountBdt.toString()) }
    var settleError by remember { mutableStateOf<String?>(null) }

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
                            text = "ইনভয়েস বিস্তারিত #${DateTimeUtils.toBengaliNumerals(invoice.orderId)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "বন্ধ করুন")
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
                            text = if (invoice.isPaid) "পরিশোধিত" else "বকেয়া",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    val opBn = BengaliTransliterationEngine.transliterate(invoice.operatorName)
                    Text(
                        text = "অপারেটর: $opBn",
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
                        val custBn = if (invoice.customerInfo.isNotBlank()) {
                            DateTimeUtils.toBengaliNumerals(BengaliTransliterationEngine.transliterate(invoice.customerInfo))
                        } else {
                            "ওয়াক-ইন কাস্টমার"
                        }
                        Text(
                            text = "গ্রাহক: $custBn",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                        )
                        if (invoice.footerNote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "নোট: ${BengaliTransliterationEngine.transliterate(invoice.footerNote)}",
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
                    text = "পণ্য / সেবাসমূহ",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (lines.isEmpty()) {
                    Text(
                        text = "পণ্য লোড হচ্ছে...",
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
                                    text = BengaliTransliterationEngine.transliterate(line.itemName),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                                val qtyBn = DateTimeUtils.toBengaliNumerals(line.quantityStr)
                                val unitBn = localizeUnit(line.unit)
                                val rateBn = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(line.unitPricePaisa))
                                Text(
                                    text = "$qtyBn $unitBn @ $rateBn ৳",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val lineTotalBn = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(line.lineTotalPaisa))
                            Text(
                                text = "$lineTotalBn ৳",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Financial Breakdown (Raw subtotal, rounded payable, rounding adjustment, paid, due)
                val rawSubtotalBn = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(invoice.rawTotalPaisa))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "মোট সাবটোটাল:", style = MaterialTheme.typography.bodySmall)
                    Text(text = "$rawSubtotalBn ৳", style = MaterialTheme.typography.bodySmall)
                }

                val roundingDiff = (invoice.roundedTotalBdt * 100L) - invoice.rawTotalPaisa
                val roundingSign = if (roundingDiff >= 0) "+" else ""
                val roundingDiffBn = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatPaisaToBdt(roundingDiff))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "রাউন্ডিং সমন্বয়:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "$roundingSign$roundingDiffBn ৳",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                val roundedPayableBn = DateTimeUtils.toBengaliNumerals(FinancialCalculationEngine.formatWholeBdtToDisplay(invoice.roundedTotalBdt))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "সর্বমোট প্রদেয়:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$roundedPayableBn ৳",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Paid & Due Breakdown
                val paidBn = DateTimeUtils.toBengaliNumerals(invoice.paidAmountBdt.toString())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "পরিশোধিত (জমা):", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        text = "$paidBn ৳",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (invoice.isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                val dueBn = DateTimeUtils.toBengaliNumerals(dueAmountBdt.toString())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "বাকি (বকেয়া):", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        text = "$dueBn ৳",
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
                    Text("প্রিভিউ ও প্রিন্ট")
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
                        Text("পিডিএফ", fontSize = 12.sp)
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
                        Text("এডিট", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Delete Button (Requires confirmation dialog)
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

                // 5. Outstanding Due Settlement (RED button with readable contrasting text)
                if (dueAmountBdt > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            settleAmountInput = dueAmountBdt.toString()
                            settleError = null
                            showSettleDueDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("details_due_payment_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "বকেয়া পরিশোধ",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showSettleDueDialog) {
        AlertDialog(
            onDismissRequest = {
                showSettleDueDialog = false
                settleError = null
            },
            modifier = Modifier.testTag("due_settlement_dialog"),
            title = {
                Text(
                    text = "বকেয়া পরিশোধ #${DateTimeUtils.toBengaliNumerals(invoice.orderId)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val totalBn = DateTimeUtils.toBengaliNumerals(invoice.roundedTotalBdt.toString())
                    val paidBn = DateTimeUtils.toBengaliNumerals(invoice.paidAmountBdt.toString())
                    val dueBn = DateTimeUtils.toBengaliNumerals(dueAmountBdt.toString())
                    Text(
                        text = "সর্বমোট: $totalBn ৳ | পরিশোধিত: $paidBn ৳",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "বর্তমান বকেয়া: $dueBn ৳",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = settleAmountInput,
                        onValueChange = {
                            settleAmountInput = it
                            settleError = null
                        },
                        label = { Text("পরিশোধের পরিমাণ (৳)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = settleError != null,
                        supportingText = settleError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("due_settlement_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = settleAmountInput.trim().toLongOrNull()
                        val dueBn = DateTimeUtils.toBengaliNumerals(dueAmountBdt.toString())
                        if (parsed == null || parsed <= 0) {
                            settleError = "সঠিক পরিমাণ লিখুন"
                        } else if (parsed > dueAmountBdt) {
                            settleError = "পরিমাণ বকেয়ার ($dueBn ৳) চেয়ে বেশি হতে পারে না"
                        } else {
                            showSettleDueDialog = false
                            settleError = null
                            onSettleDue(parsed)
                        }
                    },
                    modifier = Modifier.testTag("confirm_due_settlement_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("পরিশোধ নিশ্চিত করুন")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSettleDueDialog = false
                        settleError = null
                    },
                    modifier = Modifier.testTag("cancel_due_settlement_btn")
                ) {
                    Text(Localization.get(Localization.Key.CANCEL, language))
                }
            }
        )
    }
}

