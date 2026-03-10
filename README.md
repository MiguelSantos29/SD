![Status](https://img.shields.io/badge/status-Completed-success)
![University](https://img.shields.io/badge/University-UMinho-red)

# Distributed Sales Aggregation System

Client-server distributed system for **time-series sales aggregation and event detection**, implemented using **Java sockets and concurrent programming**.

Developed for the **Distributed Systems** course in the **3rd year of the Software Engineering degree at the University of Minho**.

The system allows multiple clients to insert sales records, query aggregated statistics and subscribe to complex event notifications while ensuring **data consistency, concurrency safety and efficient memory usage**.

---

# Overview

The project implements a distributed architecture capable of managing large volumes of sales data while supporting concurrent access from multiple clients.

The system provides operations such as:

- inserting new sales transactions
- querying aggregated statistics (sum, average, maximum)
- retrieving events for specific products
- detecting complex event patterns in sales streams
- supporting concurrent clients
- ensuring persistence of data
- limiting memory usage through a **Least Recently Used (LRU) cache**

One of the core challenges addressed in this project is maintaining efficient memory usage while still supporting queries over historical data stored on disk.

---

# System Architecture

The system follows a **client-server architecture** with modular server design.

Clients communicate with the server using **TCP sockets**, while the server manages authentication, persistence, concurrency control and event detection.

The architecture includes the following main components:

### Server

The server is responsible for:

- managing client connections
- storing and retrieving sales data
- performing aggregated queries
- detecting sales events
- handling concurrent requests
- managing memory through a cache mechanism

### Client

Clients send requests to the server and receive responses.  
Each request corresponds to a specific operation defined by the communication protocol.

---

# Data Entities

The system relies on two main data structures.

### Users

User authentication is handled using a persistent map:

```
Map<String, String>
```

Where:

- key → username  
- value → password

The map is loaded into memory during server startup and persisted to disk (`users.dat`) whenever new users are registered.

Access to this structure is protected with locks to support concurrent authentication.

---

### Sales (Venda)

Each sale is represented by a serializable object containing:

- `timestamp` – transaction time
- `produto` – product identifier
- `quantidade` – quantity sold
- `preco` – unit price

---

# Memory Organization

Sales are organized hierarchically to allow efficient aggregation queries.

```
Map<Integer, Map<String, List<Venda>>>
```

Where:

- first level → day identifier
- second level → product
- value → list of sales

This structure allows queries such as **total sales of a product in a specific day** to be executed in near constant time.

---

# Server Modular Design

The server implementation is divided into three main components.

### Memoria.java

Responsible for:

- RAM and disk storage
- implementation of the **LRU cache**
- lazy loading of historical data
- shutdown hooks for file cleanup

The cache keeps only the **S most recent days** in memory.

---

### GestorCondicoes.java

Handles the **event detection logic**.

Implements mechanisms such as:

- sliding window detection for sequential sales
- delta comparison for simultaneous sales events

---

### DataBase.java

Acts as the system coordinator.

Responsible for:

- managing locks and conditions
- coordinating interactions between memory and event detection logic
- ensuring thread safety

---

# Communication Middleware

Communication between client and server is implemented using **TCP sockets** and provided abstractions from the course:

- `TaggedConnection`
- `Demultiplexer`

These abstractions simplify:

- message tagging
- concurrent communication
- serialization of messages

---

# Stub

The client-side communication layer follows the **Stub design pattern**.

Each local method call is converted into a network message.

The workflow is:

1. Serialize operation code and arguments
2. Send request through the `Demultiplexer`
3. Wait for the response with the corresponding tag
4. Deserialize the response and return the result

---

# Communication Protocol

Communication uses a binary protocol where the first integer defines the **operation code (OpCode)**.

| OpCode | Operation | Request Arguments | Reply |
|------|------|------|------|
| 7 | REGISTER | username, password | success |
| 0 | LOGIN | username, password | success |
| 1 | INSERT | timestamp, product, quantity, price | acknowledgement |
| 2 | CONSULT_TOTAL | product | total sales |
| 4 | AGGREGATION | product, days, type | aggregated value |
| 5 | EVENTS_DAY | product set, day | list of sales |
| 8 | WAIT_SIMULT | product1, product2 | success |
| 9 | WAIT_CONSEC | quantity | detected product |
| 3 | ADVANCE_DAY | none | acknowledgement |

---

# Cache and Persistence

The server maintains historical data on disk while keeping only a limited portion in memory.

An **LRU cache strategy** ensures that only the most recently accessed days remain in memory.

This guarantees the memory constraint:

```
1 < S < D
```

Where:

- S = days stored in RAM
- D = total days stored in disk

When older data is required, it is **loaded lazily from disk**.

---

# Testing

Three main testing scenarios were implemented.

### Concurrent Client Test

Simulates **50 concurrent clients** performing random operations such as inserts and queries.

This validates:

- system stability
- correctness of aggregated results
- concurrency handling

---

### Cache Load Test

The server is configured with a very small cache:

```
S = 3
```

While inserting data for multiple days.

The test verifies:

- correct eviction using the LRU policy
- performance difference between disk reads and cache hits

Results confirm that repeated accesses are significantly faster after the data is cached.

---

### Robustness Test

A **malicious/slow client scenario** was implemented.

The client continuously sends requests but never reads the responses.

This fills TCP buffers and blocks the thread responsible for that client.

Because the architecture uses **Thread-per-Client** and performs socket writes outside critical sections, other clients continue to operate normally, preserving system availability.

---

# Technologies Used

- Java
- TCP Sockets
- Concurrent Programming
- ReentrantLocks
- Condition Variables
- Serialization
- LRU Cache
- Client-Server Architecture

---

# Report

The repository includes a detailed report describing:

- system architecture
- concurrency design decisions
- cache strategy and persistence
- communication protocol
- testing methodology
- performance evaluation

The report also discusses how different workloads affect performance and how the cache strategy impacts response times.

---

# Authors

Group 11

- Diogo Alves Ferreira (A106904)  
- Hugo Araújo Cunha (A106808)  
- José Miguel da Silva Santos (A72443)  
- Mariana Vivas Rodrigues (A106898)

---

# Academic Context

Distributed Systems  
3rd Year – Software Engineering  
University of Minho
