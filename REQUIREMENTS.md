# App Requirements
## Functional Requirements

### User Story 01 - Configure Timer Settings
As a user,  
I want to configure the microphone detection threshold, start-delay settings, and shot-detection cooldown before starting a session,  
so that the timer operates using settings appropriate for my session.

#### Acceptance Criteria
- Given the timer is in the Idle or Completed state, the user can configure the microphone detection threshold, start-delay mode, and shot-detection cooldown for the next session.
- The user can choose either a Fixed or Random Range start-delay mode.
- When Fixed mode is selected, the user can configure one fixed delay duration.
- When Random Range mode is selected, the user can configure a minimum delay and a maximum delay.
- In Random Range mode, the configured minimum delay shall not exceed the configured maximum delay.
- When a session begins, the application uses the settings selected before the session started.
- While the timer is in Preparing, WaitingForStart, or Recording, the session settings cannot be changed.
- After the active session ends, the user can modify the settings for the next session without altering the completed session results.

### User Story 02 - Start a Timing Session
As a user,  
I want to start a timing session,  
so that the application can begin the configured shot-timing sequence.

#### Acceptance Criteria
- Given the timer is in the Idle or Completed state, the user can start a new timing session.
- If the timer is in Completed when the user starts a new session, the application internally resets the timer workflow to Idle and then begins the normal start sequence by entering Preparing; no separate Reset action is required.
- When the user starts a session, the application begins preparing the microphone for shot detection.
- The application uses the timer settings selected before the session begins.
- A new session begins with no previously recorded shots or split times.
- The application does not allow a second session to be started while another session is active.

### User Story 03 - Execute the Start Delay and Beep Sequence
As a user,  
I want to receive a start signal after the configured delay,  
so that I know when the timed shooting session has begun.

#### Acceptance Criteria
- After a session is started, the application determines the start delay using the configured start-delay mode.
- When Fixed mode is selected, the application waits for the configured fixed delay duration.
- When Random Range mode is selected, the application selects a delay within the configured minimum and maximum values and waits for that selected duration.
- The application remains in the WaitingForStart state until the selected delay has completed.
- When the delay completes, the application plays one start beep.
- The start beep establishes the timing start point for the session.
- The start beep occurs before the application begins accepting shot detections for the session.
- The application prevents the start beep from being recorded as a shot.

### User Story 04 - Detect and Record Shots
As a user,  
I want to have my shots automatically detected and recorded,  
so that I do not need to manually enter shot times during a session.

#### Acceptance Criteria
- While the timer is in the Recording state, the application monitors microphone input for shot events.
- When microphone input meets the configured detection threshold, the application identifies the input as a potential shot.
- After a valid shot is detected, the application records the shot timestamp as the elapsed time since the session timing start point established by the start beep.
- Each valid shot is assigned a sequential shot number within the current session.
- The configured shot-detection cooldown is applied after a recorded shot before another shot can be accepted.
- Microphone events that do not satisfy the detection criteria are not recorded as shots.

### User Story 05 - Calculate and Display Live Timing Results
As a user,  
I want to see the timing information for each detected shot,  
so that I can evaluate my performance during the session.

#### Acceptance Criteria
- When the first shot is recorded, the application displays its timestamp without a split time from a previous shot.
- For each shot after the first, the application calculates the split time as the difference between the current shot timestamp and the previous shot timestamp.
- The displayed shot information includes the shot number and shot timestamp.
- For each applicable shot, the displayed information also includes its calculated split time.
- The displayed shot list updates after each valid shot is recorded.
- While the session is recording, the displayed session results update after each valid shot is recorded.
- The live session results display the current total number of recorded shots.
- While at least one shot has been recorded, the live total time equals the timestamp of the most recently recorded shot.
- When at least one valid split time exists, the live session results display the fastest split time, average split time, and slowest split time.
- If no valid split time exists, the live session results indicate that split statistics are unavailable.
- Split times and live session statistics are calculated using only shots recorded during the current session.

### User Story 06 - Stop and Review a Completed Session
As a user,  
I want to stop an active timing session and review the results,  
so that I can evaluate the shots recorded during that session.

#### Acceptance Criteria
- While the timer is recording, the user can stop the active session.
- When the session is stopped, the application stops accepting new shot detections for that session.
- The application changes the timer state to Completed.
- The completed session displays all shots recorded during that session.
- The completed session displays the total number of shots recorded during that session.
- For each recorded shot, the application displays the shot number, timestamp, and applicable split time.
- When at least one shot was recorded, the completed session displays total time as the timestamp of the final recorded shot.
- If no shots were recorded, the completed session indicates that total time is unavailable.
- The completed session displays the fastest split time, average split time, and slowest split time when at least one valid split time exists.
- The fastest, average, and slowest split times are calculated using only split times from the completed session.
- If the completed session does not contain enough shots to produce a split time, the application indicates that split statistics are unavailable.
- The timer remains in Completed while the user reviews or saves the finished session.
- Completed session results remain displayed and unchanged until the user starts a new session.
- When the user starts a new session from Completed, the application performs the internal reset to Idle and then begins the normal start sequence.

### User Story 07 - Save and Review Previous Sessions
As a user,  
I want to save completed timing sessions and review them later,  
so that I can look back at the results of previous sessions.

#### Acceptance Criteria
- After a session reaches the Completed state, the user can save the session.
- A saved session retains the recorded shots and their timing information.
- The application provides a history view containing previously saved sessions.
- The user can select a saved session from the history view to review its recorded results.
- Reviewing a saved session does not modify its stored shot or timing information.
- Saved sessions remain available after the application is closed and reopened.

## Non-Functional Requirements

### NFR 01 - Timing Calculation Accuracy
Given known shot timestamps, calculated split times shall differ from the mathematically expected split by no more than 0.01 seconds.

### NFR 02 - UI Update Latency
After the application accepts a shot-detection event, the displayed shot list and session results, including shot count, total time, and applicable split statistics, shall update within 200 milliseconds under normal application operation.

### NFR 03 - State Correctness
At all times during timer operation, the timer shall have exactly one active state from Idle, Preparing, WaitingForStart, Recording, or Completed.
