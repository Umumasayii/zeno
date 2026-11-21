package com.zeno.domain.security

/**
 * Platform grants/roles an action may need (docs/06_SECURITY_PERMISSIONS.md, permissions matrix).
 * The domain only names them; Android adapters report whether each is currently granted.
 */
enum class Capability {
    MICROPHONE,
    ASSISTANT_ROLE,
    CONTACTS,
    SMS_SEND,
    SMS_READ,
    CALL_PHONE,
    NOTIFICATION_LISTENER,
    USAGE_ACCESS,
    ACCESSIBILITY,
    DND_ACCESS,
    EXACT_ALARM,
    WRITE_SETTINGS,
    POST_NOTIFICATIONS,
    ROOT,
}

/** Risk classes R0–R4 from docs/06_SECURITY_PERMISSIONS.md. */
enum class RiskLevel {
    /** Informational: time, help, open public app. */
    R0,
    /** Reversible control: flashlight, volume, home, scroll. */
    R1,
    /** External/private effects: send SMS, call, read private content, add routine. */
    R2,
    /** Destructive/privileged: shutdown, reboot, delete routines, security changes. */
    R3,
    /** Disallowed in v1: secure Keyguard bypass, arbitrary root shell, financial transfers, OTP entry. */
    R4,
}
