# Verification results

Verified on October 18, 2026 using Windows 11, Java 17.0.14, Maven 3.8.8,
Spring Boot 3.5.16, and an isolated RabbitMQ Docker container.

`mvn clean package`: PASS (executable JAR produced).

Executable JAR smoke test: PASS. Started the JAR in an isolated working directory,
loaded `PORT`, `RABBITMQ_CONNECTION_STRING`, and `ORDER_QUEUE` from `.env`, and
received HTTP 200 with status UP from `/health` against the test broker.

`mvn test` with `RABBITMQ_TEST_ADDRESS` set to the isolated broker:

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| AnalyticsServiceTest | 5 | 0 | 0 | 0 |
| OrderListenerTest | 1 | 0 | 0 | 0 |
| AnalyticsHttpTest | 2 | 0 | 0 | 0 |
| RabbitMqIntegrationTest | 1 | 0 | 0 | 0 |
| Total | 9 | 0 | 0 | 0 |

The integration test published two valid orders and one malformed message to a
unique test queue. The service retained both valid orders, returned 39.96 total
revenue and 4 units sold, rejected the malformed message, and drained the queue.
No existing order-service queue was used for verification.

Azure deployment and integration with your particular running Node.js service
have not been performed. The supplied PowerShell and REST Client examples cover
the next manual verification step through your existing publisher.
