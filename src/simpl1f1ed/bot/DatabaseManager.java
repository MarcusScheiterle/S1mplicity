package simpl1f1ed.bot;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class DatabaseManager {
    private static Connection connection;

    public DatabaseManager(String databasePath) {
        try {
            System.out.println(databasePath);
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        } catch (SQLException e) {
            e.printStackTrace();
            return; // Exit if there's a problem establishing the connection.
        }
        
        try {
            if (connection == null || connection.isClosed() || !doesTableExist("users")) {
                createTables();
                System.out.println("Tables Created");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean doesTableExist(String tableName) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet resultSet = metaData.getTables(null, null, tableName, null);
            return resultSet.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void createTables() {
        try {
            String createUserTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id TEXT PRIMARY KEY," +
                    "username TEXT," +
                    "nickname TEXT," +
                    "roles TEXT," +
                    "points INTEGER DEFAULT 0," +
                    "level INTEGER DEFAULT 0," +
                    "prestige INTEGER DEFAULT 0," +
                    "last_message_timestamp TIMESTAMP DEFAULT null," +
                    "admin INTEGER DEFAULT 0" +
                    ")";
            connection.createStatement().executeUpdate(createUserTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void upsertUser(String id, String username, String nickname, String roles) {
        try {
            String updateUserQuery = "UPDATE users SET username = ?, nickname = ?, roles = ? WHERE id = ?";
            PreparedStatement updateStatement = connection.prepareStatement(updateUserQuery);
            updateStatement.setString(1, username);
            updateStatement.setString(2, nickname);
            updateStatement.setString(3, roles);
            updateStatement.setString(4, id);
            int rowsUpdated = updateStatement.executeUpdate();

            if (rowsUpdated == 0) {
                String insertUserQuery = "INSERT INTO users (id, username, nickname, roles, level, last_message_timestamp) VALUES (?, ?, ?, ?, ?, ?)";
                PreparedStatement insertStatement = connection.prepareStatement(insertUserQuery);
                insertStatement.setString(1, id);
                insertStatement.setString(2, username);
                insertStatement.setString(3, nickname);
                insertStatement.setString(4, roles);
                insertStatement.setInt(5, 0);
                insertStatement.setString(6, null);
                insertStatement.executeUpdate();
            }
        } catch (NullPointerException nullE) {
            createTables();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static int getUserPoints(String userId) {
        int points = 0;
        try {
            String selectPointsQuery = "SELECT points FROM users WHERE id = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(selectPointsQuery);
            preparedStatement.setString(1, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                points = resultSet.getInt("points");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return points;
    }

    public static void incrementUserPoints(Member member, Guild guild, Channel channel, int pointsToAdd, int reasonCode) {
        try {
            String checkTimestampQuery = "SELECT last_message_timestamp FROM users WHERE id = ?";
            PreparedStatement timestampStatement = connection.prepareStatement(checkTimestampQuery);
            timestampStatement.setString(1, member.getId());
            ResultSet timestampResult = timestampStatement.executeQuery();

            if (timestampResult.next()) {
                if (reasonCode == 1) {
                    Timestamp lastTimestamp = timestampResult.getTimestamp("last_message_timestamp");
                    long secondsPassed = lastTimestamp != null
                            ? Duration.between(lastTimestamp.toInstant(), Instant.now()).getSeconds()
                            : Long.MAX_VALUE;

                    if (lastTimestamp == null || secondsPassed > 10) {
                        int currentPoints = getUserPoints(member.getId());
                        int newPoints = currentPoints + pointsToAdd;
                        updatePointsAndLevel(member, guild, channel, newPoints);
                    } else {
                        System.out.println(
                                "Not enough time has passed since the last message. Seconds Passed: " + secondsPassed);
                    }
                } else {
                    int currentPoints = getUserPoints(member.getId());
                    int newPoints = currentPoints + pointsToAdd;
                    updatePointsAndLevel(member, guild, channel, newPoints);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void updatePointsAndLevel(Member member, Guild guild, Channel channel, int newPoints) {

        String userId = member.getId();

        int currentLevel = getLevel(userId);
        int newLevel = Levels.calculateLevel(newPoints);

        if (newLevel != currentLevel) {
            try {
                String updatePointsAndLevelQuery = "UPDATE users SET points = ?, level = ? WHERE id = ?";
                PreparedStatement preparedStatement = connection.prepareStatement(updatePointsAndLevelQuery);
                preparedStatement.setInt(1, newPoints);
                preparedStatement.setInt(2, newLevel);
                preparedStatement.setString(3, userId);
                preparedStatement.executeUpdate();
                System.out.println(String.format("Points and Level Updated - User: %s Points: %d Level: %d", userId,
                        newPoints, newLevel));

                handleLevelUp(member, guild, channel);
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                String updatePointsQuery = "UPDATE users SET points = ? WHERE id = ?";
                PreparedStatement preparedStatement = connection.prepareStatement(updatePointsQuery);
                preparedStatement.setInt(1, newPoints);
                preparedStatement.setString(2, userId);
                preparedStatement.executeUpdate();
                System.out.println(String.format("Points Updated - User: %s Points: %d", userId, newPoints));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void handleLevelUp(Member member, Guild guild, Channel channel) {

        // Assign varibales
        int currentLevel = DatabaseManager.getLevel(member.getId());
        String generalRoleName = Levels.getLevelName(currentLevel);

        // Check for nonnulls
        if (member == null || guild == null) {
            return;
        }

        Callables.levelRoleChange(member, guild, currentLevel);

        if (currentLevel % 10 != 0) {
            return;
        }

        // Build Embed
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setThumbnail(member.getEffectiveAvatarUrl())
                .setDescription(
                        member.getAsMention() + " Bigger number, Better Person! You are now a **" + generalRoleName
                                + "**")
                .setFooter("S1mplicity", member.getJDA().getSelfUser().getEffectiveAvatarUrl())
                .setTitle("Congratulations " + member.getEffectiveName() + ", you've tierd up!")
                .setColor(0x42F56C);

        // Main section for prestige or normal level up
        Button prestigeButton = null;

        try {
            int pointsNeededForNextLevel = Levels.calculateLevelPointsMap().get(currentLevel + 1)
                    - Levels.calculateLevelPointsMap().get(currentLevel);
            embedBuilder.addField("Current Level", "" + String.valueOf(currentLevel), true)
                    .addField("Next Level", "" + String.valueOf(currentLevel + 1), true)
                    .addField("Points Needed for Next Level", "" + String.valueOf(pointsNeededForNextLevel), true);
        } catch (NullPointerException e) {
            prestigeButton = Button.primary("prestige_button", "Prestige Me!");

            int currentPrestige = DatabaseManager.getPrestige(member.getId());
            embedBuilder.addField("Current Level", "" + String.valueOf(currentLevel), true)
                    .addField("Next Prestige", "" + String.valueOf(currentPrestige + 1), true)
                    .setDescription(member.getAsMention() + " You can now prestige and reset your level!");
        }

        MessageEmbed embed = embedBuilder.build();

        // Send message
        if (channel != null) {
            if (prestigeButton != null) {
                ((MessageChannel) channel).sendMessageEmbeds(embed).addActionRow(prestigeButton).queue();
            } else {
                ((MessageChannel) channel).sendMessageEmbeds(embed).queue();
            }
        } else {
            MessageChannel targetChannel = member.getJDA().getTextChannelById("1024458153196273775");
            if (targetChannel != null) {
                if (prestigeButton != null) {
                    targetChannel.sendMessageEmbeds(embed).addActionRow(prestigeButton).queue();
                } else {
                    targetChannel.sendMessageEmbeds(embed).queue();
                }
            } else {
                System.out.println("Target channel not found!");
            }
        }
    }

    public static void setPrestige(Member member, Guild guild, Channel channel, int newPrestige) {
        try {

            int previousPrestige = getPrestige(member.getId());

            String updatePrestigeQuery = "UPDATE users SET prestige = ? WHERE id = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(updatePrestigeQuery);
            preparedStatement.setInt(1, newPrestige);
            preparedStatement.setString(2, member.getId());
            preparedStatement.executeUpdate();

            if (newPrestige > previousPrestige) {
                handlePrestige(member, guild, channel, newPrestige);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void incrementPrestige(Member member, Guild guild, Channel channel) {
        try {
            int currentPrestige = getPrestige(member.getId());
            int newPrestige = currentPrestige + 1;

            String updatePrestigeQuery = "UPDATE users SET prestige = ? WHERE id = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(updatePrestigeQuery);
            preparedStatement.setInt(1, newPrestige);
            preparedStatement.setString(2, member.getId());
            preparedStatement.executeUpdate();

            handlePrestige(member, guild, channel, newPrestige);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void handlePrestige(Member member, Guild guild, Channel channel, int currentPrestige) {
        // Create an embed message
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setThumbnail(member.getEffectiveAvatarUrl())
                .setFooter("S1mplicity", member.getJDA().getSelfUser().getEffectiveAvatarUrl())
                .setTitle("Congratulations " + member.getEffectiveName() + ", you've Prestiged!")
                .setDescription("@here " + member.getAsMention() + " Is now Prestige " + currentPrestige + "!")
                .setColor(0x42F56C);

        if (member == null || guild == null) {
            return;
        }

        int currentLevel = DatabaseManager.getLevel(member.getId());
        Button prestigeButton = null;

        try {
            int pointsNeededForNextLevel = Levels.calculateLevelPointsMap().get(currentLevel + 1)
                    - Levels.calculateLevelPointsMap().get(currentLevel);
            embedBuilder.addField("Current Level", "" + String.valueOf(currentLevel), true)
                    .addField("Next Level", "" + String.valueOf(currentLevel + 1), true)
                    .addField("Points Needed for Next Level", "" + String.valueOf(pointsNeededForNextLevel), true);
        } catch (NullPointerException e) {
            prestigeButton = Button.primary("prestige_button", "Prestige Me!");

            embedBuilder.addField("Current Level", "" + String.valueOf(currentLevel), true)
                    .addField("Next Prestige", "" + String.valueOf(currentPrestige + 1), true)
                    .setDescription(member.getAsMention() + " You can now prestige and reset your level!");
        }

        Callables.levelRoleChange(member, guild, currentLevel);

        MessageEmbed embed = embedBuilder.build();

        if (channel != null) {
            if (prestigeButton != null) {
                ((MessageChannel) channel).sendMessageEmbeds(embed).addActionRow(prestigeButton).queue();
            } else {
                ((MessageChannel) channel).sendMessageEmbeds(embed).queue();
            }
        } else {
            MessageChannel targetChannel = member.getJDA().getTextChannelById("1024458153196273775");
            if (targetChannel != null) {
                if (prestigeButton != null) {
                    targetChannel.sendMessageEmbeds(embed).addActionRow(prestigeButton).queue();
                } else {
                    targetChannel.sendMessageEmbeds(embed).queue();
                }
            } else {
                System.out.println("Target channel not found!");
            }
        }
    }

    public static int getLevel(String userId) {
        int level = 0;
        try {
            String selectLevelQuery = "SELECT level FROM users WHERE id = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(selectLevelQuery);
            preparedStatement.setString(1, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                level = resultSet.getInt("level");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return level;
    }

    public static int getPrestige(String userId) {
        int prestige = 0;
        try {
            String selectPrestigeQuery = "SELECT prestige FROM users WHERE id = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(selectPrestigeQuery);
            preparedStatement.setString(1, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                prestige = resultSet.getInt("prestige");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return prestige;
    }

    public void lastMessageUpdate(String userId) {
        try {
            String updateTimestampQuery = "UPDATE users SET last_message_timestamp = ? WHERE id = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(updateTimestampQuery);
            Timestamp currentTimestamp = Timestamp.from(Instant.now());
            preparedStatement.setTimestamp(1, currentTimestamp);
            preparedStatement.setString(2, userId);
            preparedStatement.executeUpdate();
            System.out.println(String.format("Last Message Updated - User: %s Time: %s", userId, currentTimestamp));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isAdmin(String userId) {
        try {
            String selectAdminQuery = "SELECT admin FROM users WHERE id = ?";
            PreparedStatement preparedStatement = connection.prepareStatement(selectAdminQuery);
            preparedStatement.setString(1, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                int admin = resultSet.getInt("admin");
                return admin == 1; // Assuming 1 represents admin
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
