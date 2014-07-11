package minh.stub;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.jwbf.core.actions.util.ActionException;
import net.sourceforge.jwbf.core.contentRep.SimpleArticle;
import net.sourceforge.jwbf.mediawiki.actions.queries.CategoryMembersSimple;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class StubSorter {

    public static void main(String[] args) throws IOException {
        BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
        StubClassifier stubClassifier = new StubClassifier();
        MediaWikiBot bot = Utils.getBot();
        CategoryMembersSimple stubTitles = new CategoryMembersSimple(bot, "Sơ khai");
        while (!Iterables.isEmpty(stubTitles)) {
            for (Iterator<String> it = stubTitles.iterator(); it.hasNext();) {
                String stubTitle = it.next();
                SimpleArticle stub = bot.readData(stubTitle);
                stub.setEditTimestamp(new Date());
                String template = stubClassifier.classify(stub);
                String newContent = replaceStubTemplate(stub.getText(), template);
                File stubFile = File.createTempFile("", ".stub.wikitext");
                Files.write(newContent, stubFile, Charsets.UTF_8);
                edit(stubFile);
                stub.setText(Files.toString(stubFile, Charsets.UTF_8));
                try {
                    bot.writeContent(stub);
                    it.remove();
                } catch (ActionException e) {
                    System.out.println("Error while saving " + stubTitle);
                    e.printStackTrace();
                }
            }
            if (!Iterables.isEmpty(stubTitles)) {
                System.out.format("%d titles left. Do you want to retry [y]/n?", 
                        Iterables.size(stubTitles));
                String line = inReader.readLine();
                if (line.isEmpty()) {
                    line = "y"; // handling default value
                }
                if (!"y".equals(line)) {
                    break;
                }
            }
        }
    }

    private static String replaceStubTemplate(String content, String template) {
        final String regex = "\\{\\{(sơ khai.*|stub.*)\\}\\}";
        Matcher matcher = Pattern.compile(regex).matcher(content);
        if (matcher.find()) {
            StringBuffer sb = new StringBuffer();
            matcher.appendReplacement(sb, "{{" + template + "}}");
            matcher.appendTail(sb);
            return sb.toString();
        } else {
            System.err.println("Couldn't find template call.");
            return content;
        }
    }

    public static void edit(File stubFile) throws IOException {
        Process vim = new ProcessBuilder("gedit", stubFile.getAbsolutePath()).start();
        try {
            int code = vim.waitFor();
            if (code != 0) {
                System.out.println("Error code: " + code);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
}
