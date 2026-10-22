/*
 * Aerix Launcher Social layer.
 *
 * Bridges the chat to the launcher's real instance list: you can share one of your own instances
 * and, on the other end, select an instance that exists on this device.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.media

import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.social.data.AttachmentKind
import net.kdt.pojavlaunch.aerix.social.data.ChatAttachment

/** One of the player's real launcher instances, ready to be put in a message. */
data class ShareableInstance(val id: String, val name: String, val version: String)

object SocialInstances {

    /** The instances that currently exist in the launcher. */
    fun local(): List<ShareableInstance> = runCatching {
        PojavInstanceBridge.instances.map {
            ShareableInstance(id = it.id, name = it.name, version = it.versionId)
        }
    }.getOrDefault(emptyList())

    /** Builds the attachment for a real instance instead of a hardcoded demo one. */
    fun attachment(instance: ShareableInstance): ChatAttachment = ChatAttachment(
        kind = AttachmentKind.INSTANCE,
        title = instance.name,
        subtitle = instance.version.ifBlank { "Local instance" },
        payload = instance.id,
        sizeLabel = ""
    )

    /**
     * Selects a shared instance that is already installed. Returns false when it is not on this
     * device yet, which the caller surfaces instead of pretending the download happened.
     */
    fun select(instanceId: String): Boolean = runCatching {
        val target = PojavInstanceBridge.instances.firstOrNull { it.id == instanceId }
            ?: PojavInstanceBridge.instances.firstOrNull { it.name.equals(instanceId, true) }
            ?: return false
        PojavInstanceBridge.select(target)
        true
    }.getOrDefault(false)
}
