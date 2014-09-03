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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.jwbf.core.actions.Get;
import net.sourceforge.jwbf.core.actions.util.ActionException;
import net.sourceforge.jwbf.core.actions.util.HttpAction;
import net.sourceforge.jwbf.core.actions.util.ProcessException;
import net.sourceforge.jwbf.mediawiki.actions.MediaWiki;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

/**
 * A specialization of {@link CategoryMembers} with contains {@link String}s.
 * 
 * @author Thomas Stock
 */
@Slf4j
public class CategoryMembersSimple implements Iterable<String>, Iterator<String> {

  private Get msg;
  private final CategoryMembers cm;
  /**
   * Collection that will contain the result (titles of articles linking to the target) after performing the action has
   * finished.
   */
  private Collection<String> titleCollection = new ArrayList<String>();
  private Iterator<String> titleIterator;

  /**
   * @param categoryName
   *          like "Buildings" or "Chemical elements" without prefix "Category:" in {@link MediaWiki#NS_MAIN}
   */
  public CategoryMembersSimple(MediaWikiBot bot, String categoryName) {
    this(bot, categoryName, MediaWiki.NS_MAIN);

  }

  /**
   * @param categoryName
   *          like "Buildings" or "Chemical elements" without prefix "Category:"
   * @param namespaces
   *          for search
   */
  public CategoryMembersSimple(MediaWikiBot bot, String categoryName, int... namespaces) {
    cm = new CategoryMembers(bot, categoryName, namespaces) {

      public HttpAction getNextMessage() {
        return msg;
      }

      @Override
      protected void finalizeParse() {
        titleIterator = titleCollection.iterator();

      }

      @Override
      protected void addCatItem(String title, int pageid, int ns) {
        titleCollection.add(title);

      }

      @Override
      public String processAllReturningText(String s) {
        titleCollection.clear();
        String buff = super.processAllReturningText(s);

        titleIterator = titleCollection.iterator();
        return buff;
      }
    };

  }

  private synchronized void prepareCollection() {

    if (cm.init || (!titleIterator.hasNext() && cm.hasMoreResults)) {
      if (cm.init) {
        cm.setHasMoreMessages(true); // FIXME check if other action should have
                                     // this too
        msg = cm.generateFirstRequest();
      } else {
        msg = cm.generateContinueRequest(cm.nextPageInfo);
      }
      cm.init = false;
      try {

        cm.bot.performAction(cm);
        cm.setHasMoreMessages(true);
      } catch (ActionException e) {
        e.printStackTrace();
        cm.setHasMoreMessages(false);
      } catch (ProcessException e) {
        e.printStackTrace();
        cm.setHasMoreMessages(false);
      }

    }
  }

  public Iterator<String> iterator() {
    return this;
  }

  public boolean hasNext() {
    prepareCollection();
    return titleIterator.hasNext();
  }

  public String next() {
    prepareCollection();
    return titleIterator.next();
  }

  public void remove() {
    titleIterator.remove();

  }

}
