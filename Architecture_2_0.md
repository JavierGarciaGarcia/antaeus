# New Architecture Proposal 

![Architecture version 2.0](anteus_architecture_2.0.png)

## Current architecture
Current architecture keeps all the services in 1 app, so through that app we can handle customers, invoices and payments.

Having only 1 app will make the communication and the deployment simple and easy but it comes with some withdraws:
* Cannot scale specific services. If invoice service is heavily used and need to be scaled up, we cannot do it for it only, we scale everything.
* Cannot isolate databases. If one of the services is being heavily used, the performance of the database and the complete service will be affected.

## New architecture approach
A new approach can be taken. Let's extract the different services to different applications, so we will have 3 different apps:
* Customer app
* Invoices app
* Payment app

By having that we ensure:
* Isolation between services. If one of them is heavily used or have a poor performance, the other 2 will remain unaffected.
* Different evolution. We will be able to evolve each app independently. If we decide to use a different approach for customers, using a new storage system, it won't affect the others.
* Better scalability. We can scale up and down each service independently.

Another changes we can apply:
* Delegate the scheduler of the monthly payment job to the deployment system. If we're using kubernetes we can create a cron job to do that and keep the responsibility there instead of the service
* Follow the API First development approach so every app will define an API first and communicate to other services to ensure the right usage
* As an idea to consider will be to use 2 entry points in every service that expose an external API:
  * Expose a REST API to access the service
  * Expose a gRPC API to internal communication

## Storage solution
For invoice service, having in mind that will be the larger data storage, I would recommend to use [AWS Auora](https://aws.amazon.com/rds/aurora/)

For the proposed solution, I would use a Postgres Aurora cluster and use the following features:
* Having a scheduled process that will iterate over all the invoices pending to be paid, I would create a cron job to scale the cluster with a specific reader (https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-replicas-adding.html).
* Create a [custom endpoint](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.Overview.Endpoints.html#aurora-custom-endpoint-creating) for the new reader instance
* Using another AWS services, like [AWS Route 53](https://aws.amazon.com/route53/), keep the new custom endpoint accessible with the same name

By doing that we can ensure the read isolation between normal behaviour of the invoice service and the scheduled job. The scheduled job will iterate over a possible large dataset using *keyset pagination* and a new reader instance, 
so the performance of the cluster won't be affected and won't affect the performance of invoice service


## Scalability
Having an isolated set of services, we can scale them based on their own necessities. E.G. if Invoice Service is heavily used could be scaled more than customer service

### How scale the services
Based on my experience, using Kubernetes is quite simple to scale up/down a service based on [custom metrics](https://learnk8s.io/autoscaling-apps-kubernetes). 
In order to been able to do that, each service must expose their own metrics, normally using [Prometheus](https://prometheus.io/) and a [k8s prometheus adapter](https://github.com/kubernetes-sigs/prometheus-adapter)

One important thing is to take care of the status of the database to scale the services. Normally the status of the database is the big forgotten actor in the scalation process. 
Using [circuit breaker](https://martinfowler.com/bliki/CircuitBreaker.html) pattern is a *reactive* way to protect the database. When the database start failing, we open the circuit as soon as the number of errors have gone over some threshold.
But by doing that we have affected the database and let it go to a *"dark place"*

In my opinion we should go for a *proactive* way of keeping the database in good shape:
* Using rate limiters in front of the services, ensuring that the amount of effectively calls that the service is receiving never exceeds the volume we used to design it
* Scale up/down the database consumers based on database custom metrics. E.G. if the avg of response time of the database starts to increase, we can scale down the database consumers in order to let the database recover
