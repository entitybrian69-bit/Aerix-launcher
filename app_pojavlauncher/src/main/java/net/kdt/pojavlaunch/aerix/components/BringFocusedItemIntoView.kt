package net.kdt.pojavlaunch.aerix.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/** Keep a focused control visible when it lives inside a scrollable launcher menu. */
@Composable
fun Modifier.bringFocusedItemIntoView(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(requester, isFocused) {
        if (isFocused) requester.bringIntoView()
    }

    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { isFocused = it.isFocused }
}
