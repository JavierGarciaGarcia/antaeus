/*
    Configures the rest app along with basic exception handling and URL endpoints.
 */

package io.pleo.antaeus.rest

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.pleo.antaeus.core.exceptions.EntityNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.StatusNotFoundException
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private val thisFile: () -> Unit = {}

class AntaeusRest(
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService,
    private val billingService: BillingService
) : Runnable {

    override fun run() {
        app.start(7000)
    }

    // Set up Javalin rest app
    private val app = Javalin
        .create()
        .apply {
            // InvoiceNotFoundException: return 404 HTTP status code
            exception(EntityNotFoundException::class.java) { _, ctx ->
                ctx.status(404)
                ctx.json("Entity not found")
            }
            // Unexpected exception: return HTTP 500
            exception(Exception::class.java) { e, _ ->
                logger.error(e) { "Internal server error" }
            }
            exception(StatusNotFoundException::class.java) { _, ctx ->
                ctx.status(400)
                ctx.json("Invalid status")
            }
            exception(InvoiceNotFoundException::class.java) { _, ctx ->
                ctx.status(404)
                ctx.json("Invoice not found")
            }
            // On 404: return message
            error(404) { ctx -> ctx.json("not found") }
        }

    init {
        // Set up URL endpoints for the rest app
        app.routes {
            get("/") {
                it.result("Welcome to Antaeus! see AntaeusRest class for routes")
            }
            path("rest") {
                // Route to check whether the app is running
                // URL: /rest/health
                get("health") {
                    it.json("ok")
                }

                // V1
                path("v1") {
                    path("invoices") {
                        // URL: /rest/v1/invoices
                        get {
                            it.json(invoiceService.fetchAll())
                        }

                        // URL: /rest/v1/invoices/{:id}
                        get(":id") {
                            it.json(invoiceService.fetch(it.pathParam("id").toInt()))
                        }

                        // URL: /rest/v1/invoices/status/{:status}
                        get("/status/:status") {
                            it.json(invoiceService.fetchByStatus(it.pathParam("status")))
                        }
                    }

                    path("payments") {
                        // URL: /rest/v1/payments/invoices/{:id}
                        post("/invoices/:id") {
                            val result = billingService.processInvoice(it.pathParam("id").toInt())
                            if (result) {
                                it.status(200)
                                it.json("The invoice has been paid correctly")
                            } else {
                                it.status(202)
                                it.json("The invoice cannot be paid. Please consult the payment provider")
                            }
                        }

                        // URL: /rest/v1/payments/invoices/status/{:status}
                        post("/invoices/status/:status") {
                            val result = billingService.processInvoicesByStatus(it.pathParam("status"))
                            if (result) {
                                it.status(200)
                                it.json("The invoices have been paid correctly")
                            } else {
                                it.status(202)
                                it.json("Some of the invoices cannot be paid. Please consult the payment provider")
                            }
                        }

                        // URL: /rest/v1/payments/customers/{:id}
                        post("/customers/:id") {
                            val result = billingService.proccessInvoicesByCustomer(it.pathParam("id").toInt())
                            if (result) {
                                it.status(200)
                                it.json("The customer's invoices have been paid correctly")
                            } else {
                                it.status(202)
                                it.json("The customer's invoices cannot be paid. Please consult the payment provider")
                            }
                        }

                        // URL: /rest/v1/payments/customers/{:id}/status/{:status}
                        post("/customers/:id/status/:status") {
                            val result = billingService.proccessInvoicesByCustomer(
                                customer = it.pathParam("id").toInt(),
                                status = it.pathParam("status")
                            )
                            if (result) {
                                it.status(200)
                                it.json("The customer's invoices have been paid correctly")
                            } else {
                                it.status(202)
                                it.json("The customer's invoices cannot be paid. Please consult the payment provider")
                            }
                        }
                    }

                    path("customers") {
                        // URL: /rest/v1/customers
                        get {
                            it.json(customerService.fetchAll())
                        }

                        // URL: /rest/v1/customers/{:id}
                        get(":id") {
                            it.json(customerService.fetch(it.pathParam("id").toInt()))
                        }
                    }
                }
            }
        }
    }
}
