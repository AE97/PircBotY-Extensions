
import com.lordralex.ralexbot.api.Listener;
import com.lordralex.ralexbot.api.Priority;
import com.lordralex.ralexbot.api.events.CommandEvent;
import com.lordralex.ralexbot.api.events.EventType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @version 1.0
 * @author Joshua
 */
public class DeadflyCommand extends Listener {

    @Override
    public void onCommand(CommandEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final String sender = event.getSender();
        final String[] args = event.getArgs();
        final String channel = event.getChannel();
        BufferedReader reader = null;
        String target = channel;
        if (target == null) {
            target = sender;
        }
        if (target == null) {
            return;
        }
        if (args.length == 0) {
            sendMessage(target, "*deadfly <link>");
            return;
        }
        try {
            String url = args[0].replace(" ", "%20");
            URL path = new URL(url);
            reader = new BufferedReader(new InputStreamReader(path.openStream()));
            List<String> parts = new ArrayList<>();
            String s;
            while ((s = reader.readLine()) != null) {
                parts.add(s);
            }
            List<String> b = new ArrayList<>();
            for (String part : parts) {
                String[] c = part.split(",");
                b.addAll(Arrays.asList(c));
            }
            for (String string : b) {
                string = string.trim();
                if (string.startsWith("var url")) {
                    string = string.replace("var url =", "");
                    string = string.replace("\'", "");
                    string = string.replace(";", "");
                    string = string.trim();
                    sendMessage(target, parse("http://adf.ly/" + string));
                    break;
                }
            }
        } catch (IOException | URISyntaxException ex) {
            Logger.getLogger(MCFCommand.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(MCFCommand.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public String[] getAliases() {
        return new String[]{
                    "deadfly",
                    "df"
                };
    }

    @Override
    public void declarePriorities() {
        priorities.put(EventType.Command, Priority.NORMAL);
    }
}
