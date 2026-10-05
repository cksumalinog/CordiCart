package com.cordicart.cordicart

/**
 * Gmail account used to send OTP emails.
 *
 * Leave both values blank to run in DEMO MODE: the code is shown in a dialog
 * instead of being emailed.
 *
 * To send real emails: create a dedicated Gmail account, turn on 2-Step Verification,
 * create an App Password, and paste both below (app password without spaces).
 * Never upload real values to a public GitHub repository.
 */
object MailConfig {
    const val SMTP_EMAIL = ""
    const val SMTP_APP_PASSWORD = ""
}
