// The Discord Android app's Rich Presence service, as exposed by its Social SDK.
// Reverse-engineered by Nightdavisao (antisocialcord-kt, MIT licence); see
// aerix/discord/AerixDiscordPresence.kt for how it is used.
package com.discord.socialsdk.rpc;

import com.discord.socialsdk.rpc.IDiscordRpcCallback;
import com.discord.socialsdk.rpc.IDiscordRpcConnection;

interface IDiscordRpcService {
    IDiscordRpcConnection connect(long applicationId, String version, IDiscordRpcCallback callback);
}
