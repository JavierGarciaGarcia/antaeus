package io.pleo.antaeus.core.services

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.kotlin.circuitbreaker.circuitBreaker
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.constants.DEFAULT_PAGE_SIZE
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoicePage
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {

    private val MIN_DELAY = 100
    private val MAX_DELAY = 1000L
    private val logger = KotlinLogging.logger {}

    fun processInvoicesByStatus(status: String, pageSize: Int = DEFAULT_PAGE_SIZE): Boolean {
        var result = true
        var currentPage: InvoicePage? = null
        while(!hasProcessedEverything(currentPage)) {
            currentPage = getNextStatusPage(status, pageSize, getCurrentMarker(currentPage))
            result = result && processInvoices(currentPage.invoices)
        }
        return result
    }

    fun proccessInvoicesByCustomer(customer: Int,
                                   status: String = InvoiceStatus.PENDING.toString(),
                                   pageSize: Int = DEFAULT_PAGE_SIZE): Boolean {
        var result = true
        var currentPage: InvoicePage? = null
        while(!hasProcessedEverything(currentPage)) {
            currentPage = getNextCustomerPage(customer, status, pageSize, getCurrentMarker(currentPage))
            result = result && processInvoices(currentPage.invoices)
        }
        return result
    }

    fun processInvoice(id: Int): Boolean {
        return processInvoices(getInvoices(id))
    }

    private fun getInvoices(id:Int): List<Invoice> =
        listOf(invoiceService.fetch(id))
            .filter { it.status != InvoiceStatus.PAID }

    private fun getNextStatusPage(status: String, pageSize: Int, marker: Int?): InvoicePage =
        invoiceService.fetchPageByStatus(status = status, marker = marker, pageSize = pageSize)
    private fun getNextCustomerPage(customer: Int, status: String, pageSize: Int, marker: Int?): InvoicePage =
        invoiceService.fetchPageByCustomer(customer = customer, status = status, marker = marker, pageSize = pageSize)
    private fun hasProcessedEverything(currentPage: InvoicePage?): Boolean = currentPage?.isLast ?: false
    private fun getCurrentMarker(invoicePage: InvoicePage?) = invoicePage?.marker

    private fun processInvoices(invoices: List<Invoice>): Boolean = runBlocking {
        return@runBlocking processInvoicesFlow(invoices)
    }

    suspend private fun processInvoicesFlow(invoices: List<Invoice>): Boolean {
        var result = true
        invoices.asFlow()
            .map { processInvoice(it) }
            .retryWhen { cause, attempt ->
                delay(getDelay(attempt))
                attempt < 3 && cause is NetworkException
            }
            .circuitBreaker(CircuitBreaker.ofDefaults("invoicePayment"))
            .catch {
                logger.error(it) { "Exception processing the invoices" }
                result = false
            }
            .flowOn(Dispatchers.IO)
            .collect { result = result && it }
        return result
    }

    private fun processInvoice(invoice: Invoice): Boolean {
        var result = true
        logger.info{"Processing invoice ${invoice.id}"}
        try {
            result = paymentProvider.charge(invoice)
            when (result) {
                true -> updateInvoiceStatus(invoice.id, InvoiceStatus.PAID.toString())
                else -> updateInvoiceStatus(invoice.id, InvoiceStatus.PENDING.toString())
            }
        } catch(e: NetworkException) {
            logger.error{"Network error proccessing ${invoice.id}"}
            throw e
        } catch (e: Exception) {
            val newStatus = when (e) {
                is CustomerNotFoundException -> InvoiceStatus.MISSING_CUSTOMER.toString()
                is CurrencyMismatchException -> InvoiceStatus.CURRENCY_MISMATCH.toString()
                else -> InvoiceStatus.PENDING.toString()
            }
            updateInvoiceStatus(invoice.id, newStatus)
            result = false
        }
        logger.info{"Processed invoice ${invoice.id} with result ${result}"}
        return result
    }

    private fun getDelay(attempt: Long) = (MIN_DELAY * attempt).toLong().coerceAtMost(MAX_DELAY)

    private fun updateInvoiceStatus(id: Int, status: String = InvoiceStatus.PAID.toString()) = invoiceService.updateStatus(id, status)
}
