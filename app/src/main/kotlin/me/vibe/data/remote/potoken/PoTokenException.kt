/*
 * Ported from NewPipe (org.schabi.newpipe.util.potoken), GPL-3.0-or-later.
 * Copyright (C) Team NewPipe. See LICENSE.
 */
package me.vibe.data.remote.potoken

open class PoTokenException(message: String) : Exception(message)

/**
 * The system WebView cannot run the BotGuard code — usually because it is an ancient or stripped
 * implementation. Distinct from [PoTokenException] because it is permanent for this device: there
 * is no point retrying, and the provider disables itself instead.
 */
class BadWebViewException(message: String) : PoTokenException(message)
