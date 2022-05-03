## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Proposed solution
I could spend 14 hours working in the challenge following the principles:
* TDD
* Clean Code
* KISS (Keep It Simple)

The following updates have been maid:
* Implement the solution using asynchronous [Why asynchronous programming](#asynchronous) processing
* Include *circuit breaker* to add a protection layer over the external provider
* Usage of  [Keyset pagination](#keyset-pagination)
* Use krontab library to handle the scheduled execution
* Include a new architecture proposal [Antaeus v2.0](Architecture_2_0.md)
* I've included [ktlint](https://github.com/pinterest/ktlint) as linter

### Endpoints
* /rest/v1/invoices : get all invoices
* /rest/v1/invoices/{:id} : get an specific invoice
* /rest/v1/invoices/status/{:status} : get all invoices of a specific status
* /rest/v1/payments/invoices/{:id} : try to pay the specific invoice
* /rest/v1/payments/invoices/status/{:status} : pay all the invoices of the specific status
* /rest/v1/payments/customers/{:id} : pay all the invoices of the specific customer
* /rest/v1/payments/customers/{:id}/status/{:status} : pay all the invoices of specific status for a specific customer
* /rest/v1/customers : get all the customers
* /rest/v1/customers/{:id} : get a specific customer

### Asynchronous
The code has been designed and developed thinking in how the current solution can grow and can be used with real data.
Calling a third party component (payment provider) could be something that consume CPU or take time, so using asynchronous
programming will help the application to not keep the main thread waiting and not waste CPU in that

Among with that, calling an external service is something that could include extra mechanism to protect the external service
For that, I've decided to include a circuit breaker, so we will give some time to the external service to recover in case of unexpected
behavior

### Keyset pagination
For iterating over large datasets in databases the common approach is using pagination, so the application will retrieve pages
instead of the hole dataset

When the dataset is huge and could be thousands of pages, databases suffer an issue known as "deep pagination issue", meaning that
even using pagination to iterate over all the dataset, the databse suffer when tries to access latest pages
In order to fix that, one solution is use keyset pagination. The base idea is not use the offset pagination but include extra filters
in the query to move between pages.

The limitation of keyset pagination is the impossibility to access a specific page directly, but since the feature don't require that, in my opinion,
the usage of keyset pagination is possible and recommended

### Future improvements
Due to the time limitation, I couldn't apply all the improvements I would like to do:
* Extend unit test coverage to the rest of components
* Create a set of Acceptance tests to cover the behaviour of the billing service
* Move to in-memory database to a real database, using postgres as solution
* Improve database error handling
* In order to improve the [3 observability pillars](https://www.oreilly.com/library/view/distributed-systems-observability/9781492033431/ch04.html):
  * Improve logging to have better understanding of the services
  * Create a set of custom metrics per service, so they can be used for the observability of the services and for the scalability
  * Include some tracing markers to the calls between services to be able to trace the interaction between them

### Error handling
Due to time limitations, I couldn't put so much effort in handling database errors. The scenario to cover is, once the payment has been executed an error occurred while updating the invoice status
in the database

For that scenario I've used the most simple approach, retry unlimited times the update in the database. A very rough approach and not production ready.

For future improvements, I would investigate new paths:
* Can we do a "rollback" in the payment provider? If so, we can handle the update status errors by doing rollbacks in the payment provider
* Add an extra layer of complexity by defining event system and using [SAGA](https://www.baeldung.com/cs/saga-pattern-microservices) pattern to handle a distributed transaction. See new architecture proposal [Antaeus v2.0](Architecture_2_0.md) for more details

### New architecture proposal
See [new architecture proposal](Architecture_2_0.md) for more details

### Conclusion
I've enjoyed a lot the challenge. Kotlin is a great language, and I was looking the moment to start working with it.
I really hope the challenge covers the minimum requirements to go to next recruitment step. I tried to apply my experience during the last years
working with microservices in the development and specially in the documentation

Due to my limited time, I could spend around 14 hours doing it in 4 days. For creating a real production ready service, I would apply the [future improvements](#future-improvements).
And for a long time solution I recommend going for the [new architecture proposal](Architecture_2_0.md)

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!


