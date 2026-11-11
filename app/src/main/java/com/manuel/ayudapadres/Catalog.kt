package com.manuel.ayudapadres

data class Btn(val id: String, val emoji: String, val title: String, val sub: String)
data class Cat(val id: String, val name: String, val emoji: String, val color: Int, val items: List<String>)

/** Las 4 categorías y sus botones (los 25 del documento más las funciones adicionales). */
object Catalog {
    const val ESTOY_BIEN_TEXT = "Tocó “Estoy bien”."

    val cats = listOf(
        Cat("internet", "Internet", "📶", Col.AZUL, listOf("wifi", "nointernet", "bluetooth", "datos")),
        Cat("sonido", "Sonido y pantalla", "🔊", Col.MORADO,
            listOf("ringer", "volumen", "letra", "notif", "brillo", "apagado", "girar")),
        Cat("ayuda", "Ayuda", "🤝", Col.VERDE_AZUL,
            listOf("llamar", "familia", "video", "remoto", "olvide", "estoybien", "emergencia", "aprender")),
        Cat("celular", "Mi celular", "📱", Col.NARANJA,
            listOf("linterna", "lupa", "medicinas", "bateria", "clave", "espacio", "updates", "lento", "quitar", "seguridad"))
    )

    fun cat(id: String): Cat = cats.firstOrNull { it.id == id } ?: cats[0]

    fun catOf(btnId: String): String = cats.firstOrNull { btnId in it.items }?.id ?: "ayuda"

    fun btn(id: String, helper: String): Btn = when (id) {
        "wifi" -> Btn(id, "📶", "Wifi", "Conectarme al wifi de la casa")
        "nointernet" -> Btn(id, "🌐", "No tengo internet", "Reviso qué pasa y te digo")
        "bluetooth" -> Btn(id, "🎧", "Bluetooth", "Audífonos o el carro")
        "datos" -> Btn(id, "📡", "Datos móviles", "Internet fuera de casa")
        "ringer" -> Btn(id, "🔔", "Sonido, vibración o silencio", "Cambia con un toque")
        "volumen" -> Btn(id, "🔊", "Subir volumen", "Para oír las llamadas")
        "letra" -> Btn(id, "🔠", "Letra más grande", "Leer mejor la pantalla")
        "notif" -> Btn(id, "🔕", "Notificaciones molestas", "Callar las apps que avisan mucho")
        "brillo" -> Btn(id, "☀️", "Brillo de pantalla", "Más claro o más oscuro")
        "apagado" -> Btn(id, "⏱️", "La pantalla se apaga rápido", "Que espere más tiempo")
        "girar" -> Btn(id, "🔄", "Girar la pantalla", "Que no se voltee sola")
        "llamar" -> Btn(id, "📞", "Llamar a $helper", "Te llamo ahora mismo")
        "familia" -> Btn(id, "👨‍👩‍👧", "Mi familia", "Fotos grandes; tocas y llama")
        "video" -> Btn(id, "🎥", "Videollamada a la familia", "Abre WhatsApp con $helper")
        "remoto" -> Btn(id, "🖥️", "Ayuda a distancia", "$helper entra a tu celular")
        "olvide" -> Btn(id, "🔑", "Olvidé una contraseña", "$helper te ayuda")
        "estoybien" -> Btn(id, "✅", "Estoy bien", "Avisa a $helper que todo está en orden")
        "emergencia" -> Btn(id, "🆘", "Emergencia", "Envía tu ubicación a $helper")
        "aprender" -> Btn(id, "🎓", "Aprender a usar la app", "Tutorial y modo práctica")
        "linterna" -> Btn(id, "🔦", "Linterna", "Prender o apagar")
        "lupa" -> Btn(id, "🔍", "Lupa", "Agranda letras pequeñas con la cámara")
        "medicinas" -> Btn(id, "💊", "Mis medicinas", "A qué hora me toca cada una")
        "bateria" -> Btn(id, "🔋", "Batería", "Cuánta queda y cómo ahorrar")
        "clave" -> Btn(id, "🔒", "Contraseña de bloqueo", "Poner o cambiar la clave")
        "espacio" -> Btn(id, "🧹", "Liberar espacio", "Cuando dice “almacenamiento lleno”")
        "updates" -> Btn(id, "⬇️", "Actualizaciones", "Tener el celular al día")
        "lento" -> Btn(id, "🐢", "Mi celular está lento o trabado", "Cómo reiniciarlo")
        "quitar" -> Btn(id, "🗑️", "Quitar apps que no uso", "Juegos y apps olvidadas")
        "seguridad" -> Btn(id, "🛡️", "Revisión de seguridad", "¿Alguien espía mi celular?")
        else -> Btn(id, "❔", id, "")
    }
}
