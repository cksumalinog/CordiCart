package com.cordicart.cordicart

import java.util.Locale

object EmailValidator {

    // Only UC student addresses, e.g. css9223@students.uc-bcf.edu.ph
    private val STUDENT_EMAIL = Regex("^[a-z0-9._%+-]+@students\\.uc-bcf\\.edu\\.ph$")

    /** Trims spaces and lowercases, so " CSS9223@Students.UC-BCF.edu.ph" is treated the same. */
    fun normalize(raw: String?): String = raw?.trim()?.lowercase(Locale.ROOT) ?: ""

    /**
     * Unlike a plain endsWith() check, this rejects "@students.uc-bcf.edu.ph"
     * (nothing before the @) and "a@b@students.uc-bcf.edu.ph" (two @ signs).
     */
    fun isValidStudent(email: String?): Boolean = STUDENT_EMAIL.matches(normalize(email))
}
