package com.ligaya.core.emergencyengine

/** Thrown by EmergencyStateMachine when asked to perform a transition or subsystem update that
 *  section 25's state machine does not permit from the current state. */
class IllegalEmergencyTransitionException(
    val from: EmergencyState,
    val to: EmergencyState,
) : IllegalStateException("Illegal emergency state transition: $from -> $to")

/** Thrown when a subsystem update is attempted outside the window in which that subsystem is
 *  allowed to report state (see EmergencyStateMachine's class doc for the exact window). */
class SubsystemUpdateNotAllowedException(
    val currentState: EmergencyState,
) : IllegalStateException(
    "Subsystem updates are not allowed while the emergency is in $currentState",
)
