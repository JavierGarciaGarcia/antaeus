package io.pleo.antaeus.core.services

import dev.inmo.krontab.doInfinity
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledPaymentService(
    private val biilingService: BillingService
) {

    val scope = CoroutineScope(Dispatchers.Default )


    fun schedule() = scope.launch {
        doInfinity("0 0 0 1 *") {
            println("${java.time.LocalDateTime.now()} - Executin scheduled task")
            biilingService.processInvoicesByStatus(InvoiceStatus.PENDING.toString())
        }
    }

}