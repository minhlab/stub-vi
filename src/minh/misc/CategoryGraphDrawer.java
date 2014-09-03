package minh.misc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import minh.jwbf.CategoryMembersSimple;
import minh.jwbf.Utils;
import net.sourceforge.jwbf.mediawiki.actions.MediaWiki;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;

public class CategoryGraphDrawer {

    private static final String RESULT_PATH = "category-tree.dot";
    
    /**
     * Categories that won't be expanded. Should I keep this feature?
     */
    private static final Set<String> expandedBlacklist = 
            ImmutableSet.of( // should I use this feature???
//                    "Lịch sử tự nhiên theo quốc gia",
//                    "Lịch sử tự nhiên theo châu lục"
                    );
    private static final int MAX_DEPTH = 5;
    private static final int MAX_CHILDREN = 10;

    public static void main(String[] args) throws IOException {
        MediaWikiBot bot = Utils.getBot();
        String root = Utils.getProperties().getProperty("CategoryGraphDrawer.root");
        if (Strings.isNullOrEmpty(root)) {
            root = prompt();
        }
        if (root.isEmpty()) {
            System.err.println("Empty category. Exit.");
            return;
        }
        
        Multimap<String, String> graph = TreeMultimap.create();
        Set<String> trimmedCategories = Sets.newHashSet();
        breadthFirstSearch(bot, root, graph, trimmedCategories);
        
        try (PrintWriter out = new PrintWriter(RESULT_PATH)) {
            printGraph(graph, trimmedCategories, root, out);
            System.out.format("Category tree has been writen to %s. Use dot "
                    + "(graphviz) to generate an image or xdot to view.", RESULT_PATH);
            System.out.println("Done.");
        }
//        dot -Tsvg category-tree.dot -o category-tree.svg
    }

    public static String prompt() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Starting category (without namespace): ");
        return in.readLine().trim();
    }

    public static void breadthFirstSearch(MediaWikiBot bot, String root,
            Multimap<String, String> graph, Set<String> trimmedCategories) {
        Map<String, Integer> minDepth = Maps.newHashMap(); 
        Queue<String> queue = Lists.newLinkedList();
        queue.add(root);
        minDepth.put(root, 1);
        int count = 0;
        while (!queue.isEmpty()) {
            count++;
            if (count % 100 == 0) {
                System.out.format("%d...\n", count);
            }
            fetch(queue.poll(), graph, queue, trimmedCategories, minDepth, bot);
        }
//        System.out.println(graph);
    }

    private static void fetch(String root, Multimap<String, String> graph,
            Queue<String> queue, Set<String> trimmedCategories,
            Map<String, Integer> minDepth, MediaWikiBot bot) {
        if (minDepth.get(root) > MAX_DEPTH) {
            return;
        }
        List<String> children = Lists.newArrayList((Iterable<String>)
                new CategoryMembersSimple(bot, root, MediaWiki.NS_CATEGORY));
        if (children.size() > MAX_CHILDREN) {
            System.out.format("Too many children in \"%s\", displaying only "
                    + "first %d.\n", root, MAX_CHILDREN);
            children = children.subList(0, MAX_CHILDREN);
            trimmedCategories.add(root);
        }
        for (String child : children) {
            String simpleName = child.substring("Thể loại:".length());
            if (minDepth.containsKey(simpleName) && 
                    minDepth.get(simpleName) < minDepth.get(root)+1) { // avoid loops
                System.out.format("A loop may be detected at \"%s\".\n", child);
            } else {
                if (!expandedBlacklist.contains(simpleName)) {
                    queue.add(simpleName);
                }
                minDepth.put(simpleName, minDepth.get(root)+1);
            }
            graph.put(simpleName, root);
        };
    }

    public static void printGraph(Multimap<String, String> graph,
            Set<String> trimmedCategories, String name, PrintWriter out) {
        out.println("digraph \"" + name + "\" {");
        out.println("  rankdir=LR;");
        out.println("  node [shape=plaintext];");
        for (Entry<String, String> entry : graph.entries()) {
            String child = mark(entry.getKey(), trimmedCategories);
            String parent = mark(entry.getValue(), trimmedCategories);
            out.format("  \"%s\" -> \"%s\";\n", parent, child);
        }
        out.println("}");
    }
    
    private static String mark(String title, Set<String> set) {
        if (set.contains(title)) {
            title = "+" + title;
        }
        return title;
    }
    
}
