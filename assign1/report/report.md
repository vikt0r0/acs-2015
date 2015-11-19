+---------+-----------+
|   Addr  | MachineID |
+---------+-----------+
|  0 - 39 |     1     |
| 40 - 59 |     2     |
| 60 - 99 |     3     |
+---------------------+

### READ(addr)

1. client send `addr` to central server
2. central server compute `machine ID` for `addr`
3. central server send `machine ID` to client
4. client send `addr` to `machine ID`
5. machine checks `addr` falls into address space it controls
6. machine calculates local memmory address, and reads `data`
7. machine send `data` to client


### WRITE(addr, data)

1. client send `addr` to central server
2. central server compute `machine ID` for `addr`
3. central server send `machine ID` to client
4. client send `addr` and `data` to `machine ID`
5. machine checks `addr` falls into address space it controls
6. machine calculates local memmory address, and writes `data` to that location
7. machine sends ACK to client



If a machine controlling memory for address [i,j) goes down (eg. power failure),
we cannot remap that value. Doing so would violate the invariant that READ'ing
from an address that you issued the last successful WRITE to will give the same data.



# Question 2

1. Concurrency enables us to process more than one request at once, which can reduce the average latency over serial execution, as all requests do not need to wait for a slow IO device to complete (ie disk access). However, this assumes the different threads do not spend too much time waiting to aquire locks, or even deadlocks.

2. Batching vs. Dallying
    * Batching is submiting more than one request to a latency heavy service, to armotize the cost of the latency and improve throughput.
    * Dallying is waiting to fill up buffers before batching requests, to further improve throughput, but will most likely increase latency.

3. Caching the results to frequent requests is a way of fast path optimizaion, as we do not have to perform the calculation every time.
