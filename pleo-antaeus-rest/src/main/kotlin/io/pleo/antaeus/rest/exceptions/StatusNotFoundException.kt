package io.pleo.antaeus.rest.exceptions

class StatusNotFoundException(status: String) : Exception("Invoice status not found: $status")
