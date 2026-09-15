# AI Prompt-and-Diff Log

## Milestone 1 — GitHub Copilot Verification

**AI tool:** GitHub Copilot Chat  
**Environment:** Android Studio

### Prompt #1

> Explain what the onCreate method in this starter Android project does.

### Result

GitHub Copilot inspected the Android project and explained the `onCreate` method in `MainActivity.kt`, including its use of `super.onCreate(savedInstanceState)`, `enableEdgeToEdge()`, and `setContent`.

### Code Changes

No code changes were requested or generated from this prompt.

**Diff:** None.


## GitHub Copilot Chat — Requirements Elicitation
### Prompt #2

>I am developing an Android mobile shot-timer application for recreational and competitive shooters. The application will use a smartphone’s microphone to detect firearm discharges, record shot timestamps and split times, display session results, and allow the ability to save the session results to local storage for future review. 
Elicit the functional and non-functional requirements for this application. For the functional requirements, provide:
user stories in the format “As a [user], I want [goal], so that [reason]”
acceptance criteria for each user story
non-functional requirements stated in measurable or testable terms
questions you would ask the stakeholder to clarify ambiguous or missing requirements.
> Do not write or modify any code.

### Diff
No application source code was modified by GitHub Copilot.

Documentation changes resulting from this interaction:
- Added `AI_ELICI_AUDIT_LOG.md` comparing the AI-generated requirements with my independently written requirements.
- No Copilot-generated requirement was copied directly into `REQUIREMENTS.md` without review.

### Notes
The Copilot response was used for requirements elicitation and audit purposes only.