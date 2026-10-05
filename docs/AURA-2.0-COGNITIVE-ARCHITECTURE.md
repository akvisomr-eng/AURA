# AURA 2.0 — Cognitive Architecture Foundation

AURA 2.0 introduces the first executable foundation for the long-term AURA vision.

## Core layers

- Cognitive runtime and intent routing
- Session memory
- Affective state and personality expression
- Self-model and capability awareness
- World-model entity registry
- Capability planning and lifecycle registry
- Universal device descriptors/adapters
- Indonesian voice presentation with affect-driven prosody

## Design principle

AURA does not need to rewrite its cognitive core to gain a new ability. New abilities should enter through a capability boundary:

DISCOVERED -> PLANNED -> BUILDING -> TESTING -> SANDBOXED -> AUTHORIZED -> ACTIVE

Sensitive capabilities require explicit authorization. Measurements are distinguished from observations, inferences, and hypotheses.

## Target evolution

Android remains the first embodiment. The architecture is intended to extend toward Windows, Linux, embedded/Arduino-class nodes, vehicles, XR, robots, and future humanoid embodiments.

Kotlin Multiplatform is a suitable future shared-code direction for Android, desktop, server, web and native targets, while platform-specific adapters remain responsible for hardware and OS integration.

## Safety boundaries

- No unauthorized identity lookup or surveillance.
- No bypass of device security or vendor authorization.
- No autonomous vehicle control.
- Physical/electrical diagnosis must distinguish measured data from inference.
- Dynamic capability generation is sandboxed and policy-gated before activation.

## Next engineering layers

1. persistent semantic/episodic memory
2. world-model persistence and temporal state
3. capability discovery adapters
4. external camera/depth/LiDAR device fabric
5. multi-agent orchestration
6. barge-in and conversational timing
7. richer affective prosody
8. distributed AURA nodes
