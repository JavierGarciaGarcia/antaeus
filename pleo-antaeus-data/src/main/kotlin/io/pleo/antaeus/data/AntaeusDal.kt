/*
    Implements the data access layer (DAL).
    The data access layer generates and executes requests to the database.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */
package io.pleo.antaeus.data

import io.pleo.antaeus.data.constants.DEFAULT_PAGE_SIZE
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoicePage
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.Database

class AntaeusDal(db: Database) {
    private val invoiceDal = InvoiceDal(db)
    private val customerDal = CustomerDal(db)

    fun fetchInvoice(id: Int): Invoice? = invoiceDal.fetchInvoice(id)
    fun fetchInvoices(): List<Invoice> = invoiceDal.fetchInvoices()
    fun fetchInvoicesByStatus(status: InvoiceStatus): List<Invoice> = invoiceDal.fetchInvoicesByStatus(status)
    fun fetchInvoicePagesByStatus(status: InvoiceStatus, pageSize: Int?, marker: Int?): InvoicePage =
        invoiceDal.fetchInvoicesPageByStatus(status, pageSize.let { DEFAULT_PAGE_SIZE }, marker)
    fun fetchInvoicePagesByCustomer(customer: Int, status: InvoiceStatus, pageSize: Int?, marker: Int?): InvoicePage =
        invoiceDal.fetchInvoicesPageByCustomer(customer, status, pageSize.let { DEFAULT_PAGE_SIZE }, marker)
    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? =
        invoiceDal.createInvoice(amount, customer, status)
    fun updateInvoiceStatus(id: Int, newStatus: InvoiceStatus): Invoice? = invoiceDal.updateInvoiceStatus(id, newStatus)

    fun fetchCustomer(id: Int): Customer? = customerDal.fetchCustomer(id)
    fun fetchCustomers(): List<Customer> = customerDal.fetchCustomers()
    fun createCustomer(currency: Currency): Customer? = customerDal.createCustomer(currency)
}
