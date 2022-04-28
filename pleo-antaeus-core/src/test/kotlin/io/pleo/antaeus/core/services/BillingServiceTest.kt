package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.constants.DEFAULT_PAGE_SIZE
import io.pleo.antaeus.models.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BillingServiceTest {
    private val invoiceService = mockk<InvoiceService>()
    private val paymentProvider = mockk<PaymentProvider>()
    private val billingService = BillingService(paymentProvider, invoiceService)

    @Test
    fun `should call paymentProvider with list of pending invoices and update all of them to PAID`() {
        val invoicesPage = expectListOfInvoicesSuitableForBeingPaid("PENDING")
        billingService.processInvoicesByStatus("PENDING")
        for (invoice in invoicesPage.invoices) {
            verify {
                paymentProvider.charge(invoice)
                invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID.toString())
                invoiceService.fetchPageByStatus(status = "PENDING", pageSize = any(), marker = any())
            }
        }
    }

    @Test
    fun `should get the invoice list using pagination and call paymentProvider`() {
        val invoices = expectInvoicePageof1ElementToBePaid("PENDING")
        billingService.processInvoicesByStatus("PENDING", pageSize = 1)
        for (invoice in invoices) {
            verify {
                paymentProvider.charge(invoice)
                invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID.toString())
            }
            verify(exactly = 2) {
                invoiceService.fetchPageByStatus(status = "PENDING", pageSize = 1, marker = any())
            }
        }
    }

    @Test
    fun `should do not call paymentProvider with an already paid invoice`() {
        val invoice = expectAlreadyPaidInvoice(1)
        billingService.processInvoice(invoice.id)
        verify(exactly = 0){
            paymentProvider.charge(invoice)
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


    private fun expectListOfInvoicesSuitableForBeingPaid(status: String): InvoicePage {
        val expectedInvoices = aPageOfInvoices(aListOfInvoices(status = status), true)
        every {
            invoiceService.fetchPageByStatus(status, pageSize = DEFAULT_PAGE_SIZE, marker = null)
        } returns expectedInvoices
        every {
            paymentProvider.charge(any())
        } returns true
        for(invoice in expectedInvoices.invoices) {
            every {
                paymentProvider.charge(invoice)
            } returns true
            every {
                invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID.toString())
            } returns invoice
        }
        return expectedInvoices
    }

    private fun expectInvoicePageof1ElementToBePaid(status: String): List<Invoice> {
        val invoicesList = aListOfInvoices(size = 2, status = status)
        every {
            invoiceService.fetchPageByStatus(status, pageSize = 1, marker = null)
        } returns InvoicePage(
            invoices = listOf(invoicesList.first()),
            isLast = false,
            marker = invoicesList.first().id
        )
        every {
            invoiceService.fetchPageByStatus(status, pageSize = 1, marker = invoicesList.first().id)
        } returns InvoicePage(
            invoices = listOf(invoicesList.last()),
            isLast = true,
            marker = invoicesList.last().id
        )
        every {
            paymentProvider.charge(any())
        } returns true
        for(invoice in invoicesList) {
            every {
                paymentProvider.charge(invoice)
            } returns true
            every {
                invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID.toString())
            } returns invoice
        }
        return invoicesList
    }

    private fun expectAlreadyPaidInvoice(id: Int): Invoice {
        val paidInvoice = anInvoice(id, InvoiceStatus.PAID)
        every {
            invoiceService.fetch(id)
        } returns paidInvoice
        return paidInvoice
    }

    private fun expectMissingCustomerException(invoice: Invoice) {
        every {
            paymentProvider.charge(invoice)
        } throws CustomerNotFoundException(invoice.customerId)

        every {
            invoiceService.fetchPageByStatus(InvoiceStatus.PENDING.toString(), pageSize = DEFAULT_PAGE_SIZE, marker = null)
        } returns InvoicePage(listOf(invoice), isLast = true, marker = invoice.id)
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
            invoiceService.fetchPageByStatus(InvoiceStatus.PENDING.toString(), pageSize = DEFAULT_PAGE_SIZE, marker = null)
        } returns InvoicePage(listOf(invoice), isLast = true, marker = invoice.id)
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

    private fun aPageOfInvoices(invoices: List<Invoice>, isLast: Boolean = true): InvoicePage =
        InvoicePage(invoices, isLast, invoices.last().id)

    private fun aListOfInvoices(size: Int = 2, status: String = InvoiceStatus.PENDING.toString()): List<Invoice> =
            List(size) { anInvoice(it, InvoiceStatus.valueOf(status)) }

    private fun anInvoice(id: Int, status: InvoiceStatus) = Invoice(id, id, Money(BigDecimal.valueOf(1L), Currency.DKK), status)

}