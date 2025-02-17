# CHANGELOG

## 1.6.0
* Improve documentation
* Adding more descriptive error when invalid status

## 1.5.1
* Improve database error handling

## 1.5.0
* Improve scheduler perfomance

## 1.4.0
* Add ktlint and fix linting errors

## 1.3.1
* fix issue in customer payments endpoint
* add new endpoint to pay all invoices for a specific type for a client

## 1.3.0
* add new endpoint to pay for a specific customer
* regroup endpoints

## 1.2.1
* fix issue to make the invoice process into a new thread

## 1.2.0
* Improve http responses

## 1.1.0
* Included circuit breaker support (https://resilience4j.readme.io/docs/getting-started-4) for calling payment provider
* Included keyset pagination (https://use-the-index-luke.com/no-offset) for iterating over invoices list

## 1.0.0 Initial Version
* Initial version using coroutines to process the list of invoices
* Using krontab (https://github.com/InsanusMokrassar/krontab) to schedule the job