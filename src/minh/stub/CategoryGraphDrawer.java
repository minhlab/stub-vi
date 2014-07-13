package minh.stub;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import net.sourceforge.jwbf.mediawiki.actions.MediaWiki;
import net.sourceforge.jwbf.mediawiki.actions.queries.CategoryMembersSimple;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;

public class CategoryGraphDrawer {

    private static final Set<String> expandingBlacklist = 
            ImmutableSet.of("Lịch sử tự nhiên theo quốc gia",
                    "Lịch sử tự nhiên theo châu lục");
    private static final int MAX_DEPTH = 5;
    private static final int MAX_CHILDREN = 10;

    public static void main(String[] args) throws IOException {
        MediaWikiBot bot = Utils.getBot();
//        BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
//        System.out.print("Starting category: ");
//        String root = inReader.readLine().trim();
        String root = "Khoa học tự nhiên";
        Multimap<String, String> graph = TreeMultimap.create();
        Set<String> trimmedCategories = Sets.newHashSet();
        fetch(root, graph, trimmedCategories, 1, bot);
//        System.out.println(graph);
        try (PrintWriter out = new PrintWriter("category-tree.dot")) {
            out.println("digraph \"" + root + "\" {");
            out.println("  rankdir=LR;");
            out.println("  node [shape=plaintext];");
            for (Entry<String, String> entry : graph.entries()) {
                String child = mark(entry.getKey(), trimmedCategories);
                String parent = mark(entry.getValue(), trimmedCategories);
                out.format("  \"%s\" -> \"%s\";\n", parent, child);
            }
            out.println("}");
        }
        System.out.println("Done.");
    }
    
    private static String mark(String title, Set<String> set) {
        if (set.contains(title)) {
            title = "+" + title;
        }
        return title;
    }

    private static int count = 0;
    
    private static void fetch(String root, Multimap<String, String> graph,
            Set<String> trimmedCategories, int currentLevel, 
            MediaWikiBot bot) {
        count++;
        if (count % 100 == 0) {
            System.out.format("%d...\n", count);
        }
        if (currentLevel > MAX_DEPTH) {
            return;
        }
        List<String> children = Lists.newArrayList((Iterable<String>)
                new CategoryMembersSimple(bot, root, MediaWiki.NS_CATEGORY));
        if (children.size() > MAX_CHILDREN) {
            System.out.format("Too many children in \"%s\", displaying only first %d.\n", 
                    root, MAX_CHILDREN);
            children = children.subList(0, MAX_CHILDREN);
            trimmedCategories.add(root);
        }
        for (String child : children) {
            String simpleName = child.substring(9);
            if (!graph.containsKey(simpleName) && // avoid loops
                    !expandingBlacklist.contains(simpleName)) { 
                fetch(simpleName, graph, trimmedCategories, 
                        currentLevel+1, bot);
            }
            graph.put(simpleName, root);
        };
    }
    
}
