# Proposal: expose JFR events as USDT probes

## Summary

We have a working prototype that publishes a subset of JFR events as USDT
probes, so an eBPF profiler attached to a stock JVM can consume JVM
events with no agent loaded, no recording started, and no restart. The
whole change is 12 files, roughly 690 net new lines, no new public API,
no spec change, and it is gated behind the existing
`--enable-jvm-feature-dtrace` build flag. The prototype is public here:

https://github.com/DataDog/openjdk-jdk/tree/jb/usdt-jfr

- `b7b13f5acd9` JFR: add USDT probes for JFR events

This document explains the problem, what the prototype does, and why
the change is small.

## The problem: no probes for Java developers

The JVM is not opaque to eBPF. HotSpot has shipped a USDT provider
since the mid-2000s (`src/hotspot/os/posix/dtrace/hotspot.d`,
`hs_private.d`), and it covers a lot of ground: GC pause boundaries
(`gc__begin`/`gc__end`), JIT compilation (`method__compile__begin`/
`__end`, `compiled__method__load`), monitor contention, thread park and
sleep, class initialization, safepoints, and VM operations. Those
probes are aimed at JVM developers. They answer questions like "when
did a safepoint hit" or "which method got compiled", and they say it in
terms of VM internals: no class names, no allocation sizes, no GC cause,
nothing a Java developer would recognize.

The questions application profiling actually needs answered, which
class is allocating and how much, what caused a GC from the
application's point of view, which thread burns CPU, are all answered
today by exactly one system: JFR. And JFR's only consumer interfaces are
in process. Today there are exactly two ways for an external tool to
get at those answers.

1. Start a JFR recording and consume it in process, through the
   streaming API or by dumping files, then ship the data out. This works,
   but it requires application changes or an attached agent, and the
   consumer has to be Java code living inside the target.
2. Ship a vendor native agent that hooks JVM internals by version-
   specific means. Every APM vendor ends up maintaining one of these,
   against interfaces that are not stable across releases.

