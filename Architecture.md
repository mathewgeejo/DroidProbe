# DroidProbe architecture

DroidProbe currently supports its instrumented sample package, `dev.droidprobe.sample`. Arbitrary APK support is future work. This is a graph of observed behavior, not complete application coverage.

```mermaid
flowchart LR
  Host[Authorized ADB / AndroidJUnitRunner launch] --> Instrumentation
  subgraph Device
    subgraph Runner package and process
      Instrumentation[UI Automator instrumentation] --> Validator[Action validator]
      Planner[Random / graph / local LiteRT-LM planner] --> Validator
      Validator --> Executor[Bounded action executor]
      Executor --> Observe[Observation and event synchronization]
      Observe --> Graph[Observed state graph]
      Graph --> Planner
      Observe --> Oracle[Approved executable invariants]
      Oracle --> Store[Atomic run JSON and PNG evidence]
      Store --> Review[Compose controller: configuration and completed-run review]
    end
    subgraph Separate sample package and process
      UI[Six Compose screens] --> Domain[Checkout and draft engine]
      Domain --> Backend[Application-controlled fake backend]
      Domain --> Persistence[Committed immutable fixture snapshots]
      Bridge[Debug-only signature-protected provider] --> Domain
    end
    Executor -->|semantic UI interaction| UI
    Observe -->|typed read| Bridge
    Executor -->|reset / faults / release| Bridge
  end
```

The installed controller has no cross-app automation privilege. An authorized test launch starts instrumentation targeting the runner itself. The target activity and provider run in the sample process; target force-stop/recreation cannot remove the runner's graph or reports. The controller stays out of the foreground during exploration. `DroidProbe` logcat entries report structured progress; completed results reside in runner private storage and can be shared with a FileProvider.

The development APKs use the same default Android debug key. The provider lives in `src/debug`, and the SDK is a `debugImplementation` of the sample. Every `ContentProvider.call` checks the signature permission and calling UID's signature explicitly. Read/reset/fault requests include a protocol version and run ID; stale run IDs are rejected. Mutations are marshalled to the sample's main thread, committed as a single SharedPreferences JSON snapshot, and then published to Compose. This small MVP uses atomic JSON files rather than Room: reports are bounded and easy to inspect/export. A migration to Room can preserve the protocol.

```mermaid
flowchart TD
  Reset[Reset fixtures, mode, faults and orientation] --> Launch[Launch sample]
  Launch --> Observe[Read semantic UI and SDK snapshot]
  Observe --> Normalize[Normalize business state and recent history]
  Normalize --> Candidates[Enumerate supported valid actions]
  Candidates --> Rank[Novelty + workflow risk + failure relevance - cost]
  Rank --> Model{Local planner selected and usable?}
  Model -->|yes outside pending response window| Proposal[Bounded JSON proposal and repair]
  Model -->|no| Baseline[Graph or random baseline]
  Proposal --> Validate[Validate schema, package, selector, bounds and budget]
  Baseline --> Validate
  Validate --> Execute[Execute exact validated ActionIR]
  Execute --> Observe
  Observe --> Assert[Evaluate approved business invariants]
  Assert -->|violation| Evidence[Confirmed invariant evidence]
  Evidence --> Replay[Reset and replay without planner]
  Replay --> Minimize[Dependency-aware delta debugging]
  Minimize --> Export[Scenario, Kotlin harness test and evidence bundle]
```

Observations distinguish idle, submitted, persisted/acknowledgement pending and completed states even when the UI tree is identical. Run IDs, screenshot paths, event timestamps and record IDs are excluded from signatures; business phase, order multiplicity, saved content, cart, fault state, recent event types and action history remain. Selector resource keys come from Compose `testTagsAsResourceId`, validated through UI Automator. Missing actionable accessibility information produces a recorded limitation.

Each executed action retains its exact parameters, preconditions, event dependencies, timeout, before/after observation sequences and new app events. Response delay is a deterministic acknowledgement gate, not a global network switch. Rotation awaits a new activity generation; background awaits `onPause`; typed business waits poll structured conditions with bounded deadlines. Force-stop is fixture-reset plumbing and is never described as OS process death. Supported system interactions are home, back, orientation and authorized package launch. The model cannot emit executable shell commands; the only shell command in the driver is the literal allowlisted fixture force-stop.

The sample's faulty activity recreation re-submits an accepted logical checkout while its acknowledgement is held; the faulty backend inserts another record. Corrected activity handling retains the pending operation, and backend persistence is idempotent by logical operation ID. The draft defect loses acknowledged content on recreation; corrected restoration uses the committed content. Identical executable assertions run against both modes.

```mermaid
flowchart TD
  Input[Recorded failure and exact fingerprint] --> Chunk[Remove an action chunk]
  Chunk --> Dependencies{Dependencies still valid?}
  Dependencies -->|no| Next[Try next chunk or finer partition]
  Dependencies -->|yes| Reset[Reset fixtures and replay candidate]
  Reset --> Repeat[Repeat within configured threshold and budget]
  Repeat --> Predicate{Same applicable invariant violation?}
  Predicate -->|yes enough times| Keep[Keep deletion]
  Predicate -->|no / invalid / timeout / infrastructure| Next
  Keep --> Chunk
  Next --> Budget{More candidates and budget?}
  Budget -->|yes| Chunk
  Budget -->|no| Output[Smallest reproduction found within configured search budget]
```

Replay outcomes are reproduced, failure not observed, invalid precondition, infrastructure failure and timeout. The regression harness passes only if the same applicable assertion executes and passes. Minimization never counts an invalid replay as preserving a failure, and records raw successes/attempts. Failure fingerprints include the invariant and expected business result, preventing unrelated predicates on the same screen from merging.

The local adapter calls the actual pinned LiteRT-LM Engine/Conversation API on a background dispatcher. It uses CPU, verifies artifact SHA-256, records model identity/configuration, and registers no executable tools. A small quantized `.litertlm` model must be benchmarked on the intended ARM64 physical device. Missing/incompatible models remain explicitly labeled graph baselines. Model latency/parse failures/fallbacks are stored; no cloud inference is implemented.

Crash diagnosis and generalized responsiveness oracles require additional supported evidence collection. Current lifecycle/business failures are confirmed by SDK predicates; runner exceptions remain infrastructure failures and workflow deadline breaches remain suspected stalls. No LLM screen opinion becomes a confirmed finding.
