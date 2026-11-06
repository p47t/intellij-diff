# Storage Engine Architecture

The storage layer persists key-value records using an append-only write-ahead log (WAL) paired with an in-memory B-Tree index.

## Performance Targets

| Metric | Target (v1) | Notes |
| :--- | :--- | :--- |
| Read Latency (p99) | 2.5 ms | Direct page cache lookup |
| Write Throughput | 45,000 ops/s | Group commit every 10 ms |
| Compaction Pause | < 50 ms | Background incremental merge |

## Recovery Procedure

1. Scan the active WAL segment from the last checkpoint offset.
2. Verify CRC32 checksums for each frame.
3. Rebuild the volatile B-Tree index in memory.
