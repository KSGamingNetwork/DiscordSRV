package github.scarsz.discordsrv.d_commands;

/*-
 * LICENSE
 * DiscordSRV
 * -------------
 * Copyright (C) 2016 - 2022 Austin "Scarsz" Shapiro
 * -------------
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * END
 */

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.commands.PluginSlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommandProvider;
import github.scarsz.discordsrv.api.events.DiscordChatChannelListCommandMessageEvent;
import github.scarsz.discordsrv.hooks.VaultHook;
import github.scarsz.discordsrv.hooks.world.MultiverseCoreHook;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.PlayerUtil;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public class CommandPlayerList implements SlashCommandProvider {

    @Getter final static String name = "playerlist";
    @Getter final static String description = "Get the list of players.";
    @Getter final static CommandData data = new CommandData(name, description);

    @SlashCommand(path = name)
    boolean execute(SlashCommandEvent event){
        if (!DiscordSRV.config().getBoolean("DiscordChatChannelListCommandEnabled")) return false;

        String playerListMessage = getPlayerListMessage();


        //TODO: Implement event for this
        event.reply(playerListMessage).setEphemeral(true);
        return true;
    }

    public static String getPlayerListMessage() {
        String playerListMessage;
        if (PlayerUtil.getOnlinePlayers(true).size() == 0) {
            playerListMessage = PlaceholderUtil.replacePlaceholdersToDiscord(LangUtil.Message.PLAYER_LIST_COMMAND_NO_PLAYERS.toString());
        } else {
            playerListMessage = LangUtil.Message.PLAYER_LIST_COMMAND.toString().replace("%playercount%", PlayerUtil.getOnlinePlayers(true).size() + "/" + Bukkit.getMaxPlayers());
            playerListMessage = PlaceholderUtil.replacePlaceholdersToDiscord(playerListMessage);
            playerListMessage += "\n```\n";

            StringJoiner players = new StringJoiner(LangUtil.Message.PLAYER_LIST_COMMAND_ALL_PLAYERS_SEPARATOR.toString());

            List<String> playerList = new LinkedList<>();
            for (Player player : PlayerUtil.getOnlinePlayers(true)) {
                String userPrimaryGroup = VaultHook.getPrimaryGroup(player);
                boolean hasGoodGroup = StringUtils.isNotBlank(userPrimaryGroup);
                // capitalize the first letter of the user's primary group to look neater
                if (hasGoodGroup) userPrimaryGroup = userPrimaryGroup.substring(0, 1).toUpperCase() + userPrimaryGroup.substring(1);

                String playerFormat = LangUtil.Message.PLAYER_LIST_COMMAND_PLAYER.toString()
                        .replace("%username%", player.getName())
                        .replace("%displayname%", MessageUtil.strip(player.getDisplayName()))
                        .replace("%primarygroup%", userPrimaryGroup)
                        .replace("%world%", player.getWorld().getName())
                        .replace("%worldalias%", MessageUtil.strip(MultiverseCoreHook.getWorldAlias(player.getWorld().getName())));

                // use PlaceholderAPI if available
                playerFormat = PlaceholderUtil.replacePlaceholdersToDiscord(playerFormat, player);
                playerList.add(playerFormat);
            }

            playerList.sort(Comparator.naturalOrder());
            for (String playerFormat : playerList) {
                players.add(playerFormat);
            }
            playerListMessage += players.toString();

            if (playerListMessage.length() > 1996) playerListMessage = playerListMessage.substring(0, 1993) + "...";
            playerListMessage += "\n```";
        }
        return playerListMessage;
    }

    public static boolean process(GuildMessageReceivedEvent event, String message) {
        if (!DiscordSRV.config().getBoolean("DiscordChatChannelListCommandEnabled")) return false;
        if (!StringUtils.trimToEmpty(message).equalsIgnoreCase(DiscordSRV.config().getString("DiscordChatChannelListCommandMessage"))) return false;

        int expiration = DiscordSRV.config().getInt("DiscordChatChannelListCommandExpiration") * 1000;

        String playerListMessage = getPlayerListMessage();

        DiscordChatChannelListCommandMessageEvent listCommandMessageEvent = DiscordSRV.api.callEvent(
                new DiscordChatChannelListCommandMessageEvent(event.getChannel(), event.getGuild(), message, event, playerListMessage, expiration, DiscordChatChannelListCommandMessageEvent.Result.SEND_RESPONSE));
        switch (listCommandMessageEvent.getResult()) {
            case SEND_RESPONSE:
                DiscordUtil.sendMessage(event.getChannel(), listCommandMessageEvent.getPlayerListMessage(), listCommandMessageEvent.getExpiration());

                // expire message after specified time
                if (listCommandMessageEvent.getExpiration() > 0 && DiscordSRV.config().getBoolean("DiscordChatChannelListCommandExpirationDeleteRequest")) {
                    event.getMessage().delete().queueAfter(listCommandMessageEvent.getExpiration(), TimeUnit.MILLISECONDS);
                }
                return true;
            case NO_ACTION:
                return true;
            case TREAT_AS_REGULAR_MESSAGE:
                return false;
        }
        return true;
    }

    @Override
    public Set<PluginSlashCommand> getSlashCommands() {
        HashSet<PluginSlashCommand> set = new HashSet<PluginSlashCommand>();
        PluginSlashCommand playerlist = new PluginSlashCommand(DiscordSRV.getPlugin(), CommandPlayerList.data);
        set.add(playerlist);
        return set;
    }

}
