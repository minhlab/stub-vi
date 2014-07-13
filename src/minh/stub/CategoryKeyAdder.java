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

import minh.jwbf.AllPageTitles;
import net.sourceforge.jwbf.core.contentRep.Article;
import net.sourceforge.jwbf.mediawiki.actions.MediaWiki;
import net.sourceforge.jwbf.mediawiki.actions.util.RedirectFilter;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

public class CategoryKeyAdder {
    
    private static final String COMMON_PREFIX = "Lịch sử tự nhiên ";
    public static final String STATE_PATH = "stub-cat-key-adder.state";
    public static final Article LAST_ARTICLE = new Article(null, (String)null);

    public static void main(String[] args) throws IOException, InterruptedException {
        MediaWikiBot bot = Utils.getBot();
        // the trailing space is important to avoid misspelled names 
//        String savedState = loadState().substring(9);
        Iterable<String> titles = new AllPageTitles(bot, null, 
                COMMON_PREFIX, RedirectFilter.nonredirects, MediaWiki.NS_CATEGORY);
//        titles = Iterables.limit(titles, 10); // for testing
        BlockingDeque<ProofReadRequest> requests = new LinkedBlockingDeque<ProofReadRequest>(100);
        BlockingDeque<Article> outArticles = new LinkedBlockingDeque<Article>(1000);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        pool.execute(new ArticleLoader(titles, requests, outArticles, bot));
        pool.execute(new ArticleProofReadHelper(requests, outArticles));
        pool.execute(new ArticleSaver(outArticles));
        pool.shutdown();
        pool.awaitTermination(100, TimeUnit.HOURS);
        System.out.println("Finished!!!");
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
        private BlockingDeque<ProofReadRequest> requests;
        private BlockingDeque<Article> outArticles;
        private MediaWikiBot bot;
        
        public ArticleLoader(Iterable<String> titles,
                BlockingDeque<ProofReadRequest> requests, 
                BlockingDeque<Article> outArticles, MediaWikiBot bot) {
            super();
            this.titles = titles;
            this.requests = requests;
            this.outArticles = outArticles;
            this.bot = bot;
        }

        @Override
        public void run() {
            Pattern regex = Pattern.compile("\\[\\[Thể loại:" +
                    Pattern.quote(COMMON_PREFIX) + ".+?\\]\\]");
            for (String title : titles) {
                Article article = bot.getArticle(title);
                Matcher matcher = regex.matcher(article.getText());
                if (matcher.find()) {
                    String catText = matcher.group();
                    if (!catText.contains("|")) {
                        int len = catText.length();
                        String key = article.getTitle().substring(
                                ("Thể loại:" + COMMON_PREFIX).length());
                        String newCatText = catText.substring(0, len-2) + 
                                "|" + key + "]]";
                        StringBuffer sb = new StringBuffer();
                        matcher.appendReplacement(sb, newCatText);
                        matcher.appendTail(sb);
                        String newText = sb.toString();
                        try {
                            if (key.split(" ").length <= 1) {
                                article.setText(newText);
                                outArticles.offerFirst(article, 1,
                                        TimeUnit.DAYS);
                                System.out.format("Skipped proof read: %s.\n",
                                        article.getTitle());
                            } else {
                                requests.offerFirst(new ProofReadRequest(
                                        article, newText), 1, TimeUnit.DAYS);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } // end if matcher.find
            }
            try {
                requests.offer(new ProofReadRequest(LAST_ARTICLE, null), 
                        1, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    
    private static class ArticleProofReadHelper implements Runnable {

        private BlockingDeque<ProofReadRequest> requests;
        private BlockingDeque<Article> outArticles;
        private int count = 0;

        public ArticleProofReadHelper(BlockingDeque<ProofReadRequest> requests,
                BlockingDeque<Article> outArticles) {
            super();
            this.requests = requests;
            this.outArticles = outArticles;
        }

        @Override
        public void run() {
            BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
            ProofReadRequest request;
            while (true) {
                count++;
                try {
                    request = requests.pollFirst(1, TimeUnit.DAYS);
                    if (request.getArticle() == LAST_ARTICLE) {
                        break;
                    }
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                    continue;
                }

                String newText = request.getNewText();
                System.out.format("\nArticle #%d: %s\n", count, request.getArticle().getTitle());
                System.out.println(request.getArticle().getText());
                System.out.println(">>>");
                System.out.println(newText);
                System.out.print("OK ([y]/n)? ");
                String answer;
                try {
                    answer = inReader.readLine().trim();
                    if (answer.isEmpty()) answer = "y";
                    if (!"y".equals(answer)) {
                        File stubFile = File.createTempFile("stub-", ".wikitext");
                        Files.write(newText, stubFile, Charsets.UTF_8);
                        edit(stubFile);
                        newText = Files.toString(stubFile, Charsets.UTF_8);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    request.getArticle().setText(newText);
                    outArticles.offerFirst(request.getArticle(), 1, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } // end while
            try {
                outArticles.offerFirst(LAST_ARTICLE, 1, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public static void edit(File stubFile) throws IOException {
            Process vim = new ProcessBuilder("leafpad", 
                    stubFile.getAbsolutePath()).start();
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
    
    
    private static class ArticleSaver implements Runnable {
        
        private BlockingDeque<Article> articles;

        public ArticleSaver(BlockingDeque<Article> articles) {
            super();
            this.articles = articles;
        }

        @Override
        public void run() {
            Article article;
            while (true) {
                try {
                    if ((article = articles.pollLast(1, TimeUnit.DAYS)) == LAST_ARTICLE) {
                        break;
                    }
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                    continue;
                }
                
                article.setMinorEdit(true);
                article.save("Thêm khoá sắp xếp dùng công cụ bán tự động");
                try {
                    saveState(article.getTitle());
                    System.out.format("Saved %s, pending: %d.\n", 
                            article.getTitle(), articles.size());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private static class ProofReadRequest {
        Article article;
        String newText;
        public ProofReadRequest(Article article, String newText) {
            super();
            this.article = article;
            this.newText = newText;
        }
        public Article getArticle() {
            return article;
        }
        public String getNewText() {
            return newText;
        }
    }

}
