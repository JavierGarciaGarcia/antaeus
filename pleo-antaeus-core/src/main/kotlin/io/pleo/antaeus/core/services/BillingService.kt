package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.runBlocking

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {

    private val MIN_DELAY = 100
    private val MAX_DELAY = 1000L

    fun processInvoicesByStatus(status: String): Boolean {
        return processInvoices(getInvoices(status))
    }

    fun processInvoice(id: Int): Boolean {
        return processInvoices(getInvoices(id))
    }

    private fun getInvoices(id:Int): List<Invoice> = listOf(invoiceService.fetch(id))

    private fun getInvoices(status: String): List<Invoice> = invoiceService.fetchByStatus(status)

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
            .catch {
                log("Exception ${it} when process invoices")
                result = false
            }
            .collect { result = result && it }
        return result
    }

    private fun processInvoice(invoice: Invoice): Boolean {
        var result = true
        log("Processing invoice ${invoice.id}")
        try {
            result = paymentProvider.charge(invoice)
            when (result) {
                true -> updateInvoiceStatus(invoice.id, InvoiceStatus.PAID.toString())
                else -> updateInvoiceStatus(invoice.id, InvoiceStatus.PENDING.toString())
            }
        } catch(e: NetworkException) {
            log("Network error proccessing ${invoice.id}")
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
        log("Processed invoice ${invoice.id} with result ${result}")
        return result
    }

    private fun getDelay(attempt: Long) = (MIN_DELAY * attempt).toLong().coerceAtMost(MAX_DELAY)

    private fun updateInvoiceStatus(id: Int, status: String = InvoiceStatus.PAID.toString()) = invoiceService.updateStatus(id, status)

    fun log(msg: String) = println("[${Thread.currentThread().name}] ${java.time.LocalDateTime.now()} $msg")
}
