package io.pleo.antaeus.core.services

import dev.inmo.krontab.buildSchedule
import dev.inmo.krontab.doInfinity
import dev.inmo.krontab.doOnce
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

class ScheduledPaymentService(
    private val billingService: BillingService,
    private val customerService: CustomerService
) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val logger = KotlinLogging.logger {}

    fun schedule(frequency: String = "0 0 0 1 *", infinity: Boolean = true) = scope.launch {
        val scheduler = buildSchedule(frequency)
        if (infinity) {
            scheduler.doInfinity {
                processInvoices()
            }
        } else {
            scheduler.doOnce {
                processInvoices()
            }
        }
    }

    private fun processInvoices() = runBlocking {
        logger.info { "Start processing payment task" }
        customerService.fetchAll().forEach {
            launch(Dispatchers.IO) {
                try {
                    val customerResult = billingService.proccessInvoicesByCustomer(it.id, InvoiceStatus.PENDING)
                    logger.info { "Processed invoices for customer ${it.id} with result $customerResult" }
                } catch (e: Exception) {
                    logger.error(e) { "Error executing payments for customer ${it.id}" }
                }
            }
        }
        logger.info { "End processing payment task" }
    }
}
