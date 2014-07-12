package minh.stub;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import net.sourceforge.jwbf.core.contentRep.Article;
import net.sourceforge.jwbf.mediawiki.actions.MediaWiki;
import net.sourceforge.jwbf.mediawiki.actions.queries.AllPageTitles;
import net.sourceforge.jwbf.mediawiki.actions.util.RedirectFilter;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

public class StubCategoryKeyAdder {
    
    public static final String STATE_PATH = "stub-cat-key-adder.state";
    public static final Article LAST_ARTICLE = new Article(null, (String)null);

    public static void main(String[] args) throws IOException, InterruptedException {
        MediaWikiBot bot = Utils.getBot();
        Iterable<String> titles = new AllPageTitles(bot, loadState(), "Sơ khai",
                RedirectFilter.nonredirects, MediaWiki.NS_CATEGORY);
//        titles = Iterables.limit(titles, 10); // for testing
        BlockingDeque<Article> inArticles = new LinkedBlockingDeque<Article>(10);
        BlockingDeque<Article> outArticles = new LinkedBlockingDeque<Article>(10);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        pool.execute(new ArticleLoader(titles, inArticles, bot));
        pool.execute(new ArticleEditor(inArticles, outArticles));
        pool.execute(new ArticleSaver(outArticles));
        pool.shutdown();
        pool.awaitTermination(100, TimeUnit.HOURS);
    }

    public static String loadState() throws IOException {
        String savedState = null;
        File stateFile = new File(STATE_PATH);
        if (stateFile.exists()) {
            savedState = Files.toString(stateFile, Charsets.UTF_8).trim();
        }
        return savedState;
    }


    public static void saveState(String state) throws IOException {
        Files.write(state, new File(STATE_PATH), Charsets.UTF_8);
    }

    private static class ArticleLoader implements Runnable {

        private Iterable<String> titles;
        private BlockingDeque<Article> articles;
        private MediaWikiBot bot;
        
        public ArticleLoader(Iterable<String> titles,
                BlockingDeque<Article> articles, MediaWikiBot bot) {
            super();
            this.titles = titles;
            this.articles = articles;
            this.bot = bot;
        }

        @Override
        public void run() {
            for (String title : titles) {
                articles.offerFirst(bot.getArticle(title));
            }
            articles.offer(LAST_ARTICLE);
        }
    }
    
    
    private static class ArticleEditor implements Runnable {

        private BlockingDeque<Article> inArticles;
        private BlockingDeque<Article> outArticles;

        public ArticleEditor(BlockingDeque<Article> inArticles,
                BlockingDeque<Article> outArticles) {
            super();
            this.inArticles = inArticles;
            this.outArticles = outArticles;
        }

        @Override
        public void run() {
            try {
                Pattern regex = Pattern.compile("\\[\\[Thể loại:Sơ khai .+?\\]\\]");
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                int count = 0;
                Article article;
                while ((article = inArticles.pollLast(1, TimeUnit.DAYS)) != LAST_ARTICLE) {
                    count++;
                    String title = article.getTitle();
                    if ("Thể loại:Sơ khai".equals(title)) {
                        continue;
                    }
                    Matcher matcher = regex.matcher(article.getText());
                    if (matcher.find()) {
                        String catText = matcher.group();
                        if (!catText.contains("|")) {
                            int len = catText.length();
                            String newCatText = catText.substring(0, len-2) + 
                                    "|" + title.substring(17) + "]]";
                            StringBuffer sb = new StringBuffer();
                            matcher.appendReplacement(sb, newCatText);
                            matcher.appendTail(sb);

                            System.out.format("\nArticle #%d: %s\n", count, title);
                            System.out.println(article.getText());
                            System.out.println(">>>");
                            System.out.println(sb);
                            System.out.print("Save ([y]/n)? ");
                            String answer;
                            try {
                                answer = inReader.readLine().trim();
                                if (answer.isEmpty()) answer = "y";
                                if ("y".equals(answer)) {
                                    article.setText(sb.toString());
                                    outArticles.offerFirst(article);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } // end if
                } // end while
                outArticles.offer(LAST_ARTICLE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } 
        }
    }
    
    
    private static class ArticleSaver implements Runnable {
        
        private BlockingDeque<Article> articles;

        public ArticleSaver(BlockingDeque<Article> articles) {
            super();
            this.articles = articles;
        }

        @Override
        public void run() {
            try {
                Article article;
                while ((article = articles.pollLast(1, TimeUnit.DAYS)) != LAST_ARTICLE) {
                    article.setMinorEdit(true);
                    article.save("Thêm khoá sắp xếp bằng công cụ bán tự động");
                    try {
                        saveState(article.getTitle());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
    }

}
