# Order Analytics Service - Spring Boot Java

A Java 17 / Spring Boot 3.5.16 application for CST8915. It consumes the existing
Node.js order-service's RabbitMQ messages and exposes five read-only HTTP endpoints
on port 4000. The store-front and order-service do not need changes to their message
format. No MySQL server is required.

## Storage decision

For this assignment, use in-memory storage. The project requirements explicitly
allow data to reset when the service restarts.

| Choice | Benefits | Tradeoffs | Recommendation |
| --- | --- | --- | --- |
| In-memory list + aggregation maps | No database setup, simple Java code, easy local/Azure demo | Data disappears on restart, memory grows with orders, one application instance | Use for this assignment |
| MySQL | Persistent history, SQL queries, shared data across instances | Database deployment, schema, credentials, migrations, transactions | Use when persistent history is required |

Each consumed message is retained in an `ArrayList` behind synchronized methods.
All stored values are immutable Java records. Each request reads an immutable
snapshot. Analytics group that snapshot using a local `TreeMap` keyed by product
name. A single map from product name to order would overwrite repeat purchases
and could not correctly implement `GET /orders`.

`OrderStore` is an interface so a later MySQL implementation can replace the memory
store. Analytics currently scan the orders per request: O(n) time and O(n) snapshot
memory. This is appropriate for lab-sized data, not an unlimited order archive.

## Run locally on Windows 11

Prerequisites: JDK 17 or newer, Maven 3.6.3 or newer, and a running RabbitMQ broker.
Use a Java runtime compatible with Spring Boot 3.5; Java 17 is the build target.
Check the Java installation Maven actually uses:

```powershell
java -version
mvn -version
```

Open PowerShell in this folder (the one containing `pom.xml`). Copy the sample
configuration once, then edit it to match your broker:

```powershell
Copy-Item .env.example .env
notepad .env
```

Example `.env` for the local RabbitMQ container from our setup instructions:

```dotenv
PORT=4000
RABBITMQ_CONNECTION_STRING=amqp://student:LabPassword123@localhost:5672/
ORDER_QUEUE=order_queue
```

The sample password is only a local example; use your broker's actual credentials.
`.env` is ignored by Git. Spring reads it as a Java properties file: do not add
`export`, `$env:`, or surrounding quotes. OS environment variables take precedence.

Start RabbitMQ using Docker Desktop / your existing container, then start Java:

```powershell
mvn spring-boot:run
```

Wait for `Started OrderAnalyticsApplication`. Leave the terminal open; Ctrl+C stops
the application. Open http://localhost:4000/health and check for `UP`.

Alternatively build and run an executable JAR:

```powershell
mvn clean package
java -jar target/order-analytics-service-1.0.0.jar
```

If the download includes `dist/order-analytics-service-1.0.0.jar`, that is an already
built executable. From this project's root folder run:

```powershell
java -jar dist/order-analytics-service-1.0.0.jar
```

Java is enough to run that JAR; Maven is needed only to build or run from source.

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `4000` | HTTP server port |
| `RABBITMQ_CONNECTION_STRING` | `amqp://guest:guest@localhost:5672/` | Attached broker's AMQP URI |
| `ORDER_QUEUE` | `order_queue` | Queue shared with the existing publisher |

You can set these directly in PowerShell instead of using `.env`:

```powershell
$env:PORT = '4000'
$env:RABBITMQ_CONNECTION_STRING = 'amqp://student:LabPassword123@localhost:5672/'
mvn spring-boot:run
```

The queue is declared durable, non-exclusive, and not auto-delete, matching the
provided order-service. Existing declarations must match. If RabbitMQ reports
`PRECONDITION_FAILED`, compare publisher and consumer declarations before making
changes. Do not delete a queue that contains orders you need to preserve.

When Java runs directly on Windows and RabbitMQ publishes its port to Windows,
use `localhost:5672`. If Java runs in an Azure App Service, use the Azure VM's
reachable broker address instead. For `amqps://`, use a correctly configured TLS
broker endpoint and trusted certificates. URI-encode special characters in credentials.

