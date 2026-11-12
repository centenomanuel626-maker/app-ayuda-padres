package com.manuel.ayudapadres

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Lee en voz alta los mensajes de la app. */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val es = Locale("es")
                val r = tts?.setLanguage(es)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                tts?.setSpeechRate(0.9f)
                pending?.let { say(context, it) }
                pending = null
            }
        }
    }

    fun say(context: Context, message: String) {
        if (!Prefs(context).voice) return
        if (!ready) {
            pending = message
            init(context)
            return
        }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "ayuda")
    }

    fun isReady(): Boolean = ready

    fun stop() {
        tts?.stop()
    }
}
