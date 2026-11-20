package com.example.pos.data.audit

/**
 * Canonical action types recorded in the append-only audit_logs table.
 */
object AuditActionType {
    const val INVOICE_CREATED = "INVOICE_CREATED"
    const val INVOICE_METADATA_UPDATED = "INVOICE_METADATA_UPDATED"
    const val INVOICE_DELETED = "INVOICE_DELETED"

    const val PAYMENT_COLLECTED_CHECKOUT = "PAYMENT_COLLECTED_CHECKOUT"
    const val PAYMENT_DUE_SETTLED = "PAYMENT_DUE_SETTLED"

    const val PRODUCT_CREATED = "PRODUCT_CREATED"
    const val PRODUCT_UPDATED = "PRODUCT_UPDATED"
    const val PRODUCT_DELETED = "PRODUCT_DELETED"
    const val PRODUCT_REORDERED = "PRODUCT_REORDERED"

    const val CUSTOMER_CREATED = "CUSTOMER_CREATED"
    const val CUSTOMER_UPDATED = "CUSTOMER_UPDATED"
    const val CUSTOMER_DELETED = "CUSTOMER_DELETED"

    const val COMPANY_HEADER_UPDATED = "COMPANY_HEADER_UPDATED"
    const val COMPANY_LOGO_UPDATED = "COMPANY_LOGO_UPDATED"
    const val COMPANY_LOGO_REMOVED = "COMPANY_LOGO_REMOVED"

    const val FOOTER_NOTES_UPDATED = "FOOTER_NOTES_UPDATED"
    const val PRINTER_SETTINGS_UPDATED = "PRINTER_SETTINGS_UPDATED"
    const val APP_PREFERENCES_UPDATED = "APP_PREFERENCES_UPDATED"

    const val XLSX_EXPORTED = "XLSX_EXPORTED"
    const val XLSX_IMPORTED = "XLSX_IMPORTED"

    const val AUTH_REGISTER = "AUTH_REGISTER"
    const val AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS"
    const val AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED"
    const val AUTH_LOGIN_LOCKED = "AUTH_LOGIN_LOCKED"
    const val AUTH_LOGOUT = "AUTH_LOGOUT"
    const val AUTH_PROFILE_UPDATED = "AUTH_PROFILE_UPDATED"
    const val AUTH_PASSWORD_RESET = "AUTH_PASSWORD_RESET"
    const val AUTH_RECOVERY_CODE_REGENERATED = "AUTH_RECOVERY_CODE_REGENERATED"
}

/**
 * Canonical entity_type values stored in audit_logs.
 */
object AuditEntityType {
    const val INVOICE = "INVOICE"
    const val PAYMENT = "PAYMENT"
    const val PRODUCT = "PRODUCT"
    const val CUSTOMER = "CUSTOMER"
    const val COMPANY_HEADER = "COMPANY_HEADER"
    const val APP_SETTINGS = "APP_SETTINGS"
    const val OPERATOR = "OPERATOR"
    const val DATA_EXCHANGE = "DATA_EXCHANGE"

    const val SYSTEM_OPERATOR_ID = "SYSTEM"
    const val SYSTEM_OPERATOR_NAME_EN = "System / Authentication"
    const val SYSTEM_OPERATOR_NAME_BN = "সিস্টেম / অথেনটিকেশন"
}

enum class AuditCategoryFilter(val sqlCode: String) {
    ALL(""),
    INVOICE("INVOICE"),
    PAYMENT("PAYMENT"),
    PRODUCT("PRODUCT"),
    CUSTOMER("CUSTOMER"),
    COMPANY("COMPANY"),
    SETTINGS("SETTINGS"),
    AUTH("AUTH"),
    EXCHANGE("EXCHANGE")
}

enum class AuditDateFilter {
    TODAY,
    YESTERDAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    ALL,
    CUSTOM
}
