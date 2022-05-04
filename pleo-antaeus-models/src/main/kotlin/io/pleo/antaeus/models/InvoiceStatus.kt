package io.pleo.antaeus.models

enum class InvoiceStatus {
    PENDING,
    PAID,
    MISSING_CUSTOMER,
    CURRENCY_MISMATCH,
    STARTED_PAYMENT
}
