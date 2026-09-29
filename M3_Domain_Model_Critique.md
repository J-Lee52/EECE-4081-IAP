# Critique of the AI-Generated Domain Model

## Where the AI Over-Modelled

The AI over-modelled SessionHistory as a core domain class. The requirements state that the application must provide a history view containing previously saved sessions, but they do not establish “history” itself as a domain entity. In my design, saved sessions are accessed through persistence behavior rather than being owned by a separate SessionHistory object. Because of that, I would leave SessionHistory outside the core domain model.

The AI also added splitStatisticsAvailable as a Boolean attribute in SessionResults. I consider this redundant derived state rather than a separate value that needs to be stored. Whether split statistics are available can already be determined from the recorded shots or from whether valid split-statistic values exist. Keeping both the statistics and a separate Boolean introduces the possibility that the Boolean could disagree with the calculated values. The requirements do require the application to indicate when split statistics are unavailable, but they do not require that availability to be stored as its own domain attribute.

## Where the AI Under-Modelled

The most important omission is the session timing origin. The requirements explicitly state that the start beep establishes the timing start point for the session. In my design, I represent this explicitly with ShootingSession.startTime, and each Shot.timestamp is measured as elapsed time from that starting point. The AI modeled the shot timestamp but did not model the reference point that gives that timestamp its meaning.

The AI also under-modelled the structure of the start-delay configuration. It used a StartDelayMode enumeration together with nullable fixedDelay, minimumDelay, and maximumDelay fields. Although this represents the information in the requirements, it allows invalid combinations, such as Fixed mode existing alongside random-range values.

```text
StartDelay
├── FixedDelay(duration)
└── RandomDelay(minDelay, maxDelay)
```

This makes the two configurations structurally distinct and prevents unrelated delay values from being present at the same time.

Compared with my design, the AI also did not explicitly model where the statistics-calculation behavior belongs. I chose to introduce a stateless SessionStatisticsCalculator so that the rules for calculating shot count, total time, fastest split, average split, and slowest split are separated from the result data and can be tested independently. My architecture intentionally treats this calculator as a domain service. This is a design refinement I made for separation of concerns and testability, not a class explicitly required by the requirements.

## Where the AI Guessed a Relationship or Made a Different Responsibility Choice

The clearest unsupported relationship is the composition from SessionHistory to Session. The AI models:

```text
SessionHistory "1" *-- "0..*" Session
```

Composition implies a strong ownership or lifecycle relationship. The requirements establish that saved sessions can be listed and reviewed, but they do not establish that a SessionHistory domain object owns those sessions. In my design, saved sessions are persisted and retrieved through repository behavior instead.

The AI also made several responsibility assignments differently from my design. These are not baseless inventions, because the underlying behaviors do appear in the requirements, but I place them elsewhere in the architecture.

For example, the AI places state: SessionState, stop(), and save() directly on Session. The five states themselves are supported by the requirements, including the transition to Completed, so the AI was right to recognize them. My disagreement is about ownership.

In my design, TimerState belongs to TimerEngine, because the states describe the runtime workflow of the timer rather than persistent session data. The architecture specifically treats TimerState as transient application state rather than part of a saved ShootingSession.

Likewise, the AI was correct to recognize the need for stop(), but I place that behavior on TimerEngine. Stopping the timer controls the workflow transition from Recording to Completed and stops further shot acceptance. Saving is also required behavior, but in my design it is handled through SessionRepository because persistence is outside the responsibility of ShootingSession.

The AI also connects every Session to exactly one SessionResults. I do not consider the existence of a results object incorrect. My difference is that I treat SessionStatistics as a derived value produced from the authoritative shot records when needed, rather than as separately authoritative stored state.

## Where the AI Was Right

The AI correctly identified the session itself as the central domain concept. Its Session class corresponds closely to my ShootingSession, which represents one timing session and contains the configuration and recorded shots for that run.

It also correctly modeled the relationship between a session and its shots. A session can contain zero or more Shot records, and each shot contains a sequential shot number, timestamp, and optional split time. This matches the requirements, including the rule that the first shot has a timestamp but no split from a previous shot.

The AI was also correct to model the session settings as a value object. I make the same general choice with TimerSettings, because the settings are configuration values for a session rather than an independently identified entity.

Its SessionResults concept is also reasonable. My design uses the name SessionStatistics, but both models recognize that shot count, total time, fastest split, average split, and slowest split are derived from recorded shots rather than being independent entities. My disagreement is with how that result is related to the session, not with the idea of a derived result object itself.

The AI was also correct to recognize the required stop behavior and the five lifecycle states. My design assigns those responsibilities to TimerEngine, but the AI did correctly identify that those behaviors exist in the system.

Finally, the AI appropriately avoided adding UI classes, Android microphone APIs, database classes, or other platform-specific implementation details to the domain model. I agree with that boundary because those components belong to the broader application architecture rather than the core M3 domain model.

## Overall Comparison

The AI draft identified many of the correct concepts, but it mixed some application-workflow and persistence responsibilities into the session entity, omitted the explicit timing origin, and represented the start-delay configuration in a way that allows invalid combinations.

My design keeps the core domain focused on ShootingSession, Shot, TimerSettings, StartDelay with FixedDelay and RandomDelay, SessionStatistics, and SessionStatisticsCalculator. I intentionally keep TimerState, TimerEngine, history management, persistence, audio processing, and UI behavior outside the core domain model. This gives the model a narrower responsibility boundary while making the timing, configuration, and statistics rules more explicit.
