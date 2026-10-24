package dev.gdk.bumblebee.protocol

import java.util.Locale

/** Formats numbers for the bike link. Never use the phone locale here. */
fun rootFormat(pattern: String, vararg args: Any?): String =
    String.format(Locale.ROOT, pattern, *args)
