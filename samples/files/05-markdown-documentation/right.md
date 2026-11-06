# Storage Engine Architecture

The storage layer persists key-value records using an append-only write-ahead log (WAL) paired with a lock-free LSM-Tree index and bloom filters.

## Performance Targets

| Metric | Target (v2) | Notes |
| :--- | :--- | :--- |
| Read Latency (p99) | 0.8 ms | Direct page cache + Bloom filter |
| Write Throughput | 120,000 ops/s | Group commit every 2 ms |
| Compaction Pause | < 10 ms | Concurrent tiered compaction |
| Snapshot Export | < 500 ms | Zero-copy hardlink checkpoint |

## Recovery Procedure

1. Scan all active WAL segments from the latest manifest checkpoint.
2. Verify XXH3-64 checksums for each frame.
3. Replay uncommitted memtable batches into the LSM-Tree level-0 table.
4. Prune obsolete WAL segments after recovery completes.