Both options share the same flaw: something must be loaded into the
target JVM before any observability tool sees anything. Compare that
with native allocators, where the
[OTel eBPF profiler](https://github.com/open-telemetry/opentelemetry-ebpf-profiler)
already subscribes to jemalloc, tcmalloc, and libc probes with zero
changes to the target process.

Managed runtimes generally struggle here: Go, .NET, and Python keep
their application-level events behind in-process interfaces in much the
same way, so Java is not the odd one out, it is the norm. What makes
the JVM special is that the events an eBPF observer wants already
exist, curated and documented, inside JFR. The other managed runtimes
would have to build an event system first. Java needs only a
kernel-facing contract for the one it has, and that is a few hundred
lines away from being the front-runner in observability for very
little cost.

That event system is JFR: a stable, curated set defined in one file
(`src/hotspot/share/jfr/metadata/metadata.xml`) and generated from
there. The missing piece is a kernel-facing contract that an eBPF
consumer can subscribe to. That is what this prototype adds.

## What the prototype does

JFR events marked `usdt="true"` in `metadata.xml` are compiled into a
`jfr` USDT provider. The existing JFR file generator
(`make/src/classes/build/tools/jfr/GenerateJfrFiles.java`) now also emits
the dtrace provider file from the same metadata that produces the JFR
event classes, so probe signatures can never drift from event
definitions. The existing dtrace build rules then run the systemtap
`dtrace` wrapper twice, once with `-h` (a header with per-probe
is-enabled gates) and once with `-G` (an object file defining one
16-bit semaphore per probe), and link that object into libjvm.so. This is
the same mechanism HotSpot has used for its own `hotspot` and
`hs_private` providers for twenty years; nothing new is invented here.

The first three events and their probe arguments:

| JFR event | Probe | Arguments |
|---|---|---|
| GarbageCollection | `jfr:garbage__collection` | gcId, name, cause, sumOfPauses, longestPause |
| GCPhasePause | `jfr:gc__phase__pause` | gcId, name |
| ObjectAllocationSample | `jfr:object__allocation__sample` | objectClass, weight |

CPU time sampling is deliberately not on the list: an eBPF profiler
samples CPU time and stacks from the kernel side natively, better than
an in-process sampler could, so a probe would add nothing.

Strings (class names, GC names and causes) are passed as `char*`
pointers to NUL-terminated copies in a small per-thread buffer, so an
eBPF consumer reads them with the ordinary string-fetch helpers.

The subscription protocol is the standard USDT one, identical to what
the OTel eBPF profiler already does for native allocators:

1. Read the `stapsdt` notes in libjvm.so. Each note carries the probe
   location and the address of its semaphore, so this works on stripped
   binaries with no symbol table.
2. Attach a uprobe at the probe location.
3. Write a nonzero value to the semaphore address. The probe starts
   firing.
4. Write zero to stop. Firing stops immediately.

Points 3 and 4 are what make this useful in practice: a tracer can
subscribe and unsubscribe to individual events on a running process,
whenever it wants, without cooperation from the target.

## The two properties that matter

**Firing is decoupled from JFR recording.** The GC probes fire from
their call sites, not from JFR's event commit path, so they fire
whenever a tracer has subscribed, whether or not a recording was ever
started. The allocation sample, whose sampling decision normally lives
in the JFR event throttle, gets a standalone byte-distance sampler for
the no-recording case (a temporary stand-in, see Limits), so it fires
with JFR fully off as well. We verified the decoupled path end to end
on a stock build: with no recording started and no agent loaded,
clearing the semaphores produced zero probe executions, setting them
produced 814 events in an eight-second window (772 allocation samples,
18 GC, 24 GC-phase) with correct argument values (sample weights were
exact multiples of the application's 262160-byte allocation unit,
`name=G1New cause=G1 Evacuation Pause gcId=85`, `name=GC Pause`), and
clearing them again stopped the flow with zero further executions.
The consumer in that test was a debugger breakpoint planted at the
probe location; the gate mechanics and the note-carried semaphore
addresses are exactly what a real uprobe consumer uses.

**Unsubscribed cost is one byte load and a branch.** Each firing site is
wrapped in `if (JFR_<PROBE>_ENABLED())`, a generated check of the probe's
semaphore, predicted not-taken. No arguments are computed, no strings
copied, when the gate is closed. HotSpot's own always-on dtrace probes
have shipped this pattern in every Linux build for two decades.

## Why the change is small

Numbers first, from the branch: 12 files, +701/−12 against our master
base.

About half of that is the generator and build glue: the
`GenerateJfrFiles.java` emission code, a few lines of makefile to run
`dtrace -h`/`-G` and link `jfr.o`, and `usdt="true"` attributes. The
runtime side is one guarded fire call per event at the event call sites
(`gcTraceSend.cpp`, `jfrObjectAllocationSample.cpp`), each a handful of
lines, plus a support file
(`src/hotspot/share/jfr/support/jfrUsdtSupport.{hpp,cpp}`) holding the
string-copy helper, the thread-id table, and the temporary allocation
sampler.

The property that makes this maintainable rather than merely small:
adding an event is one attribute in `metadata.xml` plus, in the general
case, one guarded call where the event is produced. The provider file,
the is-enabled gates, the semaphore definitions, and the fire helper
are all generated. There is no per-probe code to keep in sync, and
because probes are generated from the same metadata as the JFR events,
the two cannot diverge.

There is no new public API, no spec text, no new command line flag, and
no behavior change for any build without `--enable-jvm-feature-dtrace`.
Build-time dependency is a `sys/sdt.h` with semaphore support (verified
against systemtap 4.8 headers); the run-time dependency is nothing.

## Why this helps JVM and OTel profiler cooperation

Today every vendor solves JVM observability for eBPF separately, by
shipping an agent that hooks internals. The existing USDT provider
does not change that: its probes speak to VM internals, and the
application-level answers stay behind JFR's in-process interfaces.
A generated, stable, zero-cost-when-idle USDT contract for JFR events
changes that in three ways.

1. **Profilers work on stock JVMs.** The OTel eBPF profiler, bpftrace,
   or any stapsemaphore-aware tracer can pick up JVM events the same way
   it picks up allocator events today: read notes, plant uprobes, flip
   semaphores. No restart, no agent, no application change.
2. **Vendors stop duplicating each other.** One contract, defined and
   maintained by the JVM, replaces N private agents that each have to
   chase JVM internals across releases. Vendors can spend the effort on
   the consumer side instead: aggregation, correlation, presentation.
3. **JFR stays the source of truth.** Event semantics, throttling, and
   curation remain in JFR, reviewed by JFR owners, documented in one
   place. The probes are a view onto that, generated from it, so the JVM
   team keeps full control of what is exposed and when.

For Oracle this extends the reach of JFR beyond in-process consumers
without growing any API surface to maintain. For Amazon, Corretto
gains the same property native runtimes already have: JVM events
available to eBPF-based tooling with no agent and no vendor-specific
hooks, so any consumer, OTel continuous profiling included, reads
them through one contract.

## Limits of the prototype

Stated plainly, so nobody finds these out later in review.

- Linux only. The macOS dtrace tooling has its own is-enabled story that
  we have not verified; the provider generation is behind
  `defined(LINUX)` and the dtrace feature.
- The `dtrace` JVM feature is opt-in today. Getting these probes into
  shipped JDKs is a distribution decision as much as an upstream one.
- The allocation sample's standalone sampler is a temporary stand-in:
  it draws a byte-distance interval from an exponential distribution
  (512 KiB mean), which bounds sample density, not rate, so an
  allocation storm can fire more than JFR's throttle would have
  allowed. The end state is to feed the probe from JFR's own
  rate-limiting throttle (the 150/s default cap), for exactly its
  rate-limiting property, once that throttle can run independently of
  a recording. When a recording is active, the JFR throttle stays the
  single sampling decision for both consumers.
- String arguments are copied into a small per-thread ring buffer at
  firing time and are valid until overwritten; a uprobe reads them at
  trap time, which is the normal consumption model, but a consumer
  cannot stash the pointer and dereference it later.
