# Market Intelligence — User Flows and Sequences

This reference describes the current responsibility of each market-intelligence module and identifies the clean extension points for the next enhancements.

## 1. Module map

```mermaid
flowchart LR
    U["Authenticated user"] --> API["Subscription API — next"]
    API --> APP["market-intelligence-application"]
    APP --> BROKER["Broker connection application"]
    BROKER --> Z["Zerodha live stream"]
    Z --> APP
    APP --> DOMAIN["market-intelligence-domain"]
    APP --> INFRA["market-intelligence-infrastructure"]
    INFRA --> DB[("PostgreSQL")]
    INFRA --> CAL["Calendar loader — next"]
    APP --> READ["Bar and intelligence query API — next"]

    classDef next stroke-dasharray: 5 5;
    class API,CAL,READ next;
```

## 2. market-intelligence-domain

### User flow

The domain module owns market-time rules, canonical bar revisions, feature calculation, evidence quality, and deterministic market-context composition. It does not connect to brokers or databases.

```mermaid
flowchart TD
    A["Enhancement developer selects an analysis cutoff"] --> B["Select point-in-time eligible bar revisions"]
    B --> C["Build the causal input manifest"]
    C --> D["Resolve versioned feature definitions"]
    D --> E["Calculate indicators and features"]
    E --> F["Assess coverage, freshness, and quality"]
    F --> G{"Evidence usable?"}
    G -- "Yes" --> H["Create initial or expanded evidence"]
    G -- "No" --> I["Record exclusion or unknown state"]
    H --> J["Fuse evidence under a versioned policy"]
    I --> J
    J --> K["Compose deterministic market-context snapshot"]
    K --> L["CORE AI and downstream decisioning"]

    N1["Next: new feature calculators"] -.-> D
    N2["Next: sector and breadth evidence"] -.-> H
    N3["Next: model-ready feature vectors"] -.-> K

    classDef next stroke-dasharray: 5 5;
    class N1,N2,N3 next;
```

### Sequence

```mermaid
sequenceDiagram
    actor Analyst as Enhancement or analysis caller
    participant Selector as Point-in-time selector
    participant Registry as Feature registry
    participant Engine as Feature engine
    participant Quality as Quality engine
    participant Evidence as Evidence factory and fusion
    participant Context as Market context composer

    Analyst->>Selector: Evaluate subject at analysis and knowledge cutoffs
    Selector->>Selector: Choose eligible immutable bar revisions
    Selector-->>Analyst: Causal bar inputs and manifest
    Analyst->>Registry: Resolve feature profile and versions
    Registry-->>Engine: Feature definitions and calculators
    Analyst->>Engine: Calculate features from causal inputs
    Engine-->>Quality: Feature values and readiness metadata
    Quality->>Quality: Apply coverage and quality policies
    Quality-->>Evidence: Accepted values, exclusions, and unknowns
    Evidence->>Evidence: Create and fuse versioned evidence
    Evidence-->>Context: Evidence dimensions and provenance
    Context->>Context: Generate deterministic context hash
    Context-->>Analyst: Market-context snapshot
```

## 3. market-intelligence-application

### User flow

The application module coordinates one user's live subscription and converts normalized broker ticks into final or corrected canonical bars.

```mermaid
flowchart TD
    U["Authenticated user selects instruments"] --> S["Start market-intelligence subscription"]
    S --> R["Resolve symbols to provider instrument IDs"]
    R --> M["Create or replace the user's managed stream"]
    M --> T["Receive normalized live ticks"]
    T --> C{"Effective market session found?"}
    C -- "No" --> RJ["Record rejected tick"]
    C -- "Yes" --> P{"Continuous trading phase?"}
    P -- "No" --> W["Advance watermark"]
    W --> RJ
    P -- "Yes" --> D{"Exact duplicate?"}
    D -- "Yes" --> RJ
    D -- "No" --> O["Order by exchange and receipt time"]
    O --> B["Aggregate configured OHLCV intervals"]
    B --> F{"Finality watermark reached?"}
    F -- "No" --> T
    F -- "Yes" --> PB["Publish immutable final bar revision"]
    T --> L{"Late tick changes a published bar?"}
    L -- "Yes" --> CR["Publish corrected revision"]
    L -- "No" --> T
    U --> X["Stop subscription"]
    X --> CL["Close only that user's stream"]

    N1["Next: authenticated REST API"] -.-> S
    N2["Next: stream health and lag metrics"] -.-> M
    N3["Next: partitioned processing and recovery"] -.-> O

    classDef next stroke-dasharray: 5 5;
    class N1,N2,N3 next;
```

### Sequence

