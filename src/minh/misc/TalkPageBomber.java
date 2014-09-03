package minh.misc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;

import minh.jwbf.Utils;
import net.sourceforge.jwbf.core.contentRep.Article;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class TalkPageBomber {

    public static void main(String[] args) throws IOException {
        if (args.length <= 0) {
            System.err.println("Cách dùng: bomber tệp-chứa-thông-điệp tóm-tắt");
            return;
        }
        String message = Files.toString(new File(args[0]), Charsets.UTF_8);
        String summary = args[1];
        MediaWikiBot bot = Utils.getBot();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                System.in, Charsets.UTF_8))) {
            Set<String> savedUsers = Sets.newHashSet();
            String user;
            while ((user = in.readLine()) != null) {
                user = user.trim();
                if (savedUsers.contains(user)) {
                    System.out.println("\tLặp lại: " + user);
                } else {
                    Article talkPage = bot.getArticle("Thảo luận Thành viên:" + user);
                    talkPage.setText(talkPage.getText() + "\n" + message);
                    talkPage.save(summary);
                    System.out.println("\tĐã gửi thông báo đến: " + user);
                    savedUsers.add(user);
                }
            }
        }
    }

}
