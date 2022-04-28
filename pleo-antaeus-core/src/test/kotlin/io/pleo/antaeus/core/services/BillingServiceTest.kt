package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BillingServiceTest {
    private val invoiceService = mockk<InvoiceService>()
    private val paymentProvider = mockk<PaymentProvider>()
    private val billingService = BillingService(paymentProvider, invoiceService)

    @Test
    fun `should call paymentProvider with list of pending invoices and update all of them to PAID`() {
        val invoices = expectListOfInvoicesSuitableForBeingPaid("PENDING")
        billingService.processInvoicesByStatus("PENDING")
        for (invoice in invoices) {
            verify {
                paymentProvider.charge(invoice)
                invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID.toString())
            }
        }
    }

    @Test
    fun `should update invoice status to missing customer to a invoice list when payment service throws missing customer exception`() {
        val invoice = anInvoice(1, InvoiceStatus.PENDING)
        expectMissingCustomerException(invoice)

        val result = billingService.processInvoicesByStatus(InvoiceStatus.PENDING.toString())

        Assertions.assertEquals(false, result)
        verify {
            paymentProvider.charge(invoice)
            invoiceService.updateStatus(invoice.id, InvoiceStatus.MISSING_CUSTOMER.toString())
        }
    }

    @Test
    fun `should update invoice status to missing customer to a specific invoic when payment service throws missing customer exception`() {
        val invoice = anInvoice(1, InvoiceStatus.PENDING)
        expectMissingCustomerException(invoice)

        val result = billingService.processInvoice(invoice.id)

        Assertions.assertEquals(false, result)
        verify {
            paymentProvider.charge(invoice)
            invoiceService.updateStatus(invoice.id, InvoiceStatus.MISSING_CUSTOMER.toString())
        }
    }

    @Test
    fun `should update invoice status to currency mismatch to an invoice list when payment service throws missing customer exception`() {
        val invoice = anInvoice(1, InvoiceStatus.PENDING)
        expectCurrencyMismatchException(invoice)

        val result = billingService.processInvoicesByStatus(InvoiceStatus.PENDING.toString())

        Assertions.assertEquals(false, result)
        verify {
            paymentProvider.charge(invoice)
            invoiceService.updateStatus(invoice.id, InvoiceStatus.CURRENCY_MISMATCH.toString())
        }
    }

    @Test
    fun `should update invoice status to currency mismatch to a specific invoice when payment service throws missing customer exception`() {
        val invoice = anInvoice(1, InvoiceStatus.PENDING)
        expectCurrencyMismatchException(invoice)

        val result = billingService.processInvoice(invoice.id)

        Assertions.assertEquals(false, result)
        verify {
            paymentProvider.charge(invoice)
            invoiceService.updateStatus(invoice.id, InvoiceStatus.CURRENCY_MISMATCH.toString())
        }
    }

    @Test
    fun `should try and retry calling payment provider and mark as paid`() {
        val invoice = anInvoice(1, InvoiceStatus.PENDING)
        expectNetworkExceptionAndSuccess(invoice)

        val result = billingService.processInvoice(invoice.id)

        Assertions.assertEquals(true, result)
        verify {
            invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID.toString())
        }
        verify(exactly = 2) {
            paymentProvider.charge(invoice)
        }
    }

    @Test
    fun `should try up to maximum number of retries calling payment provider and mark as pending`() {
        val invoice = anInvoice(1, InvoiceStatus.PENDING)
        expectNetworkExceptionAlways(invoice)

        val result = billingService.processInvoice(invoice.id)

        Assertions.assertEquals(false, result)
        verify(exactly = 4) {
            paymentProvider.charge(invoice)
        }
    }


    private fun expectListOfInvoicesSuitableForBeingPaid(status: String): List<Invoice> {
        val expectedInvoices = aListOfInvoices(status = status)
        every {
            invoiceService.fetchByStatus(status)
        } returns expectedInvoices
        every {
            paymentProvider.charge(any())
        } returns true
        for(invoice in expectedInvoices) {
            every {
                paymentProvider.charge(invoice)
            } returns true
            every {
                invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID.toString())
            } returns invoice
        }
        return expectedInvoices
    }

    private fun expectMissingCustomerException(invoice: Invoice) {
        every {
            paymentProvider.charge(invoice)
        } throws CustomerNotFoundException(invoice.customerId)

        every {
            invoiceService.fetchByStatus(InvoiceStatus.PENDING.toString())
        } returns listOf(invoice)
        every {
            invoiceService.fetch(invoice.id)
        } returns invoice
        every {
            invoiceService.updateStatus(invoice.id, InvoiceStatus.MISSING_CUSTOMER.toString())
        } returns invoice
    }

    private fun expectCurrencyMismatchException(invoice: Invoice) {
        every {
            paymentProvider.charge(invoice)
        } throws CurrencyMismatchException(invoice.customerId, invoice.customerId)

        every {
            invoiceService.fetchByStatus(InvoiceStatus.PENDING.toString())
        } returns listOf(invoice)
        every {
            invoiceService.fetch(invoice.id)
        } returns invoice
        every {
            invoiceService.updateStatus(invoice.id, InvoiceStatus.CURRENCY_MISMATCH.toString())
        } returns invoice
    }

    private fun expectNetworkExceptionAndSuccess(invoice: Invoice) {
        every {
            paymentProvider.charge(invoice)
        } throws NetworkException() andThen true
        every {
            invoiceService.fetch(invoice.id)
        } returns invoice
        every {
            invoiceService.updateStatus(invoice.id, InvoiceStatus.PENDING.toString())
        } returns invoice
        every {
            invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID.toString())
        } returns invoice
    }

    private fun expectNetworkExceptionAlways(invoice: Invoice) {
        every {
            paymentProvider.charge(invoice)
        } throws NetworkException()
        every {
            invoiceService.fetch(invoice.id)
        } returns invoice
        every {
            invoiceService.updateStatus(invoice.id, InvoiceStatus.PENDING.toString())
        } returns invoice
    }

    private fun aListOfInvoices(size: Int = 2, status: String = InvoiceStatus.PENDING.toString()): List<Invoice> =
            List(size) { anInvoice(it, InvoiceStatus.valueOf(status)) }

    private fun anInvoice(id: Int, status: InvoiceStatus) = Invoice(id, id, Money(BigDecimal.valueOf(1L), Currency.DKK), status)

}