package dev.gdk.bumblebee.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

private const val SLIDE_MS = 280
private val SlideSpec = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)
private val FadeSpec = tween<Float>(160)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pillarEnter(): EnterTransition {
    if (targetState.destination.route == "fuel") {
        return slideInVertically(SlideSpec) { it } + fadeIn(FadeSpec)
    }
    val dir = slideDir(initialState.destination.route, targetState.destination.route)
    return slideInHorizontally(SlideSpec) { dir * it / 3 } + fadeIn(FadeSpec)
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pillarExit(): ExitTransition {
    if (targetState.destination.route == "fuel" || initialState.destination.route == "fuel") {
        return fadeOut(FadeSpec)
    }
    val dir = slideDir(initialState.destination.route, targetState.destination.route)
    return slideOutHorizontally(SlideSpec) { -dir * it / 3 } + fadeOut(FadeSpec)
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pillarPopEnter(): EnterTransition {
    if (initialState.destination.route == "fuel") {
        return fadeIn(FadeSpec)
    }
    val dir = slideDir(initialState.destination.route, targetState.destination.route)
    return slideInHorizontally(SlideSpec) { dir * it / 3 } + fadeIn(FadeSpec)
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pillarPopExit(): ExitTransition {
    if (initialState.destination.route == "fuel") {
        return slideOutVertically(SlideSpec) { it } + fadeOut(FadeSpec)
    }
    val dir = slideDir(initialState.destination.route, targetState.destination.route)
    return slideOutHorizontally(SlideSpec) { -dir * it / 3 } + fadeOut(FadeSpec)
}

internal fun tabIndex(route: String?): Int {
    val core = if (route == "fuel") Pillar.GARAGE.route else route
    val i = CockpitCoreOrder.indexOfFirst { it.route == core }
    return if (i >= 0) i else CockpitCoreOrder.indexOf(Pillar.COCKPIT)
}

private fun slideDir(from: String?, to: String?): Int {
    val a = tabIndex(from)
    val b = tabIndex(to)
    return if (b >= a) 1 else -1
}
