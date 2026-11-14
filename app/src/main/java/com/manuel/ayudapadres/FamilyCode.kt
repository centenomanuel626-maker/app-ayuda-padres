package com.manuel.ayudapadres

/** El código de familia es la llave de los datos: tiene que ser largo y difícil de adivinar. */
object FamilyCode {
    /** Al menos 12 letras o números y variados (no vale 000000000000 ni 123412341234). */
    fun isStrong(code: String): Boolean {
        val a = code.filter { it.isLetterOrDigit() }
        return a.length >= 12 && a.toSet().size >= 6
    }

    fun generate(): String = SetupActivity.newCode()

    const val WEAK_MSG = "Ese código es muy fácil de adivinar. Usa uno de al menos 12 letras y números variados, como el que genera el panel."
}
