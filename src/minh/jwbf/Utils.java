package minh.jwbf;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

public class Utils {

    private static final String CONFIG_PATH = "config.properties";
    
    private static Properties prop;
    private static MediaWikiBot wikiBot;

    public static synchronized MediaWikiBot getBot() throws IOException {
        if (wikiBot == null) {
            getProperties(); // ensure initialized
            wikiBot = new MediaWikiBot("http://vi.wikipedia.org/w/");
            wikiBot.login(prop.getProperty("user"), prop.getProperty("pass"));
        }
        return wikiBot;
    }
    
    public static synchronized Properties getProperties() throws IOException {
        if (prop == null) {
            prop = new Properties();
            try (FileReader reader = new FileReader(CONFIG_PATH)) {
                prop.load(reader);
            } catch (FileNotFoundException e) {
                System.err.format("Please put %s in the current directory.", CONFIG_PATH);
                throw e;
            }
        }
        return prop;
    }

}
