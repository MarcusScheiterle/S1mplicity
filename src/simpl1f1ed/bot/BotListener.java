package simpl1f1ed.bot;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;

public class BotListener extends ListenerAdapter {
    private DatabaseManager databaseManager;

    private Map<String, Instant> voiceJoinTimeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> memberTimers = new ConcurrentHashMap<>();

    public BotListener(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void onGuildVoiceUpdate(@Nonnull GuildVoiceUpdateEvent event) {
        Member member = event.getEntity();
        AudioChannel oldValue = event.getOldValue();
        AudioChannel newValue = event.getNewValue();

        if (oldValue != null) {
            // Cancel the TimerTask for the member who left
            Timer timer = memberTimers.remove(member.getId());
            if (timer != null) {
                timer.cancel();
            }
            long secondsSpent = updatePoints(event, oldValue);
            System.out.println(member.getUser().getName() + " left voice channel " + oldValue.getName()
                    + " | Time Spent: " + secondsSpent + " seconds | Points Awarded: " + calculatePoints(secondsSpent));
        }

        if (newValue != null) {
            voiceJoinTimeMap.put(member.getId(), Instant.now());

            Timer timer = new Timer(true);
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    long secondsSpent = updatePoints(event, newValue);
                    System.out.println(member.getUser().getName() + " is in voice channel " + newValue.getName()
                            + " | Time Spent: " + secondsSpent + " seconds | Points Awarded: "
                            + calculatePoints(secondsSpent));
                }
            };
            timer.schedule(task, 0, 300000);
            memberTimers.put(member.getId(), timer);
        }
    }

    private long updatePoints(GuildVoiceUpdateEvent event, AudioChannel channel) {
        Member member = event.getMember();

        Instant joinTime = voiceJoinTimeMap.get(member.getId());
        if (joinTime == null)
            return 0;

        long secondsSpent = Duration.between(joinTime, Instant.now()).getSeconds();
        voiceJoinTimeMap.put(member.getId(), Instant.now());

        int points = calculatePoints(secondsSpent);
        // Assume the databaseManager.incrementUserPoints method takes `points` as an
        // argument
        DatabaseManager.incrementUserPoints(member, event.getGuild(), null, points, 0);
        return secondsSpent;
    }

    private int calculatePoints(long secondsSpent) {
        return (int) (secondsSpent / 60) / 5;
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!event.getAuthor().isBot()) {
            DatabaseManager.incrementUserPoints(event.getMember(), event.getGuild(), event.getChannel(), 1, 1);
            databaseManager.lastMessageUpdate(event.getAuthor().getId());
        }
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {

        Member member = event.getMember();

        if (event.getComponentId().equals("prestige_button")) {

            if (member == null) {
                return;
            }

            if (DatabaseManager.getLevel(member.getId()) >= 100) {
                Callables.prestige(member, event.getGuild(), event.getChannel());
            } else {
                event.reply("You are not able to prestige...").setEphemeral(true).queue();
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        String interactionCommand = event.getName();
        Member member = event.getMember();

        if (member == null) {
            return;
        }

        String userId = member.getId();

        event.deferReply(true).queue();

        try {

            if (interactionCommand.equalsIgnoreCase("getlevels")) {
                // Retrieve the level requirements hash
                Map<Integer, Integer> levelRequirements = Levels.calculateLevelPointsMap();
                OptionMapping option = event.getOption("section");

                if (option == null) {
                    return;
                }

                MessageEmbed message = Callables.buildLevelRequirementsMessage(levelRequirements,
                        option.getAsString(), event.getMember());
                // Send the message back to the user
                if (message != null) {
                    event.getHook().sendMessageEmbeds(message).setEphemeral(true).queue();
                } else {
                    event.getHook().sendMessage("Could not get levels, report to Simpl1f1ed").setEphemeral(true)
                            .queue();
                }
            }
            // ADMIN COMMANDS
            if (databaseManager.isAdmin(userId)) {

                if (interactionCommand.equalsIgnoreCase("updatelevel")) {

                    OptionMapping memberOption = event.getOption("member");

                    if (memberOption == null) {
                        return;
                    }

                    Member mentionedMember = memberOption.getAsMember();

                    Callables.handleLevelUp(mentionedMember, event.getGuild(), event.getChannel());
                    event.getHook().sendMessage("Test command executed").setEphemeral(true)
                            .queue();
                }

                if (interactionCommand.equalsIgnoreCase("retropointassignment")) {

                    OptionMapping memberOption = event.getOption("member");
                    OptionMapping startTime = event.getOption("start_time");
                    OptionMapping endTime = event.getOption("end_time");

                    if (startTime == null || endTime == null || memberOption == null) {
                        return;
                    }

                    String start = startTime.getAsString();
                    String end = endTime.getAsString();

                    int points = Callables.calculateTimeDifferenceInMinutes(start, end);
                    Member mentionedMember = memberOption.getAsMember();

                    DatabaseManager.incrementUserPoints(mentionedMember, event.getGuild(), event.getChannel(), points,
                            0);
                    event.getHook().sendMessage("Points assigned").setEphemeral(true)
                            .queue();
                }

                if (interactionCommand.equalsIgnoreCase("setpoints")) {

                    OptionMapping pointsOption = event.getOption("points");
                    OptionMapping memberOption = event.getOption("member");

                    if (pointsOption == null || memberOption == null) {
                        return;
                    }

                    Integer points = pointsOption.getAsInt();
                    Member mentionedMember = memberOption.getAsMember();

                    if (mentionedMember == null) {
                        return;
                    }

                    DatabaseManager.updatePointsAndLevel(mentionedMember, event.getGuild(), event.getChannel(), points);
                    event.getHook().sendMessage(mentionedMember.getAsMention() + " Points were set to: " + points)
                            .setEphemeral(true)
                            .queue();

                }

                if (interactionCommand.equalsIgnoreCase("addPoints")) {

                    OptionMapping pointsOption = event.getOption("points");
                    OptionMapping memberOption = event.getOption("member");

                    if (pointsOption == null || memberOption == null) {
                        return;
                    }

                    Integer points = pointsOption.getAsInt();
                    Member mentionedMember = memberOption.getAsMember();

                    if (mentionedMember == null) {
                        return;
                    }

                    DatabaseManager.incrementUserPoints(mentionedMember, event.getGuild(), event.getChannel(), points,
                            0);
                    event.getHook().sendMessage(points + " were added to: " + mentionedMember.getAsMention())
                            .setEphemeral(true)
                            .queue();

                }

                if (interactionCommand.equalsIgnoreCase("createroles")) {
                    Callables.createLevelRoles(event);
                    event.getHook().sendMessage("Roles Created").setEphemeral(true)
                            .queue();
                }

                if (interactionCommand.equalsIgnoreCase("removelevelroles")) {
                    Callables.removeLevelRoles(event);
                    event.getHook().sendMessage("Roles Removed").setEphemeral(true)
                            .queue();
                }
            } else {
                event.getHook().sendMessage("You are not an admin...").setEphemeral(true).queue();
            }

        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessage("An error occured and I could not process your command.").setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        event.getJDA().getGuilds().forEach(guild -> {
            guild.updateCommands().addCommands(
                    Commands.slash("getlevels", "Get a list of all the levels and the points needed to get them.")
                            .addOptions(
                                    new OptionData(OptionType.STRING, "section",
                                            "The section of level you\'re interested in", true)
                                            .addChoice("1-50", "firstHalf")
                                            .addChoice("51-100", "secondHalf")),
                    Commands.slash("updatelevel", "ADMIN ONLY")
                            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                            .addOption(OptionType.USER, "member",
                                    "Mention the person to add points too", true, false),
                    Commands.slash("addpoints", "ADMIN ONLY")
                            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                            .addOption(OptionType.USER, "member",
                                    "Mention the person to add points too", true, false)
                            .addOption(OptionType.INTEGER, "points",
                                    "Amount of points to add", true, false),
                    Commands.slash("retropointassignment", "ADMIN ONLY")
                            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                            .addOption(OptionType.USER, "member",
                                    "Mention the person to add points too", true, false)
                            .addOption(OptionType.STRING, "start_time",
                                    "Start time of joining", true, false)
                            .addOption(OptionType.STRING, "end_time",
                                    "End time on leave", true, false),
                    Commands.slash("setpoints", "ADMIN ONLY")
                            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                            .addOption(OptionType.USER, "member",
                                    "Mention the person to add points too", true, false)
                            .addOption(OptionType.INTEGER, "points",
                                    "Amount of points to set to", true, false),
                    Commands.slash("createroles", "ADMIN ONLY")
                            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                    Commands.slash("removelevelroles", "ADMIN ONLY")
                            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)))
                    .queue();

            guild.loadMembers().onSuccess(members -> {
                for (Member member : members) {
                    String userId = member.getUser().getId();
                    String username = member.getUser().getName();
                    String nickname = member.getEffectiveName();
                    List<Role> rolesList = member.getRoles();
                    String roles = String.join(", ", rolesList.stream().map(Role::getName).toArray(String[]::new));

                    System.out.println(String.format("UserID:%s Username:%s Nickname:%s Roles:[%s]", userId, username,
                            nickname, roles));

                    databaseManager.upsertUser(userId, username, nickname, roles);
                }

                System.out.println("All users added to DB");
            });
        });
    }
}
