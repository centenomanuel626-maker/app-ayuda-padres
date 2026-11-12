package com.manuel.ayudapadres

// Tutoriales guiados con brillo sobre el botón que hay que tocar (ver Coach.kt).

/** Guías disponibles en "Aprender": id y título. */
fun guides(helper: String): List<Pair<String, String>> = listOf(
    "wifi" to "Conectarme al wifi",
    "nointernet" to "No tengo internet",
    "ringer" to "Poner el celular a sonar o vibrar",
    "volumen" to "Subir el volumen",
    "letra" to "Agrandar la letra",
    "llamar" to "Llamar a $helper",
    "pantalla" to "Mostrarle mi pantalla a $helper",
    "estoybien" to "Avisar que estoy bien",
    "linterna" to "Prender la linterna",
    "lupa" to "Usar la lupa",
    "medicinas" to "Ver mis medicinas",
    "emergencia" to "Qué hacer en una emergencia"
)

/** Recorrido completo por la pantalla principal (se muestra solo la primera vez). */
fun MainActivity.startTour() {
    if (coach != null) return
    val h = prefs.helperName
    val home: () -> Unit = { if (screen != "home") showHome() }
    val steps = listOf(
        Coach.Step(null, "Hola, ${prefs.parentName}. Te voy a mostrar cómo funciona esta app. Lo que brilla es lo que te explico.", before = home),
        Coach.Step("estoybien", "Cada día toca aquí para avisarle a $h que estás bien."),
        Coach.Step("cat:internet", "Aquí arreglas el internet: el wifi, los datos y el Bluetooth."),
        Coach.Step("cat:sonido", "Aquí cambias el sonido, la vibración, el brillo y el tamaño de la letra."),
        Coach.Step("cat:ayuda", "Aquí llamas a $h o a tu familia, o le muestras tu pantalla."),
        Coach.Step("cat:celular", "Aquí están la linterna, la lupa, tus medicinas y la revisión de seguridad."),
        Coach.Step("mic", "Toca el micrófono y dile a la app lo que necesitas. Por ejemplo: no tengo internet."),
        Coach.Step("dock:call", "Este botón verde llama a $h. Siempre está aquí abajo."),
        Coach.Step("dock:sos", "Este botón rojo es para emergencias. Le manda a $h dónde estás y lo llama."),
        Coach.Step(null, "Nunca instales apps que te manden por mensaje, aunque digan que son de $h. Y no te preocupes: con esta app no puedes dañar nada."),
        Coach.Step(null, "Si quieres repasar, entra a Ayuda y toca “Aprender a usar la app”. Ahí hay guías para cada cosa.")
    )
    coach = Coach(this, steps) {
        prefs.tutorialDone = true
    }
    coach?.start()
}

/** Guía para una tarea: señala la categoría y luego el botón, y la persona los toca de verdad. */
fun MainActivity.startGuide(id: String) {
    if (coach != null) return
    val h = prefs.helperName
    val home: () -> Unit = { showHome() }
    fun cat(c: String, text: String) = Coach.Step("cat:$c", text, tap = true, before = home)
    fun btn(b: String, text: String) = Coach.Step("btn:$b", text, tap = true)
    val steps: List<Coach.Step> = when (id) {
        "wifi" -> listOf(
            cat("internet", "Primero toca Internet."),
            btn("wifi", "Ahora toca Wifi. Se abre la lista: toca el nombre del wifi de tu casa.")
        )
        "nointernet" -> listOf(
            cat("internet", "Primero toca Internet."),
            btn("nointernet", "Toca “No tengo internet”. La app revisa todo y te dice qué hacer.")
        )
        "ringer" -> listOf(
            cat("sonido", "Primero toca Sonido y pantalla."),
            btn("ringer", "Toca aquí. Cada toque cambia: suena, vibra o silencio. A la derecha ves cómo quedó.")
        )
        "volumen" -> listOf(
            cat("sonido", "Primero toca Sonido y pantalla."),
            btn("volumen", "Toca “Subir volumen”. Cada toque lo sube un poco más.")
        )
        "letra" -> listOf(
            cat("sonido", "Primero toca Sonido y pantalla."),
            btn("letra", "Toca “Letra más grande” y elige el tamaño.")
        )
        "llamar" -> listOf(
            Coach.Step("dock:call", "Para llamar a $h toca el botón verde de abajo. Está en todas las pantallas.", tap = true, before = home)
        )
        "pantalla" -> listOf(
            cat("ayuda", "Primero toca Ayuda."),
            btn("pantalla", "Toca “Mostrarle mi pantalla a $h”. Cuando conteste, toca la flecha de compartir y elige Pantalla completa.")
        )
        "estoybien" -> listOf(
            Coach.Step("estoybien", "Toca esta tarjeta verde una vez al día. $h recibe el aviso de que estás bien.", tap = true, before = home)
        )
        "linterna" -> listOf(
            cat("celular", "Primero toca Mi celular."),
            btn("linterna", "Toca Linterna. Para apagarla, tócala otra vez.")
        )
        "lupa" -> listOf(
            cat("celular", "Primero toca Mi celular."),
            btn("lupa", "Toca Lupa. Acerca la cámara a lo que quieres leer y usa Más o Menos.")
        )
        "medicinas" -> listOf(
            cat("celular", "Primero toca Mi celular."),
            btn("medicinas", "Toca Mis medicinas para ver a qué hora te toca cada una.")
        )
        // La emergencia se explica sin tocarla de verdad.
        "emergencia" -> listOf(
            Coach.Step("dock:sos", "Si te sientes mal o tienes un problema, toca este botón rojo.", before = home),
            Coach.Step("dock:sos", "La app te pregunta si necesitas ayuda. Tocas “Sí” y le manda a $h dónde estás y lo llama por WhatsApp."),
            Coach.Step(null, "Si fue sin querer, toca “No, era un error” y no pasa nada.")
        )
        else -> emptyList()
    }
    if (steps.isEmpty()) return
    coach = Coach(this, steps) { }
    coach?.start()
}
