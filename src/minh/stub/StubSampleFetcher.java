package minh.stub;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import minh.jwbf.Utils;
import net.sourceforge.jwbf.core.contentRep.SimpleArticle;
import net.sourceforge.jwbf.mediawiki.actions.MediaWiki;
import net.sourceforge.jwbf.mediawiki.actions.queries.AllPageTitles;
import net.sourceforge.jwbf.mediawiki.actions.queries.BacklinkTitles;
import net.sourceforge.jwbf.mediawiki.actions.queries.CategoryMembersSimple;
import net.sourceforge.jwbf.mediawiki.actions.util.RedirectFilter;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import org.yaml.snakeyaml.Yaml;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class StubSampleFetcher {

    private static final int ARTICLE_PER_TEMPLATE = 10;
    public static final String TEMPLATES_PATH = "stub.templates";
    public static final String ARTICLES_PATH = "stub.articles";

    public static void main(String[] args) throws IOException {
        MediaWikiBot bot = Utils.getBot();
        Iterable<String> templates = getTemplateTitles(bot);
//        titles = Iterables.limit(titles, 10); // for testing
//        System.out.println(Joiner.on("\n").join(titles)); // for testing
        Map<String, List<String>> templateMap = fetchAndSaveTemplates(bot, templates);
        Map<String, String> articleMap = fetchAndSaveArticles(bot, templateMap);
        System.out.format("Finished fetching %d templates, %d articles.",
                templateMap.size(), articleMap.size());
    }

    /**
     * Fetch stub template titles, may contain duplication.
     * 
     * @param bot
     * @return
     */
    public static Iterable<String> getTemplateTitles(MediaWikiBot bot) {
        // the trailing space is important to avoid misspelled names 
        Iterable<String> titlesByName = new AllPageTitles(bot, null, "Sơ khai ",
                RedirectFilter.nonredirects, MediaWiki.NS_TEMPLATE);
        Iterable<String> titlesByCategory = new CategoryMembersSimple(
                bot, "Bản mẫu sơ khai", MediaWiki.NS_TEMPLATE);
        Iterable<String> titles = Iterables.concat(titlesByName, titlesByCategory);
        titles = Iterables.filter(titles, new Predicate<String>() {
            @Override
            public boolean apply(String title) {
                return !title.endsWith("/doc") && 
                        !"Bản mẫu:Sơ khai".equals(title);
            }
        });
        return titles;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> fetchAndSaveTemplates(MediaWikiBot bot,
            Iterable<String> templates) throws IOException {
        if (new File(TEMPLATES_PATH).exists()) {
            return (Map<String, List<String>>) readYAML(TEMPLATES_PATH);
        }
        
        int count = 0;
        Map<String, List<String>> templateMap = Maps.newHashMap(); 
        for (String template : templates) {
            if (templateMap.containsKey(template)) {
                continue; // found duplication
            }
            Iterable<String> backlinkTitles = new BacklinkTitles(bot, template,
                    RedirectFilter.nonredirects, MediaWiki.NS_MAIN);
            Iterable<String> articles;
            if (template.startsWith("Bản mẫu:Sơ khai ")) {
                String mainArticleTitle = template.substring(16);
                String category = "Thể loại:" + mainArticleTitle;
                Iterable<String> catMemberTitles = new CategoryMembersSimple(
                        bot, mainArticleTitle, MediaWiki.NS_MAIN);
                Iterable<String> catBacklinkTitles = new BacklinkTitles(bot,
                        category, RedirectFilter.nonredirects, MediaWiki.NS_MAIN);
                // try desperately to limit the number of requests to server
                // while still preventing any duplication
                articles = Sets.newLinkedHashSet(Iterables.limit(
                        Iterables.concat(backlinkTitles,
                                ImmutableList.of(mainArticleTitle),
                                catMemberTitles, catBacklinkTitles),
                        ARTICLE_PER_TEMPLATE * 2));
            } else {
                articles = backlinkTitles;
            }
            // actually limit the number of articles
            articles = Iterables.limit(articles, ARTICLE_PER_TEMPLATE);
            templateMap.put(template, Lists.newArrayList(articles));
            count++;
            if (count % 1000 == 0) {
                System.out.format("%d...\n", count);
            }
        }
        
        writeYAML(templateMap, TEMPLATES_PATH);
        return templateMap;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fetchAndSaveArticles(MediaWikiBot bot,
            Map<String, List<String>> titleMap) throws IOException {
        if (new File(ARTICLES_PATH).exists()) {
            return (Map<String, String>) readYAML(ARTICLES_PATH); 
        }
        
        int count = 0;
        Map<String, String> articleMap = Maps.newHashMap();
        for (List<String> articleTitles : titleMap.values()) {
            for (String articleTitle : articleTitles) {
                if (articleMap.containsKey(articleTitle)) {
                    continue;
                }
                SimpleArticle article = bot.readData(articleTitle);
                articleMap.put(articleTitle, article.getText());
                count++;
                if (count % 1000 == 0) {
                    System.out.format("%d...\n", count);
                }
            }
        }
        
        writeYAML(articleMap, ARTICLES_PATH);
        return articleMap;
    }

    public static Object readYAML(String path) throws IOException,
            FileNotFoundException {
        Object articleMap;
        try (Reader reader = new FileReader(path)) {
            Yaml yaml = new Yaml();
            System.out.format("Found data at %s, reading... ", path);
            articleMap = yaml.load(reader);
            System.out.println("Done.");
        }
        return articleMap;
    }

    public static void writeYAML(Map<?, ?> map, String path)
            throws IOException {
        try (Writer writer = new FileWriter(path)) {
            Yaml yaml = new Yaml();
            System.out.format("Writing to %s... ", path);
            yaml.dump(map, writer);
            System.out.println("Done.");
        }
    }

}
