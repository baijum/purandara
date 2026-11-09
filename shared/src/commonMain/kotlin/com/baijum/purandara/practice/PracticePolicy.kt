package com.baijum.purandara.practice

/** Normal accompaniment remains the default; Listen–repeat never scores the model. */
enum class PracticeMode { NORMAL, LISTEN_REPEAT }

enum class RecoveryMode { CONTINUOUS, TIMED, WHEN_READY }

/** Independent of the selected whole piece, section or teacher-reviewed phrase. */
data class PracticePolicy(
    val mode: PracticeMode = PracticeMode.NORMAL,
    val recoveryMode: RecoveryMode = RecoveryMode.CONTINUOUS,
    val recoverySeconds: Int = 5,
    val repeat: Boolean = false,
    val feedback: Boolean = false,
    val responseGuide: Boolean = false,
) {
    init {
        require(recoverySeconds in 1..30) { "recovery must be 1–30 seconds" }
    }

    /** Ordinary playback/repeat keeps the existing seamless renderer. */
    val needsTransport: Boolean
        get() = mode == PracticeMode.LISTEN_REPEAT || repeat && recoveryMode != RecoveryMode.CONTINUOUS

    companion object {
        /** Explicit arguments for Swift; no Kotlin default arguments are required. */
        fun create(
            mode: PracticeMode,
            recoveryMode: RecoveryMode,
            recoverySeconds: Int,
            repeat: Boolean,
            feedback: Boolean,
            responseGuide: Boolean,
        ): PracticePolicy = PracticePolicy(mode, recoveryMode, recoverySeconds, repeat, feedback, responseGuide)
    }
}
