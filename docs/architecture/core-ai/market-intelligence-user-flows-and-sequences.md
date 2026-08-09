# Market Intelligence — User Flows and Sequences

This reference describes the current responsibility of each market-intelligence module and identifies the clean extension points for the next enhancements.

## 1. Module map

```mermaid
flowchart LR
    U["Authenticated user"] --> API["market-intelligence-api"]
    API --> APP["market-intelligence-application"]
    APP --> BROKER["Broker connection application"]
    BROKER --> Z["Zerodha live stream"]
    Z --> APP
    APP --> DOMAIN["market-intelligence-domain"]
    APP --> INFRA["market-intelligence-infrastructure"]
    INFRA --> DB[("PostgreSQL")]
    INFRA --> CAL["Governed calendar publication"]
    API --> READ["Point-in-time bar reads and replay"]

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

    N2["Next: stream health and lag metrics"] -.-> M
    N3["Next: partitioned processing and recovery"] -.-> O
    Q["Authenticated latest or replay request"] --> QC["Apply tenant, range, analysis, and knowledge cutoffs"]
    QC --> QP["Return latest revision per eligible interval"]

    classDef next stroke-dasharray: 5 5;
    class N2,N3 next;
```

### Sequence

```mermaid
sequenceDiagram
    actor User
    participant API as Authenticated subscription API
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
    OP["Market operations user"] --> LOADER["Publish validated exchange calendar"]
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

    BARS --> QUERY["Point-in-time causal query adapter"]
    BARS --> OUTBOX["Bar revision event outbox — next"]
    REJECT --> OBS["Quality and operations dashboard — next"]

    classDef next stroke-dasharray: 5 5;
    class OUTBOX,OBS next;
```

### Sequence

```mermaid
sequenceDiagram
    actor Operator as Market operations user
    participant Loader as Calendar publication service
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

## 5. market-intelligence-api

### User flow

The API module exposes one subscription resource per authenticated user. User identity is always taken from the verified bearer token and is never accepted from request data.

```mermaid
flowchart TD
    U["User sends bearer token"] --> AUTH{"Token valid?"}
    AUTH -- "No" --> UNAUTH["401 Unauthorized"]
    AUTH -- "Yes" --> CMD{"Requested operation"}
    CMD -- "PUT" --> VALIDATE["Validate account and instrument list"]
    VALIDATE --> OWN["Derive user ID from authenticated identity"]
    OWN --> START["Create or replace only this user's stream"]
    START --> SAFE["Return broker-neutral status without provider tokens"]
    CMD -- "GET" --> STATUS{"Subscription exists?"}
    STATUS -- "Yes" --> SAFE
    STATUS -- "No" --> NOTFOUND["404 Not Found"]
    CMD -- "DELETE" --> STOP["Idempotently stop only this user's stream"]
    STOP --> NOCONTENT["204 No Content"]
    CMD -- "GET latest bar" --> LATEST["Apply analysis and knowledge cutoffs"]
    LATEST --> ONE{"Eligible revision exists?"}
    ONE -- "Yes" --> BAR["Return latest causal revision"]
    ONE -- "No" --> NOTFOUND
    CMD -- "GET replay" --> RANGE["Validate bounded range and opaque cursor"]
    RANGE --> PAGE["Return chronological causal page"]
    PAGE --> MORE{"More intervals available?"}
    MORE -- "Yes" --> CURSOR["Return next opaque cursor"]
    MORE -- "No" --> DONE["Replay complete"]

    N1["Next: rate limits and quotas"] -.-> VALIDATE
    N2["Next: stream event notifications"] -.-> SAFE
    classDef next stroke-dasharray: 5 5;
    class N1,N2 next;
```

### Sequence

```mermaid
sequenceDiagram
    actor User
    participant Security as Bearer-token filter
    participant API as Subscription controller
    participant Facade as User intelligence subscription service
    participant Query as Market bar query service
    participant Manager as Per-user stream manager

    User->>Security: PUT subscription with bearer token
    Security->>Security: Authenticate token and establish identity
    alt Invalid or missing token
        Security-->>User: 401 Unauthorized
    else Authenticated
        Security->>API: Request plus authenticated principal
        API->>API: Validate account and instruments
        API->>Facade: subscribe(principal user, account, instruments)
        Facade->>Manager: Create or replace user's singleton stream
        Manager-->>Facade: Current stream status
        Facade-->>API: Broker-neutral subscription status
        API-->>User: 200 OK
    end

    User->>Security: GET subscription
    Security->>API: Authenticated principal
    API->>Facade: status(principal user)
    Facade-->>API: Status or empty
    API-->>User: 200 OK or 404 Not Found

    User->>Security: DELETE subscription
    Security->>API: Authenticated principal
    API->>Facade: unsubscribe(principal user)
    Facade->>Manager: Close only that user's stream
    API-->>User: 204 No Content

    User->>Security: GET latest bar or replay with two causal cutoffs
    Security->>API: Authenticated principal
    API->>Query: Query tenant bars by analysis and knowledge cutoff
    Query-->>API: Latest revision or bounded chronological page
    API-->>User: Causal bar response and optional continuation cursor
```

## 6. Recommended enhancement order

```mermaid
flowchart LR
    E1["1. Transactional bar event outbox"] --> E2["2. Stream lag, rejection, and quality metrics"]
    E2 --> E3["3. Distributed partitioning and crash recovery"]
    E3 --> E4["4. Feature and market-context pipeline"]
```

The governed calendar publisher, authenticated subscription commands, and point-in-time bar reads/replay are now implemented. The next enhancement should stage every published bar revision through the transactional outbox for reliable downstream delivery.
