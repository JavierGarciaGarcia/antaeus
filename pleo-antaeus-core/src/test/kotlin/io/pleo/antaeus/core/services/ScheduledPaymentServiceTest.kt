package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ScheduledPaymentServiceTest {
    private val billingService = mockk<BillingService>()
    private val customerService = mockk<CustomerService>()
    private val scheduledPaymentService = ScheduledPaymentService(billingService, customerService)

    @Test
    fun `should call billing service with all customers`() = runBlocking {
        val customers = expectListOfCustomersOkProcessed(5)
        val scheduledJob = scheduledPaymentService.schedule("* * * * *", false)
        scheduledJob.start()
        scheduledJob.join()
        verify {
            customerService.fetchAll()
        }
        customers.forEach {
            verify {
                billingService.proccessInvoicesByCustomer(it.id, InvoiceStatus.PENDING)
            }
        }
    }

    private fun expectListOfCustomersOkProcessed(size: Int = 2): List<Customer> {
        val customers = List(size) { aCustomer(it) }
        every {
            customerService.fetchAll()
        } returns customers
        customers.forEach {
            every {
                billingService.proccessInvoicesByCustomer(it.id, InvoiceStatus.PENDING)
            } returns true
        }
        return customers
    }

    private fun aCustomer(id: Int) = Customer(id, Currency.DKK)
}
