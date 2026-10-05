package com.cordicart.cordicart

import android.os.Handler
import android.os.Looper
import java.util.Properties
import java.util.concurrent.Executors
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Sends the OTP email through Gmail SMTP.
 * Android forbids network work on the main (UI) thread, so sending happens on a
 * background thread and the result is posted back to the main thread.
 */
object MailSender {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    val isConfigured: Boolean
        get() = MailConfig.SMTP_EMAIL.isNotBlank() && MailConfig.SMTP_APP_PASSWORD.isNotBlank()

    fun sendOtpEmail(
        recipient: String,
        code: String,
        onSent: () -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            try {
                val props = Properties().apply {
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.host", "smtp.gmail.com")
                    put("mail.smtp.port", "587")
                    put("mail.smtp.connectiontimeout", "10000")
                    put("mail.smtp.timeout", "10000")
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication =
                        PasswordAuthentication(MailConfig.SMTP_EMAIL, MailConfig.SMTP_APP_PASSWORD)
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(MailConfig.SMTP_EMAIL, "CordiCart"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
                    subject = "Your CordiCart verification code"
                    setText(
                        "Hi!\n\nYour CordiCart verification code is: $code\n\n" +
                        "This code expires in 5 minutes. If you did not request it, you can ignore this email."
                    )
                }

                Transport.send(message)
                mainHandler.post { onSent() }
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                mainHandler.post { onError(reason) }
            }
        }
    }
}