## Message contract

```json
{
  "product": { "id": 1, "name": "Cat Food", "price": 9.99 },
  "quantity": 2,
  "totalPrice": 19.98
}
```

Product ID must be positive, name nonblank, price nonnegative, quantity a positive
integer, and totalPrice nonnegative. Required fields cannot be null. JSON is parsed
from raw bytes, so the existing Node.js publisher does not need JSON content-type
or Java type headers. Extra JSON fields are ignored by Spring Boot's normal mapper.

Revenue uses the publisher's `totalPrice`; it is not recomputed from product price.
`BigDecimal` avoids binary floating-point errors. Revenue and averages are returned
as JSON numbers rounded to two decimal places, using HALF_UP. Order and quantity
counts are integers. Product names are grouped exactly as received (case-sensitive).

## API

| Method | Endpoint | Result |
| --- | --- | --- |
| GET | `/health` | 200 when broker is reachable and queue exists; otherwise 503 |
| GET | `/orders` | All orders consumed by this process, in arrival order |
| GET | `/analytics/summary` | Total orders, revenue, average order value |
| GET | `/analytics/products` | Product name, order count, revenue; sorted by name |
| GET | `/analytics/top-products` | Up to 3 products by total quantity descending; ties by name |

Health example:

```json
{"status":"UP","rabbitmq":"UP","storage":"in-memory"}
```

Health checks broker access and queue existence; it is not a proof that every
message is valid or that processing can never fail. Previously collected analytics
remain readable if the broker goes offline while the process stays running.

With no orders, `/orders`, `/analytics/products`, and `/analytics/top-products` return
`[]`. `/analytics/summary` returns:

```json
{"totalOrders":0,"totalRevenue":0.00,"averageOrderValue":0.00}
```

Use a browser, VS Code REST Client (`requests.http`), or a second PowerShell terminal:

```powershell
Invoke-RestMethod http://localhost:4000/health
Invoke-RestMethod http://localhost:4000/orders
Invoke-RestMethod http://localhost:4000/analytics/summary
Invoke-RestMethod http://localhost:4000/analytics/products
Invoke-RestMethod http://localhost:4000/analytics/top-products
```

There is intentionally no POST endpoint on this service. Submit orders through
the existing store-front / Node.js order-service, which publishes them to RabbitMQ.
For a local test, keep Node.js order-service on port 3000 running and submit:

```powershell
$order = @{
    product = @{ id = 1; name = 'Dog Food'; price = 19.99 }
    quantity = 2
    totalPrice = 39.98
} | ConvertTo-Json -Depth 3

Invoke-RestMethod -Uri 'http://localhost:3000/orders' -Method Post -ContentType 'application/json' -Body $order
```

Then query the analytics endpoints. Consumption is asynchronous, so allow a brief
moment after the publisher responds. With a fresh analytics process and just this
message, the summary is one order, 39.98 total revenue, 39.98 average order value.
Earlier queued messages may be consumed as soon as this service starts.

## Consumer pattern and limitations

- Push-based consumer using Spring AMQP `@RabbitListener` and the existing queue.
  This continuously listens without a polling loop and fits one consumer processing
  work from one order queue.
- One listener, one application process, one application instance. Prefetch is 1.
  Do not scale out independent in-memory instances: each would hold only its own
  portion of the messages, and API responses would differ between instances.
- Spring acknowledgement mode `AUTO`: the container sends the broker ACK after the
  listener successfully validates and stores the order. This is not RabbitMQ's
  no-ack / immediate acknowledgement mode.
- Malformed JSON or invalid orders are rejected without requeue. Without an
  existing dead-letter policy they are discarded; with one they follow the broker's
  policy. Rejections are logged. This prevents a poison message blocking valid work.
- Acknowledged messages leave RabbitMQ. Restarting Java loses its stored history;
  restarting RabbitMQ does not restore acknowledged messages to Java.