```mermaid
sequenceDiagram
    actor User
    participant API as Subscription API — next
    participant Facade as UserMarketIntelligenceSubscriptionService
    participant Resolver as Instrument resolver
    participant Manager as Per-user subscription manager
    participant Broker as Zerodha live provider
    participant Consumer as MarketIntelligenceTickConsumer
    participant Calendar as MarketSessionPort
    participant Bars as MarketBarPublicationPort
    participant Rejects as MarketTickRejectionPort

    User->>API: Start stream for selected instruments
    API->>Facade: subscribe(user context, instruments)
    Facade->>Resolver: Resolve provider instrument IDs
    Resolver-->>Facade: Resolved instruments
    Facade->>Manager: Subscribe with canonical consumer
    Manager->>Broker: Connect user's stream
    Broker-->>Manager: CONNECTED
    Manager-->>API: Per-user subscription status
    API-->>User: Streaming started

    loop Every broker frame
        Broker-->>Manager: Normalized market ticks
        Manager->>Consumer: onTicks(user, account, ticks)
        Consumer->>Calendar: Resolve effective session
        alt Tick is invalid, duplicate, or out of session
            Consumer->>Rejects: Persist rejection reason
        else Tick is admitted
            Consumer->>Consumer: Reorder, aggregate, and advance watermark
            alt Completed bar reaches finality
                Consumer->>Bars: Publish FINAL revision
            else Late tick changes published bar
                Consumer->>Bars: Publish CORRECTED revision
            end
        end
    end

    User->>API: Stop stream
    API->>Facade: unsubscribe(user)
    Facade->>Manager: Close user's managed stream
    Manager->>Broker: Close WebSocket
    API-->>User: Streaming stopped
```

## 4. market-intelligence-infrastructure

### User and operator flow

The infrastructure module supplies the effective calendar, durable append-only persistence, Spring composition, and bounded in-memory retention.

```mermaid
flowchart TD
    OP["Market operations user"] --> LOADER["Load or revise exchange calendar — next"]
    LOADER --> SESSION[("market_session")]
    LOADER --> PHASE[("market_session_phase")]

    START["Application starts"] --> CFG["Create configured runtime beans"]
    CFG --> CAL["JDBC session adapter"]
    CFG --> STORE["JDBC intelligence store"]
    CFG --> CONSUMER["Canonical tick consumer"]
    CFG --> FACADE["Per-user intelligence subscription facade"]

    CONSUMER --> CAL
    CAL --> SESSION
    CAL --> PHASE
    CONSUMER --> STORE
    STORE --> BARS[("market_bar_revision")]
    STORE --> REJECT[("market_tick_rejection")]

    TIMER["Retention scheduler"] --> EVICT["Evict session ledgers after correction window"]
    EVICT --> CONSUMER

    BARS --> QUERY["Point-in-time read repository — next"]
    BARS --> OUTBOX["Bar revision event outbox — next"]
    REJECT --> OBS["Quality and operations dashboard — next"]

    classDef next stroke-dasharray: 5 5;
    class LOADER,QUERY,OUTBOX,OBS next;
```

### Sequence

```mermaid
sequenceDiagram
    actor Operator as Market operations user
    participant Loader as Calendar loader — next
    participant DB as PostgreSQL
    participant Spring as Spring configuration
    participant Consumer as Tick consumer
    participant Calendar as JDBC session adapter
    participant Store as JDBC intelligence store
    participant Reaper as Retention scheduler

    Operator->>Loader: Publish venue session and phase version
    Loader->>DB: Insert effective-dated session definition
    DB-->>Loader: Calendar version stored

    Spring->>Spring: Read timeframes, lateness, policy versions, and retention
    Spring->>Calendar: Create session port
    Spring->>Store: Create publication and rejection ports
    Spring->>Consumer: Create canonical consumer

    Consumer->>Calendar: sessionFor(instrument, exchange timestamp)
    Calendar->>DB: Query effective session and ordered phases
    DB-->>Calendar: Session definition
    Calendar-->>Consumer: Domain MarketSession

    alt Canonical bar revision
        Consumer->>Store: publish(user, account, revision)
        Store->>DB: Append market_bar_revision
    else Rejected tick
        Consumer->>Store: reject(rejection)
        Store->>DB: Append market_tick_rejection
    end

    loop Configured retention sweep
        Reaper->>Consumer: Evict sessions ended before cutoff
        Consumer-->>Reaper: Completed ledgers removed
    end
```

## 5. Recommended enhancement order

```mermaid
flowchart LR
    E1["1. Calendar loader and validation"] --> E2["2. Authenticated subscription API"]
    E2 --> E3["3. Bar read and replay API"]
    E3 --> E4["4. Transactional bar event outbox"]
    E4 --> E5["5. Stream lag, rejection, and quality metrics"]
    E5 --> E6["6. Distributed partitioning and crash recovery"]
    E6 --> E7["7. Feature and market-context pipeline"]
```

The first enhancement should be the calendar loader because the live consumer intentionally rejects every tick for which no effective session definition exists.
