# EKCHOUS Engine — Streaming

Cubed-sphere chunk paging for a streamed open world. Three-tier residency, manual virtual page table, Zstd disk persistence.

## Chunk size and addressing

- **Chunk size**: 64×64 pixels = 4096 cells. Matches a compute workgroup at 8×8 × 64 threads.
- **Address**: `(face: u3, cx: u20, cy: u20)`. Six cubed-sphere faces × ~1M chunks per face = ~6M chunks per planet maximum (most planets use far fewer).
- **Per-chunk memory** (Grid only, uncompressed): 4096 pixels × 16 bytes = **64 KB**.
- **Resident chunks cap**: 64K on an 8 GB GPU (→ 4 GB for the grid ping-pong pair). Halve for 4 GB.

## Three-tier residency

Residency around each player's position on the planet:

| Tier | Distance | What runs | Where it lives |
|---|---|---|---|
| **Active** | 0–5 chunks | Full sim pipeline (all 18 passes) | GPU resident, in ping-pong grid |
| **Dormant-resident** | 5–15 chunks | Skipped via indirect dispatch (activity bits cleared) | GPU resident, kept hot for fast wake |
| **Disk** | 15+ chunks | Paged out; Zstd-compressed on disk | Disk only, NOT in GPU memory |

The 5-chunk Active radius matches `EKCHOUS_planet_satellites.md` streaming rule and `pixel_physics_foundation §11.2` LOD Active band.

## Async streaming

- **I/O thread** runs Zstd compression/decompression and disk I/O.
- **Persistent-mapped staging ring** (3-frame depth) for GPU upload. Each frame writes new chunks into the ring; GPU consumes via `glBufferSubData` from the ring.
- **Eviction**: LRU on Dormant-resident exit boundary. When a chunk leaves Dormant-resident, it goes to Disk (after Zstd compression).
- **Promotion**: when a chunk needs to enter Active (player approaches), I/O thread reads + decompresses + queues for GPU upload through the staging ring. Promotion latency target: < 100 ms (one render frame at 60 Hz).

## Chunk page table

A sparse hash from `(face, cx, cy)` to GPU page index. Implemented as a flat hash table (open addressing, linear probing) keyed by packed `u64`. CPU-side; the GPU samples through it via a small lookup texture or UBO. Capacity 64K entries.

We **explicitly do not use `ARB_sparse_texture`** — portability across NVIDIA/AMD/Intel is uneven, and the manual page table is simpler.

## Persistence format

Each chunk serializes to a per-chunk file (or to a shard within a sharded packfile). Format:

```
struct ChunkSerialization {
  u64 magic;             // 0x454B43485553_4348 ("EKCHOUSCH")
  u32 format_version;
  ivec3 address;         // (face, cx, cy)
  u64 world_seed;
  u32 generation_tick;
  u32 uncompressed_size;
  u32 compressed_size;
  u8  payload[compressed_size]; // Zstd of:
                                //   - Grid pixel SSBO contents (64 KB)
                                //   - Side-buffer entries for this chunk
                                //   - Local body table fragments
                                //   - Local in_flux particles
};
```

Zstd is chosen for fast decompression (~500 MB/s single-threaded) and for high RLE compression of large rock / sky regions where most pixels have the same `element_id`.

Key format: `(world_seed, face, cx, cy, generation_tick)` for keyed retrieval.

File layout choice (deferred):
- **Option A**: one file per chunk. Simple; many small files.
- **Option B**: sharded packfile (e.g., 256 chunks per file, indexed by header). Better for many-chunk batch loads.
- **Recommended**: Option B with 256 chunks per shard, indexed by an offset table in the shard header. Lower file count, batched I/O.

## Body table across streaming boundaries

Bodies are **NOT chunk-local**. A bonded body's AABB may overlap multiple chunks, and the body's `pixel_list_head` references pixels across chunks.

**Survival rule**: a body remains in `BodyTable` (GPU resident) while ANY of its pixels are in Active or Dormant-resident. Only when ALL pixels are at Disk tier does the body serialize to a separate `bodies.dat` sidecar (also Zstd-compressed) and get evicted from `BodyTable`.

**Promotion**: when a player approaches a region with serialized bodies, the bodies' entries are restored to `BodyTable` *before* their chunks fully promote to Active. This avoids a brief tick where pixels exist in the grid but have no body header.

## Cloth and rope across streaming

Cloth particles are anchored to bodies. When an anchored body streams to Disk:

- **If both anchors of a cloth piece leave Active+Dormant**: the cloth serializes with its origin chunk's sidecar.
- **If only one anchor leaves**: the surviving anchor remains in the cloth's `ClothParticleSoA` entry; the departed anchor is demoted to a **world-space pinned point** (fixed-point world coordinate). When the original body streams back in, the anchor is re-bound if possible; otherwise the cloth keeps the pinned point.

This lets a player drop a rope, walk out of streaming range, and find the rope still hanging when they return.

## In_flux pixels across streaming

In_flux pixels in chunks that leave Active are **paused, not destroyed**. Their position + velocity (q16.16 + q8.8) is serialized with the chunk. On chunk promotion to Active, in_flux pixels resume integration from their last position/velocity.

This is determinism-safe: position + velocity are fixed-point; resumption is identical to having simulated the chunk continuously (in the deterministic sim, dormancy is just a way to skip computation — the state at resumption IS the state that would have existed).

## Streaming budget

At 60 Hz render, the streamer must:

- Upload up to ~3 new chunks per render frame (player walking quickly).
- Evict up to ~3 chunks per render frame.
- Compress/decompress on the I/O thread without blocking the main thread.

Bandwidth: 3 chunks/frame × 64 KB uncompressed × 60 fps ≈ 11.5 MB/s upload. Zstd at 500 MB/s easily keeps up. Disk read at 100 MB/s SSD comfortable; HDD users see longer streaming latency.

## What is NOT in the streaming system

- **No texture streaming** — the engine doesn't load art assets per chunk; rendering is procedural from element_id + tissue_type via the MaterialLUT.
- **No LOD geometry** — there's no geometry to LOD; everything is per-pixel.
- **No dynamic chunk size** — 64×64 is fixed at compile time.
- **No predictive streaming** — first-pass uses simple radius-based residency; predictive ("player is moving north, prefetch northern chunks") is a Phase 2+ optimization.

## Day-One scope cut

For the Day-One falling-sand demo, streaming is OFF. One chunk only, always resident, no paging. The streaming subsystem is stubbed with `TODO_CORPUS(planet_satellites::5-chunk-rule)` and lives in `engine/world/Streamer.h` with empty implementations.

Streaming becomes Day-N+1 work once the in-chunk physics is stable.
