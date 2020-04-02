package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.http.ExtractFormParameters;
import com.almworks.api.connector.http.HtmlUtils;
import com.almworks.bugzilla.integration.BugzillaDateUtil;
import com.almworks.bugzilla.integration.BugzillaHTMLConstants;
import com.almworks.bugzilla.integration.data.Comment;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.integration.err.AmbiguousUserMatchException;
import com.almworks.util.Pair;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.text.TextUtil;
import com.almworks.util.xml.JDOMElementIterator;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.apache.commons.httpclient.NameValuePair;
import org.jdom.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class OperUtils {
  private static final String UPDATE_URL_UPPERCASE = Util.upper(BugzillaHTMLConstants.URL_UPDATE_BUG);
  private static final String SUBMIT_URL_UPPERCASE = Util.upper(BugzillaHTMLConstants.URL_SUBMIT_BUG);
  static final String PROPAGATE_RESOLUTIONS = "####-propagate-resolutions-####";
  private static final Collection<String> TITLES =
    StringUtil.caseInsensitiveSet("title", "h1", "h2", "h3", "h4", "h5", "b", "strong", "em");
  static final long COMMENT_CHECK_TOLERANCE = Const.MINUTE * 10;
  private static final Pattern ISPRIVATE_PATTERN = Pattern.compile("isprivate[\\-_]([0-9]+)");
  private static final Pattern COMMENT_DIV_CLASS_PATTERN = Pattern.compile("\\bbz_comment\\b");
  private static final Pattern COMMENT_HEAD_CLASS_PATTERN = Pattern.compile("\\bbz_(first_)?comment_head\\b");
  private static final Pattern COMMENT_DATE_PATTERN =
    Pattern.compile("\\b\\d\\d\\d\\d\\-\\d\\d\\-\\d\\d\\s\\d\\d:\\d\\d(:\\d\\d)?(\\s[A-Z][A-Z][A-Z]?)?\\b");
  private static final String UPDATE_ATTACH_UPPERCASE = Util.upper("attachment.cgi");

  static Element findUpdateFormElement(final Element root, boolean acceptNoActionForm) {
    return findFormElement(root, UPDATE_URL_UPPERCASE, acceptNoActionForm);
  }

  static Element findSubmitFormElement(final Element root, boolean acceptNoActionForm) {
    return findFormElement(root, SUBMIT_URL_UPPERCASE, acceptNoActionForm);
  }

  static Element findUpdateAttachmentFormElement(Element root, boolean acceptNoActionForm) {
    return findFormElement(root, UPDATE_ATTACH_UPPERCASE, acceptNoActionForm);
  }

  private static Element findFormElement(Element root, String script, boolean acceptNoActionForm) {
    Iterator<Element> ii = JDOMUtils.searchElementIterator(root, "form");
    Element noActionForm = null;
    while (ii.hasNext()) {
      Element element = ii.next();
      String value = JDOMUtils.getAttributeValue(element, "action", "", false);
      value = value == null ? null : value.trim();
      if (value != null && value.length() > 0) {
        if (Util.upper(value).endsWith(script)) {
          return element;
        }
      } else {
        if (noActionForm == null &&
          "post".equalsIgnoreCase(JDOMUtils.getAttributeValue(element, "method", "", false)))
        {
          noActionForm = element;
        }
      }
    }
    return acceptNoActionForm ? noActionForm : null;
  }

  static String reformatComment(String comment) {
    if (comment == null)
      return null;
    String wrapped;
    if (BugzillaHTMLConstants.BUGZILLA_COMMENT_NOWRAP) wrapped = comment;
    else {
      int length = BugzillaHTMLConstants.BUGZILLA_COMMENT_LINE;
      if (length <= 0) length = 80;
      wrapped = TextUtil.hardWrap(comment.trim(), length);
      assert wrapped != null;
    }
    return wrapped.trim();
  }

  public static String replaceXmlEntities(String source, boolean replaceHtmlEntities, boolean fix214) {
    String result;
    if (fix214) {
      result = JDOMUtils.replaceXmlEntities(source, replaceHtmlEntities, true, false);
      result = JDOMUtils.replaceXmlEntities(result, replaceHtmlEntities, false, true);
    } else {
      result = JDOMUtils.replaceXmlEntities(source, replaceHtmlEntities);
    }
    return result;
  }

  /**
   * Counts the number of title elements like <title> or <h1> that contain one of the given words
   */
  public static int searchTitles(Element root, String[] strings) {
    int count = 0;
    for (Element title : JDOMUtils.searchElements(root, TITLES)) {
      String value = Util.upper(JDOMUtils.getTextTrim(title));
      for (String string : strings) {
        if (value.indexOf(Util.upper(string)) >= 0) {
          count++;
          break;
        }
      }
    }
    return count;
  }

  static List<NameValuePair> getUnambiguousMatchRequestParameters(Document document, boolean forSubmit)
    throws AmbiguousUserMatchException
  {
    Element root = document.getRootElement();
    int weight = searchTitles(root, new String[] {"confirm match"});
    if (weight == 0)
      return null;
    Element form = forSubmit ? findSubmitFormElement(root, true) : findUpdateFormElement(root, true);
    if (form == null)
      return null;
    List<Element> selects = JDOMUtils.searchElements(form, "select");
    int size = selects.size();
    if (size > 0) {
      StringBuffer whine = new StringBuffer(size > 1 ? "ambiguous values: " : "ambiguous value: ");
      for (int i = 0; i < size; i++) {
        Element select = selects.get(i);
        String value = JDOMUtils.getAttributeValue(select, "name", "unknown", false);
        if (i > 0)
          whine.append(", ");
        whine.append(value);
      }
      throw new AmbiguousUserMatchException(whine.toString());
    }
    List<Pair<String, String>> formParameters = HtmlUtils.extractDefaultFormParameters(form).toPairList();
    final List<NameValuePair> fixedParameters = HtmlUtils.PAIR_TO_NVP.collectList(formParameters);
    return fixedParameters;
  }

  static List<Pair<BugGroupData, Boolean>> findGroupInfo(Element form) {
    final List<Pair<BugGroupData, Boolean>> result = Collections15.arrayList();
    final Iterator<Element> ii = JDOMUtils.searchElementIterator(form, "input", "type", "checkbox");
    while (ii.hasNext()) {
      final Element checkbox = ii.next();
      final String id = extractGroupId(checkbox);
      if (id != null) {
        final String description = extractGroupDescr(checkbox);
        if (description != null && !description.isEmpty()) {
          final String name = extractGroupName(checkbox, id);
          final boolean selected = JDOMUtils.getAttributeValue(checkbox, "checked", null, false) != null;
          result.add(Pair.create(new BugGroupData(id, name, description), selected));
        }
      }
    }
    return result;
  }

  private static String extractGroupId(Element checkbox) {
    // pre-4.0
    final String name = JDOMUtils.getAttributeValue(checkbox, "name", null, false);
    if(name != null && name.startsWith(BugzillaHTMLConstants.GROUP_ID_PREFIX)) {
      return name;
    }

    // BZ 4.0
    final String id = JDOMUtils.getAttributeValue(checkbox, "id", null, false);
    if(id != null && id.startsWith(BugzillaHTMLConstants.GROUP_ID_PREFIX_BZ40)) {
      return id;
    }

    return null;
  }

  private static String extractGroupDescr(Element checkbox) {
    Parent parent = checkbox.getParent();
    int index = parent.indexOf(checkbox);
    if (index < 0)
      return null;
    for (index++; index < parent.getContentSize(); index++) {
      Content c = parent.getContent(index);
      if (c instanceof Text) {
        String v = JDOMUtils.convertSmartUnicodeChars(((Text) c).getText()).trim();
        if (v.length() > 0)
          return v;
      } else if (c instanceof Element) {
        Element e = (Element) c;
        String eName = e.getName();
        if ("label".equalsIgnoreCase(eName)) {
          return JDOMUtils.getTextTrim(e);
        } else if (!("br".equalsIgnoreCase(eName) || "b".equalsIgnoreCase(eName) || "i".equalsIgnoreCase(eName) ||
          "u".equalsIgnoreCase(eName)))
        {
          break;
        }
      }
    }
    return null;
  }

  private static String extractGroupName(Element checkbox, String id) {
    if(id.startsWith(BugzillaHTMLConstants.GROUP_ID_PREFIX_BZ40)) {
      final String name = JDOMUtils.getAttributeValue(checkbox, "value", null, false);
      assert name != null : id;
      return name;
    }
    return null;
  }

  /**
   * @return null if comment privacy info is not found, or list of (sequence, comment text, is_private).
   */
  @Nullable
  public static List<FrontPageData.CommentInfo> findCommentInfo(Element form, TimeZone defaultTimezone,
    boolean[] format32)
  {
    List<FrontPageData.CommentInfo> result = null;
    Iterator<Element> ii = JDOMUtils.searchElementIterator(form, "input", "type", "checkbox");
    int sequence = 0;
    while (ii.hasNext()) {
      Element checkbox = ii.next();
      String name = JDOMUtils.getAttributeValue(checkbox, "name", null, false);
      if (name == null)
        continue;
      Matcher matcher = ISPRIVATE_PATTERN.matcher(name);
      if (!matcher.matches()) {
        continue;
      }
      int commentNumber = Util.toInt(matcher.group(1), -1);
      if (commentNumber < 0) {
        continue;
      }
      if (matcher.group(0).startsWith("isprivate_"))
        format32[0] = true;
      boolean privacy = JDOMUtils.getAttributeValue(checkbox, "checked", null, false) != null;

      String when = null;
      Element commentDiv = findCommentDivUp(checkbox);
      Element whenInput = JDOMUtils.searchElement(form, "input", "name", "when-" + commentNumber);
      if (whenInput != null) {
        when = JDOMUtils.getAttributeValue(whenInput, "value", null, true);
      } else {
        Element head = findCommentHeadSpanDown(commentDiv);
        when = findCommentDateInHeadSpan(head);
      }
      if (when == null) {
        Log.warn("cannot get comment date [" + commentNumber + "]");
        continue;
      }
      Date date = BugzillaDateUtil.parseOrWarn(when, null, defaultTimezone);

      Element textPre = findCommentTextPre(form, commentDiv, commentNumber);
      if (textPre == null) {
        Log.warn("cannot find comment text for #" + commentNumber);
        continue;
      }
      String text = JDOMUtils.getTextTrim(textPre);
      if (result == null)
        result = Collections15.arrayList();
      result.add(
        new FrontPageData.CommentInfo(sequence, commentNumber, text, date == null ? 0 : date.getTime(), privacy));
      sequence++;
    }
    return result;
  }

  private static Element findCommentTextPre(Element form, Element commentDiv, int sequence) {
    // bz 3.2+
    if (commentDiv != null) {
      Iterator<Element> ii = JDOMUtils.searchElementIterator(commentDiv, "pre");
      while (ii.hasNext()) {
        Element e = ii.next();
        String id = JDOMUtils.getAttributeValue(e, "id", null, false);
        if (id != null && id.startsWith("comment_text_"))
          return e;
      }
    }
    // fall back to old version (in 3.2 sequence is not sequential actually)
    return JDOMUtils.searchElement(form, "pre", "id", "comment_text_" + sequence);
  }

  private static String findCommentDateInHeadSpan(Element span) {
    if (span == null)
      return null;
    String headText = JDOMUtils.getTextTrim(span);
    Matcher matcher = COMMENT_DATE_PATTERN.matcher(headText);
    return matcher.find() ? matcher.group(0) : null;
  }

  private static Element findCommentHeadSpanDown(Element commentDiv) {
    if (commentDiv == null)
      return null;
    Iterator<Element> ii = JDOMUtils.searchElementIterator(commentDiv, null);
    while (ii.hasNext()) {
      Element e = ii.next();
      String name = e.getName();
      if (!"div".equalsIgnoreCase(name) && !"span".equalsIgnoreCase(name))
        continue;
      String eClass = JDOMUtils.getAttributeValue(e, "class", "", false);
      if (COMMENT_HEAD_CLASS_PATTERN.matcher(eClass).find())
        return e;
    }
    return null;
  }

  private static Element findCommentDivUp(Element element) {
    Element div = element;
    while (true) {
      div = JDOMUtils.getAncestor(div, "div");
      if (div == null)
        break;
      String divClass = JDOMUtils.getAttributeValue(div, "class", "", false);
      if (COMMENT_DIV_CLASS_PATTERN.matcher(divClass).find())
        return div;
    }
    return null;
  }

  static Pair<Map<String, CustomFieldInfo>, MultiMap<String, String>> findCustomFieldInfoAndValues(Element form,
    boolean submit)
  {
    Map<String, CustomFieldInfo> cfinfo = null;
    MultiMap<String, String> cfvalues = null;

    JDOMElementIterator ii = new JDOMElementIterator(form);
    int order = 1;
    while (true) {
      Element label = ii.next("label");
      if (label == null)
        break;
      String fieldId = JDOMUtils.getAttributeValue(label, "for", null, true);
      if (fieldId == null || !fieldId.startsWith("cf_"))
        continue;
      String displayName = JDOMUtils.getTextTrim(label);
      if (displayName.endsWith(":"))
        displayName = displayName.substring(0, displayName.length() - 1);
      ii.skipLastElementContent();

      Element input = findInput(ii, fieldId);
      if (input == null) {
        Log.warn("cannot find field " + fieldId + " (" + displayName + ")");
        continue;
      }

      // todo refactor this part
      if (cfvalues == null)
        cfvalues = MultiMap.create();
      CustomFieldType type;
      List<String> options = null;
      String inputType = input.getName();
      if ("input".equalsIgnoreCase(inputType)) {
        String onchange = Util.lower(JDOMUtils.getAttributeValue(input, "onchange", "", true));
        if (onchange.length() > 0 && onchange.contains("calendar")) {
          type = CustomFieldType.DATE_TIME;
        } else {
          // check if it is ID field
          int textSize = Util.toInt(JDOMUtils.getAttributeValue(input, "size", "", false), -1);
          if (textSize == 7) {
            // magic width for ID fields
            // second check: see if there "edit" js link
            Element td = JDOMUtils.getAncestor(input, "td");
            Element editAction = JDOMUtils.searchElement(td, "a", "id", fieldId + "_edit_action");
            if (editAction != null) {
              // found!
              type = CustomFieldType.BUG_ID;
            } else {
              type = CustomFieldType.TEXT;
            }
          } else {
            type = CustomFieldType.TEXT;
          }
        }
        cfvalues.add(fieldId, JDOMUtils.getAttributeValue(input, "value", "", true));
      } else if ("textarea".equalsIgnoreCase(inputType)) {
        type = CustomFieldType.LARGE_TEXT;
        cfvalues.add(fieldId, JDOMUtils.getText(input));
      } else if ("select".equalsIgnoreCase(inputType)) {
        if (HtmlUtils.isMultipleSelect(input)) {
          type = CustomFieldType.MULTI_SELECT;
        } else {
          type = CustomFieldType.CHOICE;
        }
        options = HtmlUtils.getSelectOptionValues(input);
        ExtractFormParameters.DEFAULT.processSelect(input, cfvalues);
      } else {
        assert false : fieldId + ":" + displayName;
        Log.warn("cannot find inputs for custom field " + fieldId + ":" + displayName);
        continue;
      }
      if (cfinfo == null) {
        cfinfo = Collections15.linkedHashMap();
      }
      CustomFieldInfo cfi =
        new CustomFieldInfo(fieldId, displayName, type, order++, submit ? Boolean.TRUE : null, options);
      cfinfo.put(fieldId, cfi);
    }

    return cfinfo == null && cfvalues == null ? null : Pair.create(cfinfo, cfvalues);
  }

  @Nullable
  static Element findInput(JDOMElementIterator ii, String fieldId) {
    JDOMElementIterator jj = ii.duplicate();
    Element input;
    while ((input = jj.next()) != null) {
      if ("input".equalsIgnoreCase(input.getName()) &&
        fieldId.equals(JDOMUtils.getAttributeValue(input, "name", "", true)))
      {
        String inputType = JDOMUtils.getAttributeValue(input, "type", null, false);
        if (inputType != null && !inputType.equalsIgnoreCase("text")) {
          Log.warn("unknown input type: " + inputType + " (" + input + ")");
          continue;
        }
        break;
      }
      if ("select".equalsIgnoreCase(input.getName()) &&
        fieldId.equals(JDOMUtils.getAttributeValue(input, "name", "", true)))
      {
        break;
      }
      if ("textarea".equalsIgnoreCase(input.getName()) &&
        fieldId.equals(JDOMUtils.getAttributeValue(input, "name", "", true)))
      {
        break;
      }
    }
    return input;
  }

  public static List<String> findKnobs(Element updateForm) {
    List<String> r = Collections15.arrayList();
    Iterator<Element> ii = JDOMUtils.searchElementIterator(updateForm, "input", "name", "knob");
    while (ii.hasNext()) {
      Element e = ii.next();
      String type = JDOMUtils.getAttributeValue(e, "type", null, false);
      if (!"radio".equalsIgnoreCase(type) && !"hidden".equalsIgnoreCase(type)) {
        Log.warn("bug update form contains knob input type " + type);
      }
      String value = JDOMUtils.getAttributeValue(e, "value", null, true);
      if (value != null) {
        r.add(value);
      }
    }
    return r;
  }

  /**
   * In Bugzilla 3.1 there might be different fields for resolution -- one for each status. The requested resolution
   * should be propagated to all of them.
   */
  public static void propagateResolutions31(MultiMap<String, String> parameters) {
    if (!parameters.remove(PROPAGATE_RESOLUTIONS, PROPAGATE_RESOLUTIONS))
      return;
    String resolution = parameters.getLast("resolution");
    if (resolution == null) {
      assert false : parameters;
      return;
    }
    for (String key : Collections15.arrayList(parameters.keySet())) {
      if (key.startsWith("resolution_knob_")) {
        parameters.replaceAll(key, resolution);
      }
    }
  }

  static boolean isCommentSubmitted(String text, BugInfo bugInfo, long lastUpdateAttemptTime, String username) {
    String textIdentity = null;
    Comment[] comments = bugInfo.getOrderedComments();
    for (int i = comments.length - 1; i >= 0; i--) {
      Comment comment = comments[i];
      if (!Util.equals(comment.getWhoEmail(), username)) {
        // encountered comment from another user - stop check
        return false;
      }
      Date date = comment.getWhenDate();
      if (date.getTime() - lastUpdateAttemptTime < -COMMENT_CHECK_TOLERANCE) {
        // comment appeared earlier than update began - stop check
        return false;
      }
      String commentIdentity = TextUtil.getSoftStringForComparison(comment.getText());
      if (textIdentity == null)
        textIdentity = TextUtil.getSoftStringForComparison(text);
      if (textIdentity.equals(commentIdentity)) {
        Log.warn("this comment is already submitted: " + StringUtil.limitString(text, 40));
        return true;
      }
    }
    return false;
  }

  public static List<BugzillaUser> extractShortUsersFromListOptions(Element list, String emailSuffix) {
    List<BugzillaUser> result = Collections15.arrayList();
    Iterator<Element> ii = JDOMUtils.searchElementIterator(list, "option");
    while (ii.hasNext()) {
      BugzillaUser user = extractShortUserFromOption(ii.next(), emailSuffix);
      if (user != null) result.add(user);
    }
    return result;
  }

  public static BugzillaUser extractShortUserFromOption(Element option, String emailSuffix) {
    String email = JDOMUtils.getAttributeValue(option, "value", null, false);
    String name = JDOMUtils.getText(option);
    int emailIndex = name.indexOf("<" + email + ">");
    if (emailIndex >= 0) name = name.substring(0, emailIndex).trim();
    BugzillaUser user = BugzillaUser.shortEmailName(email, name, emailSuffix);
    return user;
  }

  public static Collection<String> removeEmailSuffix(Collection<String> fullEmails, String suffix) {
    if (fullEmails == null || fullEmails.isEmpty()) return fullEmails;
    if (suffix == null || suffix.length() == 0) return fullEmails;
    List<String> result = Collections15.arrayList(fullEmails.size());
    for (String email : fullEmails) {
      if (email != null && email.endsWith(suffix)) email = email.substring(0, email.length() - suffix.length());
      result.add(email);
    }
    return result;
  }
}