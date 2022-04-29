/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.StatusNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoicePage
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.data.constants.DEFAULT_PAGE_SIZE

class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
        return dal.fetchInvoices()
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun fetchByStatus(status: String): List<Invoice> {
        val validStatus : InvoiceStatus = translateStatus(status)
        return dal.fetchInvoicesByStatus(validStatus)
    }

    fun fetchPageByStatus(status: String, pageSize: Int = DEFAULT_PAGE_SIZE, marker: Int?): InvoicePage {
        val validStatus : InvoiceStatus = translateStatus(status)
        return dal.fetchInvoicePagesByStatus(validStatus, pageSize, marker)
    }

    fun fetchPageByCustomer(customer: Int, pageSize: Int = DEFAULT_PAGE_SIZE, marker: Int?): InvoicePage =
        dal.fetchInvoicePagesByCustomer(customer, pageSize, marker)

    fun updateStatus(id: Int, newStatus: String): Invoice? {
        return dal.updateInvoiceStatus(id, translateStatus(newStatus))
    }

    private fun translateStatus(status : String): InvoiceStatus {
        val validStatus : InvoiceStatus?
        try {
            validStatus = InvoiceStatus.valueOf(status)
        } catch (e: Exception) {
            throw StatusNotFoundException(status)
        }
        return validStatus
    }
}
