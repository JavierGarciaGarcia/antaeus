# CHANGELOG

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