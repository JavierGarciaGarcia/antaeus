package io.pleo.antaeus.core.services

import dev.inmo.krontab.doInfinity
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging

class ScheduledPaymentService(
    private val billingService: BillingService
) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val logger = KotlinLogging.logger {}

    fun schedule() = scope.launch {
        val oncePerMonth = "0 0 0 1 *"
        doInfinity(oncePerMonth) {
            logger.info("Executing scheduled task")
            val result = billingService.processInvoicesByStatus(InvoiceStatus.PENDING)
            logger.info("Task executed with result $result")
        }
    }
}
