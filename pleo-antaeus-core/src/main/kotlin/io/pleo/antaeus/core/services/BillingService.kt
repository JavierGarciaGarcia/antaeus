package io.pleo.antaeus.core.services

import com.github.michaelbull.retry.policy.fullJitterBackoff
import com.github.michaelbull.retry.retry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.kotlin.circuitbreaker.circuitBreaker
import io.pleo.antaeus.core.constants.NUMBER_OF_ATTEMPTS
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {

    private val MIN_DELAY = 100
    private val MAX_DELAY = 1000L
    private val logger = KotlinLogging.logger {}

    fun processInvoicesByStatus(status: InvoiceStatus, pageSize: Int = DEFAULT_PAGE_SIZE): Boolean {
        var result = true
        var currentPage: InvoicePage? = null
        while (!hasProcessedEverything(currentPage)) {
            currentPage = getNextStatusPage(status, pageSize, getCurrentMarker(currentPage))
            result = result && processInvoices(currentPage.invoices)
        }
        return result
    }

    fun proccessInvoicesByCustomer(customer: Int, status: InvoiceStatus = InvoiceStatus.PENDING, pageSize: Int = DEFAULT_PAGE_SIZE): Boolean {
        var result = true
        var currentPage: InvoicePage? = null
        while (!hasProcessedEverything(currentPage)) {
            currentPage = getNextCustomerPage(customer, status, pageSize, getCurrentMarker(currentPage))
            result = result && processInvoices(currentPage.invoices)
        }
        return result
    }

    fun processInvoice(id: Int): Boolean {
        return processInvoices(getInvoices(id))
    }

    private fun getInvoices(id: Int): List<Invoice> =
        listOf(invoiceService.fetch(id))
            .filter { it.status != InvoiceStatus.PAID }

    private fun getNextStatusPage(status: InvoiceStatus, pageSize: Int, marker: Int?): InvoicePage =
        invoiceService.fetchPageByStatus(status = status, marker = marker, pageSize = pageSize)
    private fun getNextCustomerPage(customer: Int, status: InvoiceStatus, pageSize: Int, marker: Int?): InvoicePage =
        invoiceService.fetchPageByCustomer(customer = customer, status = status, marker = marker, pageSize = pageSize)
    private fun hasProcessedEverything(currentPage: InvoicePage?): Boolean = currentPage?.isLast ?: false
    private fun getCurrentMarker(invoicePage: InvoicePage?) = invoicePage?.marker

    private fun processInvoices(invoices: List<Invoice>): Boolean = runBlocking {
        return@runBlocking processInvoicesFlow(invoices)
    }

    private suspend fun processInvoicesFlow(invoices: List<Invoice>): Boolean {
        var result = true
        invoices.asFlow()
            .map { processInvoice(it) }
            .retryWhen() { cause, attempt ->
                delay(getMilisDelay(attempt))
                shouldRetry(attempt, cause)
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
        var result: Boolean
        logger.info { "Processing invoice ${invoice.id}" }
        if (isInvoiceBeingPaid(invoice)) {
            logger.info { "The invoice ${invoice.id} is started to be paid, ignoring it" }
            return true
        } else {
            updateInvoiceStatus(invoice.id, InvoiceStatus.STARTED_PAYMENT)
        }
        var newStatus: InvoiceStatus
        try {
            result = paymentProvider.charge(invoice)
            newStatus = paymentResponse2InvoiceStatus(result)
        } catch (e: NetworkException) {
            logger.error { "Network error processing ${invoice.id}" }
            updateInvoiceStatus(invoice.id, InvoiceStatus.PENDING)
            throw e
        } catch (e: Exception) {
            newStatus = exception2InvoiceStatus(e)
            result = false
        }
        updateInvoiceStatus(invoice.id, newStatus)
        logger.info { "Processed invoice ${invoice.id} with result $result" }
        return result
    }

    private fun isInvoiceBeingPaid(invoice: Invoice): Boolean = invoice.status == InvoiceStatus.STARTED_PAYMENT

    private fun getMilisDelay(attempt: Long) = (MIN_DELAY * attempt).coerceAtMost(MAX_DELAY)

    private fun updateInvoiceStatus(id: Int, status: InvoiceStatus = InvoiceStatus.PAID) = runBlocking {
        retry(fullJitterBackoff(base = 10L, max = 1000L)) {
            invoiceService.updateStatus(id, status)
        }
    }

    private fun paymentResponse2InvoiceStatus(paymentResponse: Boolean): InvoiceStatus = when (paymentResponse) {
        true -> InvoiceStatus.PAID
        false -> InvoiceStatus.PENDING
    }

    private fun exception2InvoiceStatus(exception: Exception): InvoiceStatus = when (exception) {
        is CustomerNotFoundException -> InvoiceStatus.MISSING_CUSTOMER
        is CurrencyMismatchException -> InvoiceStatus.CURRENCY_MISMATCH
        else -> InvoiceStatus.PENDING
    }

    private fun shouldRetry(attempt: Long, cause: Throwable): Boolean {
        val isExpectedException = cause is NetworkException
        val retriesOverThreshold = attempt >= NUMBER_OF_ATTEMPTS
        return !retriesOverThreshold && isExpectedException
    }
}
