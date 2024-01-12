package simpl1f1ed.bot;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class App {
    public static void main(String[] args) {
        String token = System.getenv("BOT_TOKEN");

        try {            
            JDABuilder builder = JDABuilder.createDefault(token);
            builder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
            builder.enableIntents(GatewayIntent.GUILD_MEMBERS);
            builder.addEventListeners(new BotListener());
            builder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
