# A2A Jakarta - Enterprise Example

This example demonstrates the [A2A Java SDK](https://github.com/a2aproject/a2a-java)'s enterprise
features for running in a load-balanced environment: JPA-backed `TaskStore` and
`PushNotificationConfigStore`, and a Kafka-backed `ReplicatedQueueManager`. It runs two WildFly
nodes sharing one PostgreSQL database and one Kafka broker, and proves that a task created and
driven via one node is fully visible — including live streamed events — via the other.

Unlike [`examples/simple`](../simple), this is not a minimal transport demo: it specifically
exercises multi-instance persistence and event replication. See the a2a-java docs for background:
[Storage & Persistence](https://github.com/a2aproject/a2a-java/blob/main/docs/content/dev/extra/storage.md)
and [Replicated Queue Manager](https://github.com/a2aproject/a2a-java/blob/main/docs/content/dev/extra/replicated-queue-manager.md).

## Server

The server is a Jakarta EE application, located in [`server`](./server), built from the same
profiles as `examples/simple` (`jsonrpc`, `grpc`, `rest`). It additionally depends on four
SDK modules — grouped in a clearly-commented block in
[`server/pom.xml`](./server/pom.xml) — that automatically replace the SDK's default in-memory
implementations once present on the classpath:

* `a2a-java-extras-task-store-database-jpa`
* `a2a-java-extras-push-notification-config-store-database-jpa`
* `a2a-java-queue-manager-replicated-core`
* `a2a-java-queue-manager-replication-mp-reactive`

Persistence is configured in [`server/src/main/resources/META-INF/persistence.xml`](./server/src/main/resources/META-INF/persistence.xml),
pointing at the portable Jakarta EE default datasource (`java:comp/DefaultDataSource`), which
WildFly Glow binds automatically via the `postgresql:default` add-on
(see [`server/pom.xml`](./server/pom.xml)'s `discover-provisioning-info` blocks). The `kafka`
add-on similarly provisions the MicroProfile Reactive Messaging Kafka connector — no manual
CLI/subsystem configuration is needed for either.

### Two nodes, one build

Both nodes run from the *same* provisioned server output. Node B is distinguished purely by
`-Djboss.socket.binding.port-offset=1000` (a standard WildFly technique) and a unique Kafka
consumer `group.id` override — see
[`server/src/main/resources/META-INF/microprofile-config.properties`](./server/src/main/resources/META-INF/microprofile-config.properties)
for why each node needs its own group id (broadcast semantics, not the shared-group production
pattern described in the replicated-queue-manager doc — deliberate, for a deterministic two-node
demo).

| | Node A | Node B |
|---|---|---|
| HTTP/JSON-RPC/REST | `localhost:8080` | `localhost:9080` |
| gRPC | `localhost:9555` | `localhost:10555` |

## Client

The client, in [`client`](./client), demonstrates the replication story directly:

1. Sends a message with `messageId="init"` to **node A**. The server's `AgentExecutor` (see
   [`server/.../EnterpriseExampleAgentExecutorProducer.java`](./server/src/main/java/org/wildfly/a2a/jakarta/examples/enterprise/EnterpriseExampleAgentExecutorProducer.java))
   recognizes this as a demo-only handshake, creates the task, and returns immediately without
   doing any work.
2. Resubscribes to that task id via **node B** — a node that has never processed this task
   locally.
3. Sends the real message (the user's name) back to **node A**, which runs the actual work
   (`WORKING` → artifact → `COMPLETED`).
4. Prints the states node B observed and the final artifact text — everything node B saw arrived
   via Kafka replication, not local state.

## Prerequisites

* PostgreSQL and Kafka, run as containers (Podman locally, Docker in CI — same commands, swap the
  binary name).

## Building and Running the Example

Run the following commands from the current directory (i.e. `examples/enterprise` under the root
checkout folder).

### 1. Start PostgreSQL and Kafka

```shell
podman run -d --name a2a-enterprise-postgres \
  -e POSTGRES_DB=a2a_enterprise -e POSTGRES_USER=a2a -e POSTGRES_PASSWORD=a2a \
  -p 5432:5432 postgres:16

podman run -d --name a2a-enterprise-kafka -p 9092:9092 apache/kafka:4.1.0

# Wait a few seconds for the broker to finish starting, then:
podman exec a2a-enterprise-kafka /opt/kafka/bin/kafka-topics.sh --create \
  --topic replicated-events --bootstrap-server localhost:9092 --partitions 1
```

### 2. Build and provision the server (choose one transport)

```shell
mvn clean install -Pjsonrpc -f server/pom.xml
```

(Use `-Pgrpc` or `-Prest` for the other transports.)

### 3. Create the two node instances and start them

```shell
cp -r server/target/wildfly server/target/node-a
cp -r server/target/wildfly server/target/node-b

POSTGRESQL_DATABASE=a2a_enterprise POSTGRESQL_USER=a2a POSTGRESQL_PASSWORD=a2a \
POSTGRESQL_SERVICE_HOST=localhost POSTGRESQL_SERVICE_PORT=5432 \
server/target/node-a/bin/standalone.sh \
  -Dmp.messaging.incoming.replicated-events-in.group.id=a2a-jakarta-enterprise-node-a &

POSTGRESQL_DATABASE=a2a_enterprise POSTGRESQL_USER=a2a POSTGRESQL_PASSWORD=a2a \
POSTGRESQL_SERVICE_HOST=localhost POSTGRESQL_SERVICE_PORT=5432 \
server/target/node-b/bin/standalone.sh \
  -Djboss.socket.binding.port-offset=1000 \
  -Dmp.messaging.incoming.replicated-events-in.group.id=a2a-jakarta-enterprise-node-b &
```

For the `grpc` transport, append `--stability=preview` to both `standalone.sh` invocations.

### 4. Run the client

```shell
mvn exec:java -f client/pom.xml -Prun-jsonrpc -Duser.name=Kabir
```

(Use `-Prun-grpc` or `-Prun-rest` to match the transport built in step 2.)

Expected output:

```
Created task <uuid> via node A
Node B observed states (via Kafka replication): [TASK_STATE_WORKING, TASK_STATE_COMPLETED]
Agent responds: Hello Kabir
```

### 5. Shut down

```shell
kill %1 %2
podman stop a2a-enterprise-postgres a2a-enterprise-kafka
podman rm a2a-enterprise-postgres a2a-enterprise-kafka
```