- Delivery is not exactly-once. A redelivery can be counted twice if the connection
  fails between storing and acknowledgement. Identical legitimate purchases are
  intentionally retained; the current payload has no unique order/event ID.
- There is no authentication on this lab API. All stored orders are accessible to
  clients that can reach it. Add access control if using it beyond a lab demo.

For a persistent version, add a unique publisher-generated event ID and a MySQL
table with a unique constraint on that ID, commit the database transaction before
acknowledging, and make redelivery handling idempotent. A map keyed by product name
is not a substitute for that design.

## Tests

```powershell
mvn test
```

Unit and HTTP tests require no running broker. They cover empty data, repeated
orders, decimal math and rounding, quantity ranking and ties, concurrent appends,
invalid messages, all five HTTP endpoints, and broker-down HTTP status.

The real RabbitMQ integration test is opt-in. Use a separate test broker:

```powershell
$env:RABBITMQ_TEST_ADDRESS = 'amqp://your-test-user:your-test-password@localhost:5673/'
mvn test
Remove-Item Env:RABBITMQ_TEST_ADDRESS
```

It uses a unique test queue and deletes only that queue afterward. The test sends
raw Node.js-style JSON and malformed JSON, waits for consumption, checks the HTTP
analytics, and verifies the queue drains. Without the variable, this test is skipped.

## Azure App Service

1. Build `mvn clean package` and deploy the executable JAR to a Java 17 (Java SE)
   App Service. Use an App Service Java runtime, not a Tomcat WAR deployment.
2. Configure `RABBITMQ_CONNECTION_STRING` and `ORDER_QUEUE` in App Service environment
   variables; keep credentials out of source control.
3. Configure `PORT` to match the Java App Service hosting/startup port. The app also
   accepts standard Spring overrides such as `SERVER_PORT`.
4. Ensure the App Service can reach the RabbitMQ VM's AMQP endpoint and that the
   broker account has access to the intended virtual host/queue.
5. Keep App Service at one instance for in-memory storage. A restart, deployment,
   or cold start resets data. Start/warm the service before placing demo orders.
6. Place orders using the deployed store-front and inspect the deployed analytics
   endpoints. Do not use `localhost` as the broker host in Azure.

## Architecture and code map

```mermaid
flowchart TD
    Front["Vue store-front"] -->|"GET /products"| Product["Python product-service :3030"]
    Front -->|"POST /orders"| Order["Node.js order-service :3000"]
    Order -->|"publish JSON"| Broker["RabbitMQ: order_queue"]
    Broker -->|"consume + ACK"| Java["Spring Boot analytics :4000"]
    Client["Browser or REST client"] -->|"five GET endpoints"| Java
```

- `model/OrderMessage.java`: immutable incoming payload and validation.
- `messaging/RabbitConfiguration.java`: durable queue declaration.
- `messaging/OrderListener.java`: JSON validation and storage before ACK.
- `store/OrderStore.java`: storage interface; `InMemoryOrderStore` implements it.
- `service/AnalyticsService.java`: calculations using immutable snapshots.
- `service/BrokerHealth.java`: passive broker/queue check.
- `web/AnalyticsController.java`: five REST endpoints.
- `src/main/resources/application.properties`: configuration and single-consumer settings.

## Assignment documentation

Use a separate private repository for this service as required by the assignment.
Add your instructor as a collaborator, links to the other service repositories,
your demo video, and each group member's actual contributions. Review and adapt
the implementation with your group before submission.

AI assistance disclosure to adapt: This project was drafted with ChatGPT assistance
for Java implementation, documentation, and test design. Record your group's own
review, modifications, test runs, and understanding; do not claim checks you did not run.

References:
- Assignment: https://github.com/ramymohamed10/26F_CST8915_Midterm_Project
- Spring Boot AMQP: https://docs.spring.io/spring-boot/3.5/reference/messaging/amqp.html
- Spring Rabbit listeners: https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-annotation-driven.html
