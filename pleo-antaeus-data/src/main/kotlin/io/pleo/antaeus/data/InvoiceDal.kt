package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import io.pleo.antaeus.data.constants.DEFAULT_PAGE_SIZE

class InvoiceDal ( private val db: Database ){
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun fetchInvoicesByStatus(status: InvoiceStatus): List<Invoice> {
        return transaction (db) {
            InvoiceTable
                .select(InvoiceTable.status.eq(status.toString()))
                .map { it.toInvoice() }
        }
    }

    fun fetchInvoicesPageByStatus(status: InvoiceStatus, pageSize: Int = DEFAULT_PAGE_SIZE, marker: Int?): InvoicePage {
        val condition = InvoiceTable
            .select(InvoiceTable.status.eq(status.toString()))
        marker?.let {
            condition.andWhere { InvoiceTable.id.greater(it) }
        }
        val invoices =  transaction(db) {
            condition
                .limit(pageSize)
                .orderBy(InvoiceTable.id to SortOrder.ASC)
                .map { it.toInvoice() }
        }
        return InvoicePage(
            invoices = invoices,
            isLast = invoices.size < pageSize,
            marker = if (invoices.isEmpty()) null else invoices.last().id
        )
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id)
    }

    fun updateInvoiceStatus(id: Int, newStatus: InvoiceStatus): Invoice? {
        val id = transaction(db) {
            InvoiceTable
                .update( { InvoiceTable.id.eq(id) } ) {
                    it[status] = newStatus.toString()
                }

        }
        return fetchInvoice(id)
    }
}