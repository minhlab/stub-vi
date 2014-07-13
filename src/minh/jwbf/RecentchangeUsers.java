/*
 * Copyright 2007 Thomas Stock.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Contributors:
 *
 */
package minh.jwbf;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.jwbf.core.RequestBuilder;
import net.sourceforge.jwbf.core.actions.util.HttpAction;
import net.sourceforge.jwbf.mediawiki.ApiRequestBuilder;
import net.sourceforge.jwbf.mediawiki.actions.MediaWiki;
import net.sourceforge.jwbf.mediawiki.actions.MediaWiki.Version;
import net.sourceforge.jwbf.mediawiki.actions.queries.RecentchangeTitles;
import net.sourceforge.jwbf.mediawiki.actions.queries.TitleQuery;
import net.sourceforge.jwbf.mediawiki.actions.util.MWAction;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import org.jdom.Element;
import org.joda.time.DateTime;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Modified from {@link RecentchangeTitles} to find out users that contributed
 * recent changes.
 * 
 * Gets a list of users recently made changes, ordered by modification
 * timestamp. Parameters: rcfrom (paging timestamp), rcto (flt), rcnamespace
 * (flt), rcminor (flt), rcusertype (dflt=not|bot), rcdirection (dflt=older),
 * rclimit (dflt=10, max=500/5000) F api.php ? action=query & list=recentchanges
 * - List last 10 changes
 * 
 * @author Thomas Stock
 * @author Minh Ngoc Le
 */
@Slf4j
public class RecentchangeUsers extends TitleQuery<String> {

  /** value for the bllimit-parameter. **/
  private static final int limit = 100;

  private int find = 1;

  private final MediaWikiBot bot;

  private final int[] namespaces;

  /**
   * Collection that will contain the result (users of articles linking to the target) after performing the action has
   * finished.
   */
  private final Collection<String> userCollection = Lists.newArrayList();
  private final boolean uniqUsers;

  private DateTime endDateTime;

  private class RecentInnerAction extends InnerAction {

    protected RecentInnerAction(Version v) {
      super(v);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String processAllReturningText(final String s) {

      userCollection.clear();
      parseArticleTitles(s);

      if (uniqUsers) {
        Set<String> set = Sets.newHashSet();
        set.addAll(userCollection);
        userCollection.clear();
        userCollection.addAll(set);
      }
      titleIterator = userCollection.iterator();

      return "";
    }
  }

  /**
   * generates the next MediaWiki-request (GetMethod) and adds it to msgs.
   * 
   * @param namespace
   *          the namespace(s) that will be searched for links, as a string of numbers separated by '|'; if null, this
   *          parameter is omitted
   * @param rcstart
   *          timestamp
   */
  private HttpAction generateRequest(int[] namespace, String rcstart) {

    try {
        RequestBuilder requestBuilder = new ApiRequestBuilder() //
            .action("query") //
            .formatXml() //
            .param("list", "recentchanges") //
            .param("rclimit", limit + "") //
            .param("rcprop", URLEncoder.encode("user|timestamp", "UTF-8"))
            .param("rcshow", URLEncoder.encode("!bot|!anon", "UTF-8"))
        ;
        if (namespace != null) {
          requestBuilder.param("rcnamespace", MediaWiki.encode(MWAction.createNsString(namespace)));
        }
        if (rcstart.length() > 0) {
          requestBuilder.param("rcstart", rcstart);
        }

        return requestBuilder.buildGet();
    } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
    }

  }

  private HttpAction generateRequest(int[] namespace) {

    return generateRequest(namespace, "");

  }

  /**
   *
   */
  public RecentchangeUsers(MediaWikiBot bot, int... ns) {
    this(bot, false, ns);

  }

  /**
   *
   */
  public RecentchangeUsers(MediaWikiBot bot, boolean uniqChanges, int... ns) {
      this(bot, false, null, ns);
  }

  /**
   *
   */
  public RecentchangeUsers(MediaWikiBot bot, boolean uniqUsers, DateTime endDateTime, int... ns) {
    super(bot);
    this.endDateTime = endDateTime;
    namespaces = ns;
    this.bot = bot;
    this.uniqUsers = uniqUsers;

  }

  /**
   *
   */
  public RecentchangeUsers(MediaWikiBot bot) {
    this(bot, MediaWiki.NS_ALL);

  }

  /**
   * picks the article name from a MediaWiki api response.
   * 
   * @param s
   *          text for parsing
   */
  @Override
  protected Collection<String> parseArticleTitles(String s) {
    Element root = getRootElement(s);
    findContent(root);
    return userCollection;

  }

  @SuppressWarnings("unchecked")
  private void findContent(final Element root) {

    Iterator<Element> el = root.getChildren().iterator();
    while (el.hasNext()) {
      Element element = el.next();
      if (element.getQualifiedName().equalsIgnoreCase("rc")) {
        String timestamp = element.getAttribute("timestamp").getValue();
        DateTime dateTime = DateTime.parse(timestamp);
        
        if (find < limit && dateTime.isAfter(endDateTime)) {
          userCollection.add(MediaWiki.decode(element.getAttributeValue("user")));
        }

        if (dateTime.isAfter(endDateTime)) {
            nextPageInfo = timestamp;
        } else {
            nextPageInfo = "";
        }
        find++;
      } else {
        findContent(element);
      }

    }
  }

  @Override
  protected HttpAction prepareCollection() {
    find = 1;
    if (getNextPageInfo().length() <= 0) {
      return generateRequest(namespaces);
    } else {
      return generateRequest(namespaces, getNextPageInfo());
    }

  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return new RecentchangeUsers(bot, uniqUsers, endDateTime, namespaces);
  }

  @Override
  protected String parseHasMore(String s) {
    return "";
  }

  @Override
  protected InnerAction getInnerAction(Version v) {

    return new RecentInnerAction(v);
  }

}
