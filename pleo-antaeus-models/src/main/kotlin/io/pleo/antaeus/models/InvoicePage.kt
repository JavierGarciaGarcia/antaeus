package io.pleo.antaeus.models

data class InvoicePage(
    val invoices: List<Invoice>,
    val isLast: Boolean,
    val marker: Int?
)
