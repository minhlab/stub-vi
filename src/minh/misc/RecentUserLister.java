package minh.misc;

import java.io.IOException;
import java.util.Map.Entry;

import minh.jwbf.RecentchangeUsers;
import minh.jwbf.Utils;
import net.sourceforge.jwbf.mediawiki.actions.MediaWiki;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import org.joda.time.DateTime;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;

public class RecentUserLister {

    public static void main(String[] args) throws IOException {
        MediaWikiBot wikiBot = Utils.getBot();
        DateTime lastMonth = new DateTime().minusMonths(1);
        Iterable<String> users = new RecentchangeUsers(wikiBot, false, 
                lastMonth, MediaWiki.NS_MAIN);
//        users = Iterables.limit(users, 200); // for testing
        Multiset<String> userCounts = HashMultiset.create();
        int count = 0;
        for (String user : users) {
            userCounts.add(user);
            count++;
            if (count % 1000 == 0) {
                System.out.println(count + "...");
            }
        }
        Multimap<Integer, String> countUsers = TreeMultimap.create(
                Ordering.natural().reverse(), Ordering.natural());
        for (String user : userCounts) {
            countUsers.put(userCounts.count(user), user);
        }
        Iterable<Entry<Integer, String>> mostActiveUsers = 
                Iterables.limit(countUsers.entries(), 200);
        for (Entry<Integer, String> entry : mostActiveUsers) {
//            System.out.println(entry.getValue() + ":" + entry.getKey());
            System.out.println(entry.getValue());
        }
    }
    
}
