# AI Elicitation Audit Log

## AI Tool
GitHub Copilot Chat in Android Studio

## Prompt
I am developing an Android mobile shot-timer application for recreational and competitive shooters. The application will use a smartphone’s microphone to detect firearm discharges, record shot timestamps and split times, display session results, and allow the ability to save the session results to local storage for future review.

Elicit the functional and non-functional requirements for this application. For the functional requirements, provide:
- user stories in the format “As a [user], I want [goal], so that [reason]”
- acceptance criteria for each user story
- non-functional requirements stated in measurable or testable terms
- questions you would ask the stakeholder to clarify ambiguous or missing requirements

Do not write or modify any code.


# 1. What the AI Missed

## Start-beep suppression as a specific requirement
The model included a countdown/start signal and mentioned that feedback should not interfere with shot detection, but it did not clearly define the specific requirement that the application’s own start beep must be prevented from being detected as a shot.

This is important because the same phone is both producing the start signal and listening through its microphone. My design treats the start beep as a specific event before recording begins, with suppression or filtering needed so the beep is not recorded as the first shot.

## Explicit timer state model
The model did not ask about or define the explicit application states I intend to use, such as Idle, Preparing, WaitingForStart, Recording, and Completed.

Instead, it described behavior procedurally. My design uses explicit states to prevent contradictory conditions and to make transitions easier to reason about and test.

## Shot-detection cooldown as a configurable setting
The model mentioned cooldown behavior in its non-functional/testing suggestions, but it did not identify shot-detection cooldown as one of the user-configurable timer settings in the main configuration story.

My requirements include microphone threshold, start delay, and cooldown as settings the shooter can configure before the session.

## Relationship between user workflow and internal states
The model described starting, detecting, and reviewing sessions, but it did not identify the relationship between the user-facing workflow and the internal state transitions.

That relationship is important in my design because user actions such as Start, delay completion, and Stop drive transitions between defined timer states.

## Requirement that settings remain fixed during an active session
The model allowed configuration before a session, but it did not explicitly ask whether those settings may change while a session is active.

My requirement is that session settings be configured while the timer is idle and not be changed in a way that affects the active recording session.


# 2. What the AI Invented

## Discipline type
The model added session discipline types such as pistol, rifle, and shotgun.

I did not specify discipline selection as a feature. The current application is focused on shot timing, not categorizing sessions by firearm discipline.

## Round count
The model added a configurable round count.

I did not state that the shooter must enter a planned number of rounds or shots before starting a session. My current design allows the user to start and stop the session without a required shot count.

## Saved settings profiles
The model added the ability to save timer settings as reusable profiles.

This was not part of my stated scope. It may be useful in a larger application, but it adds configuration and persistence requirements beyond the current project.

## Automatic calibration mode
The model invented an ambient-noise calibration/test mode that recommends sensitivity.

I had not requested automatic calibration. My current design uses a configurable microphone detection threshold, but automatic calibration is outside the defined scope.

## Configurable timeout when no shots are detected
The model added a warning after a configurable period with no detected shots.

I did not request a no-shot timeout or warning feature.

## Shot-confirmation feedback
The model suggested an optional audible, vibration, or visual confirmation after each detected shot.

I did not request confirmation feedback for each shot. The required start beep is different from a confirmation signal after every detection.

## Sorting and filtering session history
The model added sorting and filtering saved sessions by date or discipline.

My scope includes saving and reviewing previous sessions, but not advanced history filtering.

## Export functionality
The model suggested exporting results and asked about CSV/JSON export.

Exporting or sharing session results was not part of my requested scope.

## Cloud sync and backup
The model asked whether cloud synchronization, export, or backup should be supported.

My prompt specifically described saving results to local storage. Cloud synchronization is not part of the current project.

## Shooter and equipment metadata
The model suggested storing shooter name, firearm model, ammunition type, weather, and range conditions.

None of these were part of the application concept I provided. These would significantly expand the data model without supporting the core shot-timing goal.

## Training mode without shot detection
The model suggested a manual-timing mode that does not use microphone detection.

This was not requested and conflicts with the main technical focus of the project, which is microphone-based shot detection.


# 3. What the AI Got Right That I Had Not Explicitly Considered

## Microphone permission handling
The model explicitly identified microphone permission as a requirement and described what should happen if permission is denied.

I had focused primarily on the normal session flow and had not written a user story or acceptance criterion covering permission denial. This is useful because microphone access is required for the app’s core function.

## Releasing microphone resources
The model stated that audio capture should stop and resources should be released when the session ends or when the app exits.

I had not explicitly included this behavior in my initial requirements, but it is a useful lifecycle requirement for an Android app using the microphone.

## Preventing background recording
The model identified that the application should not continuously record audio outside active sessions.

This is a useful privacy and resource-management consideration that I had not explicitly stated.

## Storage failure handling
The model stated that saving should fail gracefully and display an error if storage is unavailable.

I had defined saving and reviewing sessions, but I had not yet specified failure behavior when local storage cannot be used.

## Accessibility and device compatibility
The model raised accessibility, screen-size support, and Android-version compatibility.

These are valid non-functional concerns I had not included in the initial concept. They may not all become formal requirements for this milestone, but they are useful questions for future refinement.

## Quantifiable performance targets
The model provided measurable examples for UI update latency, timing error, startup time, session processing time, storage capacity, and battery usage.

Some of the specific numbers are unsupported and would need validation, but the response correctly demonstrated that non-functional requirements should be written in measurable, falsifiable terms.


# 4. Overall Judgment

GitHub Copilot was useful as a requirements-breadth tool, but its output should not be accepted directly as the project specification.

Its strongest contribution was identifying operational concerns I had not explicitly written down, especially microphone permission handling, resource release, storage failure behavior, and privacy-related microphone use. These suggestions helped reveal edge cases around the Android platform rather than only the normal shot-timing workflow.

However, the model also invented a substantial amount of functionality that I had never requested, including discipline selection, round count, reusable settings profiles, automatic calibration, session-history filtering, export, cloud synchronization, shooter/equipment metadata, and a manual timing mode. Accepting these requirements without review would significantly expand the scope of the project.

The model also missed some details that are important specifically to my design, including explicit application states, the relationship between workflow and state transitions, the requirement to keep active-session settings fixed, and a clear requirement that the app’s own start beep not be recorded as a shot.

My conclusion is that the AI output is useful for generating questions and identifying edge cases, but it is not reliable enough to define project scope on its own. Human review is required to separate useful requirements from unsupported assumptions and invented features.