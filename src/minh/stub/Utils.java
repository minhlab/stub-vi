package minh.stub;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

public class Utils {

    public static MediaWikiBot getBot() throws IOException {
        Properties prop = new Properties();
        try (FileInputStream is = new FileInputStream("config.properties")) {
            prop.load(is);
        }
        
        MediaWikiBot wikiBot = new MediaWikiBot("http://vi.wikipedia.org/w/");
        wikiBot.login(prop.getProperty("user"), prop.getProperty("pass"));
        return wikiBot;
    }

}
