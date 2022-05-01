/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.data.constants.DEFAULT_PAGE_SIZE
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoicePage
import io.pleo.antaeus.models.InvoiceStatus

class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
        return dal.fetchInvoices()
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun fetchByStatus(status: InvoiceStatus): List<Invoice> = dal.fetchInvoicesByStatus(status)

    fun fetchPageByStatus(status: InvoiceStatus, pageSize: Int = DEFAULT_PAGE_SIZE, marker: Int?): InvoicePage =
        dal.fetchInvoicePagesByStatus(status, pageSize, marker)

    fun fetchPageByCustomer(
        customer: Int,
        status: InvoiceStatus = InvoiceStatus.PENDING,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        marker: Int?
    ): InvoicePage = dal.fetchInvoicePagesByCustomer(customer, status, pageSize, marker)

    fun updateStatus(id: Int, newStatus: InvoiceStatus): Invoice? = dal.updateInvoiceStatus(id, newStatus)
}
