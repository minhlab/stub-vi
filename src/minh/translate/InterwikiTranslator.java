package minh.translate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

/**
 * This tool help translate pages that are mostly links like Help:Contents/Browse
 * @author Le Ngoc Minh
 *
 */
public class InterwikiTranslator {

    public static void main(String[] args) throws IOException {
        System.out.println("Please input wikitext content:");
        InputStreamReader in = new InputStreamReader(System.in, Charsets.UTF_8);
        String enWikitext = CharStreams.toString(in);
        Pattern linkRegex = Pattern.compile("\\[\\[(.+?)((#.+?)?\\|.+?)?\\]\\]");
        Matcher wikitextMatcher = linkRegex.matcher(enWikitext);
        
        System.out.println("\n>>>\n");
        StringBuffer viWikitext = new StringBuffer();
        int counter = 0;
        while (wikitextMatcher.find()) {
            String title = wikitextMatcher.group(1);
            String simpleTitle = title;
            if (title.indexOf(':') > 0) {
                simpleTitle = title.substring(title.indexOf(':')+1);
            }
            String linkText = Objects.firstNonNull(wikitextMatcher.group(2), "");
            boolean linkTextIsSimple = Pattern.matches(
                    "\\|\\s*" + Pattern.quote(simpleTitle) + "\\s*", linkText);
//            if (linkTextIsSimple) System.out.format("simple: %s - %s\n", title, linkText);
            
            System.out.format("%2d. Inspecting “%s”... ", ++counter, title);
            try (InputStream pageIn = new URL("https://en.wikipedia.org/wiki/" + title).openStream();
                    InputStreamReader pageReader = new InputStreamReader(pageIn, Charsets.UTF_8)) {
                String pageHTML = CharStreams.toString(pageReader);
                String viLinkRegex = "<li class=\"interlanguage-link interwiki-vi\">.*?"
                        + "<a href=\"(?:https?:)?//vi.wikipedia.org/wiki/(.+?)\".*?</li>";
                Matcher linkMatcher = Pattern.compile(viLinkRegex, Pattern.DOTALL).matcher(pageHTML);
                if (linkMatcher.find()) {
                    String viTitle = URLDecoder.decode(linkMatcher.group(1), "UTF-8").replaceAll("_", " ");
                    String viSimpleTitle = viTitle;
                    if (viTitle.indexOf(':') > 0) {
                        viSimpleTitle = viTitle.substring(viTitle.indexOf(':')+1);
                    }
                    String viLinkText = linkTextIsSimple ? "|" + viSimpleTitle : linkText;
                    wikitextMatcher.appendReplacement(viWikitext, String.format("[[%s%s]]", viTitle, viLinkText));
                    System.out.format("Found: “%s”.\n", viTitle);
                } else {
                    System.out.println("Not found.");
                }
            } catch (Exception e) {
                System.err.println("Error while reaching " + title);
                e.printStackTrace();
            }
        }
        wikitextMatcher.appendTail(viWikitext);
        
        System.out.println("\n>>>\n");
//        System.out.println(viWikitext);
        File resultFile = File.createTempFile("translate-", ".txt");
        System.out.format("Saving to %s... ", resultFile.getAbsolutePath());
        Files.write(viWikitext, resultFile, Charsets.UTF_8);
        System.out.println("Done.");
        System.out.print("Opening in text editor... ");
        Runtime.getRuntime().exec(new String[] {"leafpad", resultFile.getAbsolutePath()});
        System.out.println("Done. Exit.");
    }
    
}
