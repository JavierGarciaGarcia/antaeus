package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoicePage
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.function.Executable
import java.math.BigDecimal

class InvoiceServiceTest {
    private val dal = mockk<AntaeusDal> {
        every { fetchInvoice(404) } returns null
        every { fetchInvoicesByStatus(InvoiceStatus.PAID) } returns
            listOf(Invoice(1, 1, Money(BigDecimal.valueOf(1L), Currency.DKK), InvoiceStatus.PAID))
    }

    private fun anInvoicePage(status: InvoiceStatus, isLast: Boolean, pageSize: Int): InvoicePage =
        InvoicePage(
            invoices = List(pageSize) { Invoice(it, it, Money(BigDecimal.valueOf(1L), Currency.DKK), InvoiceStatus.PENDING) },
            isLast = isLast,
            marker = pageSize
        )

    private val invoiceService = InvoiceService(dal = dal)

    @Test
    fun `will throw if invoice is not found`() {
        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(404)
        }
    }

    @Test
    fun `will return invoice by status`() {
        val invoices = invoiceService.fetchByStatus(InvoiceStatus.PAID)
        Assertions.assertAll(
            Executable { Assertions.assertEquals(1, invoices.size) },
            Executable { Assertions.assertEquals(1, invoices.get(0).id) }
        )
    }

    @Test
    fun `will return 2 pages of PENDING invoices`() {

        every { dal.fetchInvoicePagesByStatus(InvoiceStatus.PENDING, 50, null) } returns
            anInvoicePage(InvoiceStatus.PENDING, false, 50)

        every { dal.fetchInvoicePagesByStatus(InvoiceStatus.PENDING, 50, 49) } returns
            anInvoicePage(InvoiceStatus.PENDING, true, 50)

        val page1 = invoiceService.fetchPageByStatus(status = InvoiceStatus.PENDING, pageSize = 50, marker = null)
        val page2 = invoiceService.fetchPageByStatus(status = InvoiceStatus.PENDING, pageSize = 50, marker = page1.invoices.last().id)
        Assertions.assertAll(
            Executable { Assertions.assertEquals(50, page1.invoices.size) },
            Executable { Assertions.assertFalse(page1.isLast) },
            Executable { Assertions.assertEquals(50, page2.invoices.size) },
            Executable { Assertions.assertTrue(page2.isLast) }
        )
    }

    @Test
    fun `will return 2 pages of PENDING invoices when look for specific customer`() {

        every { dal.fetchInvoicePagesByCustomer(customer = 1, status = InvoiceStatus.PENDING, pageSize = 50, marker = null) } returns
            anInvoicePage(InvoiceStatus.PENDING, false, 50)

        every { dal.fetchInvoicePagesByCustomer(customer = 1, status = InvoiceStatus.PENDING, pageSize = 50, marker = 49) } returns
            anInvoicePage(InvoiceStatus.PENDING, true, 50)

        val page1 = invoiceService.fetchPageByCustomer(customer = 1, pageSize = 50, marker = null)
        val page2 = invoiceService.fetchPageByCustomer(customer = 1, pageSize = 50, marker = page1.invoices.last().id)
        Assertions.assertAll(
            Executable { Assertions.assertEquals(50, page1.invoices.size) },
            Executable { Assertions.assertFalse(page1.isLast) },
            Executable { Assertions.assertEquals(50, page2.invoices.size) },
            Executable { Assertions.assertTrue(page2.isLast) }
        )
    }

    @Test
    fun `update invoice status`() {
        val id = 1
        val newStatus = InvoiceStatus.PAID
        val updatedInvoice = expectUpdateInvoice(id, newStatus)

        val result = invoiceService.updateStatus(id, newStatus)

        Assertions.assertEquals(updatedInvoice, result)
        verify {
            dal.updateInvoiceStatus(id, newStatus)
        }
    }

    private fun expectUpdateInvoice(id: Int, newStatus: InvoiceStatus): Invoice {
        val updatedInvoice = Invoice(id, id, Money(BigDecimal.valueOf(1L), Currency.DKK), newStatus)
        every {
            dal.updateInvoiceStatus(id, newStatus)
        } returns updatedInvoice
        return updatedInvoice
    }
}
