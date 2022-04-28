package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.StatusNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
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
        every { fetchInvoicesByStatus(InvoiceStatus.PAID) } returns listOf(Invoice(1, 1, Money(BigDecimal.valueOf(1L), Currency.DKK), InvoiceStatus.PAID))
    }

    private val invoiceService = InvoiceService(dal = dal)

    @Test
    fun `will throw if invoice is not found`() {
        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(404)
        }
    }

    @Test
    fun `fetch will throw if status is not found`() {
        assertThrows<StatusNotFoundException> {
            invoiceService.fetchByStatus("INVALID STATUS")
        }
    }

    @Test
    fun `will return invoice by status`() {
        val invoices = invoiceService.fetchByStatus(InvoiceStatus.PAID.toString())
        Assertions.assertAll(
            Executable { Assertions.assertEquals(1, invoices.size) },
            Executable { Assertions.assertEquals(1, invoices.get(0).id) }
        )
    }

    @Test
    fun `update invoice status`() {
        val id = 1
        val newStatus = InvoiceStatus.PAID
        val updatedInvoice = expectUpdateInvoice(id, newStatus)

        val result = invoiceService.updateStatus(id, newStatus.toString())

        Assertions.assertEquals(updatedInvoice, result)
        verify {
            dal.updateInvoiceStatus(id, newStatus)
        }
    }

    @Test
    fun `update will throw if status is not found`() {
        assertThrows<StatusNotFoundException> {
            invoiceService.updateStatus(1, "INVALID STATUS")
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
