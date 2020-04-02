package com.almworks.bugzilla.provider;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.datalink.*;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.integers.LongIterator;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.Terms;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.i18n.Local;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.Util;

import java.text.DateFormat;
import java.util.Date;

/*
<style>
body, td, pre { font-family: Tahoma; font-size: 11pt; }
.url { text-align: right; text-decoration: underline; margin-bottom: 8px; }
.header { font-weight: bold; font-size: 13pt; }
.fields { margin: 8px 0px 0px 0px; }
.value { margin: 0px 0px 0px 16px; }
.ctitle, .cuser, .cwhen { font-weight: bold; margin: 12px 0px 0px 0px;}
.cwhen { text-align: right; }
</style>
*/


public class BugExternalPresentation {
  private static final BugzillaAttribute[] ATTRIBUTES = {
    BugzillaAttribute.REPORTER, BugzillaAttribute.PRODUCT,
    BugzillaAttribute.COMPONENT, BugzillaAttribute.VERSION, BugzillaAttribute.SEVERITY,
    BugzillaAttribute.OPERATING_SYSTEM, BugzillaAttribute.PLATFORM, BugzillaAttribute.STATUS,
    BugzillaAttribute.RESOLUTION, BugzillaAttribute.ASSIGNED_TO, BugzillaAttribute.PRIORITY,
    BugzillaAttribute.TARGET_MILESTONE
  };

  private static final DateFormat DATE_FORMAT = DateUtil.LOCAL_DATE_TIME;

  private BugExternalPresentation() {}

  public static String getExternalPresentation(ItemVersion revision, String artifactUrl) {
    StringBuffer result = new StringBuffer();
    Integer id = revision.getValue(Bug.attrBugID);
    String summary = revision.getValue(Bug.attrSummary);
    if (id == null)
      return "Unknown Artifact";
    addUrl(result, artifactUrl);
    if (summary == null) {
      addHeader(result, id, Local.parse("($(" + Terms.key_artifact + ") is not loaded)"));
      return result.toString();
    }
    addHeader(result, id, summary);
    addAttributes(result, revision);
    addComments(result, revision);
    return result.toString();
  }

  private static void addAttributes(StringBuffer result, ItemVersion version) {
    result.append("<div class=\"fields\"><table border=\"0\" cellspacing=\"4\" cellpadding=\"0\">\n");
    for (BugzillaAttribute bza : ATTRIBUTES) {
      final BugzillaAttributeLink link = CommonMetadata.ATTR_TO_LINK.get(bza);
      if(!(link instanceof DefaultReferenceLink) && !(link instanceof UserReferenceLink)) {
        assert false : link;
        continue;
      }
      final DBAttribute attribute = link.getWorkspaceAttribute();

      final Object rawValue = version.getValue(attribute);
      String strValue = null;

      if(rawValue instanceof String) {
        strValue = (String)rawValue;
      } else if(rawValue instanceof Long) {
        final long item = ((Long) rawValue).longValue();
        if(item > 0) {
          final DBAttribute<String> visualAttr = ((SingleReferenceLink<String>)link).getReferentVisualKey();
          if(visualAttr != null) {
            strValue = version.forItem(item).getValue(visualAttr);
          }
        }
      }

      if(strValue != null) {
        result.append("<tr><td valign=\"top\" class=\"name\">")
          .append(bza.getName())
          .append(":</td><td valign=\"top\" class=\"value\">")
          .append(escape(strValue))
          .append("</td></tr>\n");
      }
    }
    result.append("</table></div>\n");
  }

  private static void addComments(StringBuffer result, ItemVersion version) {
    result.append("<div class=\"comments\">\n");
    LongList comments = version.getSlaves(CommentsLink.attrMaster);
    if (comments.size() == 0) {
      result.append("<div class=\"nocomments\">no comments</div>\n");
    } else {
      result.append("<table border=\"0\" cellpadding=\"0\" cellspacing=\"4\">\n");
      int i = 0;
      for (final LongIterator it = comments.iterator(); it.hasNext();) {
        final ItemVersion cv = version.forItem(it.next());
        String text = Util.NN(cv.getValue(CommentsLink.attrText));
        Date when = cv.getValue(CommentsLink.attrDate);
        String whentext = when == null ? "" : DATE_FORMAT.format(when);
        Long who = cv.getValue(CommentsLink.attrAuthor);
        String email;
        if(who == null || who <= 0) {
          email = "";
        } else {
          email = Util.NN(version.forItem(who).getValue(User.EMAIL));
        }
        String title = i == 0 ? "Description" : "Comment #" + i;
        i++;
        result.append("<tr><td valign=\"top\" class=\"ctitle\">").append(escape(title))
          .append("</td><td valign=\"top\" class=\"cuser\">").append(escape(email))
          .append("</td><td valign=\"top\" class=\"cwhen\">").append(escape(whentext))
          .append("</td></tr>\n");
        String betterText = escape(text);
//        betterText = "<pre>" + TextUtil.hardWrap(betterText, 80) + "</pre>";
        betterText = replaceEndLines(betterText);

        result.append("<tr><td valign=\"top\" class=\"ctext\" colspan=\"3\">").append(betterText)
          .append("</td></tr>\n");
      }
      result.append("</table>\n");
    }
    result.append("</div>\n");
  }

  private static String replaceEndLines(String text) {
    StringBuffer buf = new StringBuffer();
    int length = text.length();
    int pos = 0;
    while (pos < length) {
      int k = text.indexOf('\n', pos);
      if (k == -1) {
        buf.append(text.substring(pos));
        break;
      } else {
        buf.append(text.substring(pos, k));
        buf.append("<br>");
        pos = k + 1;
      }
    }
    return buf.toString();
  }

  private static void addHeader(StringBuffer result, Integer id, String summary) {
    result.append("<div class=\"header\"><span class=\"id\">#").append(id).append("</span> ")
      .append(escape(summary)).append("</div>\n");
  }

  private static String escape(String summary) {
    String escaped = JDOMUtils.escapeXmlEntities(summary);
    // hack for JEditorPane - it doesn't correctly show &apos;
    escaped = escaped.replaceAll("\\&apos\\;", "'");
    return escaped;
  }

  private static void addUrl(StringBuffer result, String artifactUrl) {
    result.append("<div class=\"url\">").append(artifactUrl).append("</div>\n");
  }
}
