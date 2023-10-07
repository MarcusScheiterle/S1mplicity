package simpl1f1ed.bot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.RoleAction;

public class Callables {

    public static void levelRoleChange(Member member, Guild guild, int currentLevel) {
        if (member == null || guild == null) {
            return;
        }

        // Collect roles to remove and roles to keep
        Map<Integer, String> levelNames = Levels.getLevelNamesMap();
        List<Role> rolesToKeep = member.getRoles().stream()
                .filter(role -> !role.getName().startsWith("Level") && !levelNames.values().contains(role.getName()))
                .collect(Collectors.toList());

        if (rolesToKeep == null) {
            return;
        }

        // Modify roles in one operation
        CompletableFuture<Void> allRolesModified = new CompletableFuture<>();
        guild.modifyMemberRoles(member, rolesToKeep).queue(success -> allRolesModified.complete(null),
                failure -> allRolesModified.completeExceptionally(new Exception("Failed to modify roles")));

        // Once all roles are removed
        allRolesModified.thenRun(() -> {
            if (currentLevel > 0) {
                Role levelRole = guild.getRolesByName("Level " + currentLevel, true).stream().findFirst().orElse(null);
                if (levelRole != null) {
                    guild.addRoleToMember(member, levelRole).queue();
                }
            }

            String generalRoleName = Levels.getLevelName(currentLevel);

            if (generalRoleName == null) {
                return;
            }

            Role generalRole = guild.getRolesByName(generalRoleName, true).stream().findFirst().orElse(null);
            if (generalRole != null) {
                guild.addRoleToMember(member, generalRole).queue();
            }
        });
    }

    public static void prestige(Member member, Guild guild, Channel channel) {
        DatabaseManager.updatePointsAndLevel(member, guild, channel, 0);
        DatabaseManager.incrementPrestige(member, guild, channel);
    }

    public static void createLevelRoles(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            System.out.println("Failed to create roles: Guild is null.");
            return;
        }

        Map<Integer, String> levelNames = Levels.getLevelNamesMap();
        for (Map.Entry<Integer, String> levelName : levelNames.entrySet()) {
            createRole(guild, levelName.getValue(), true);
        }

        for (int level = 1; level <= 100; level++) {
            createRole(guild, "Level " + level, false);
        }
    }

    private static void createRole(Guild guild, String roleName, boolean hoisted) {
        RoleAction roleAction = guild.createRole().setName(roleName).setHoisted(hoisted);
        roleAction.queue(
                role -> System.out.println("Created role: " + roleName),
                failure -> System.out.println("Failed to create role: " + roleName));
    }

    public static void removeLevelRoles(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();

        if (guild == null) {
            System.out.println("Cannot get guild");
            return;
        }

        Map<Integer, String> levelNames = Levels.getLevelNamesMap(); // Assuming Levels.getLevelNamesMap() returns the

        for (Role role : guild.getRoles()) {
            if (role.getName().startsWith("Level ") || levelNames.containsValue(role.getName())) {
                role.delete().queue(
                        success -> System.out.println("Removed role: " + role.getName()),
                        failure -> System.out.println("Failed to remove role: " + role.getName()));
            }
        }
    }

    public static MessageEmbed buildLevelRequirementsMessage(Map<Integer, Integer> levelRequirements, String section,
            Member member) {
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("Level Requirements")
                .setFooter("S1mplicity", member.getJDA().getSelfUser().getEffectiveAvatarUrl())
                .setDescription("Points required to reach each level")
                .setColor(0x42F56C);

        int startLevel;
        int endLevel;
        String fieldTitle1;
        String fieldTitle2;

        if (section.equals("firstHalf")) {
            startLevel = 1;
            endLevel = 50;
            fieldTitle1 = "Levels " + startLevel + "-" + (startLevel + (endLevel - startLevel) / 2);
            fieldTitle2 = "Levels " + (startLevel + (endLevel - startLevel) / 2 + 1) + "-" + endLevel;
        } else if (section.equals("secondHalf")) {
            startLevel = 51;
            endLevel = 100;
            fieldTitle1 = "Levels " + startLevel + "-" + (startLevel + (endLevel - startLevel) / 2);
            fieldTitle2 = "Levels " + (startLevel + (endLevel - startLevel) / 2 + 1) + "-" + endLevel;
        } else {
            // Invalid section, return an empty embed
            embedBuilder.setDescription("Error: an error occurred, try again...");
            return embedBuilder.build();
        }

        StringBuilder fieldContent1 = new StringBuilder();
        StringBuilder fieldContent2 = new StringBuilder();

        int currentIndex = 1;

        for (Map.Entry<Integer, Integer> entry : levelRequirements.entrySet()) {
            int level = entry.getKey();
            int pointsRequired = entry.getValue();

            if (level >= startLevel && level <= endLevel) {
                StringBuilder targetFieldContent = (currentIndex <= (endLevel - startLevel + 1) / 2) ? fieldContent1
                        : fieldContent2;
                targetFieldContent.append("Level ").append(level).append(": ").append(pointsRequired).append("\n");
                currentIndex++;
            }
        }

        embedBuilder.addField(fieldTitle1, "" + fieldContent1.toString(), true);
        embedBuilder.addField(fieldTitle2, "" + fieldContent2.toString(), true);
        embedBuilder.setColor(0x42F56C);
        return embedBuilder.build();
    }

    public static int calculateTimeDifferenceInMinutes(String startTime, String endTime) {
        int startMinutes = convertToMinutesSinceMidnight(startTime);
        int endMinutes = convertToMinutesSinceMidnight(endTime);

        // If the end time is smaller than the start time, it means it's on the next day.
        if (endMinutes < startMinutes) {
            endMinutes += 24 * 60;
        }

        return endMinutes - startMinutes;
    }

    public static int convertToMinutesSinceMidnight(String time) {
        String[] parts = time.split(" ");
        String[] hhmm = parts[0].split(":");
        
        int hours = Integer.parseInt(hhmm[0]);
        int minutes = Integer.parseInt(hhmm[1]);
        
        // Convert 12-hour time format to 24-hour time format
        if (parts[1].equalsIgnoreCase("pm")) {
            if (hours != 12) { // if it's 12 PM, then it's noon, no need to add 12
                hours += 12;
            }
        } else { // am
            if (hours == 12) { // if it's 12 AM, then it's midnight, make it 0
                hours = 0;
            }
        }
        
        return hours * 60 + minutes;
    }
}
