package io.pleo.antaeus.core.exceptions

class StatusNotFoundException(status: String) : Exception("Invoice status not found: $status")
