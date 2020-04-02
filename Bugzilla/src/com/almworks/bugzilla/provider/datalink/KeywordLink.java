package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.application.BadItemException;
import com.almworks.api.application.ResolvedFactory;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.BugzillaProvider;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.util.DBNamespace;
import com.almworks.util.text.TextUtil;
import org.almworks.util.Collections15;
import org.almworks.util.Util;

import java.util.*;

public class KeywordLink extends ReferenceArrayLink<Set<Long>> implements ResolvedFactory<KeywordKey> {
  public static final DBNamespace NS = BugzillaProvider.NS.subNs("keyword");
  private static final DBItemType typeKeyword = NS.type();
  public static final DBAttribute<String> attrDisplay = NS.string("keywordDisplay", "Keyword", false);
  public static final DBAttribute<String> attrId = NS.string("keywordId", "Keyword ID", false);
  public static final DBAttribute<String> attrDescription = NS.string("description", "Description", false);
  public static final DBAttribute<Boolean> attrSynced = NS.bool("synchronized", "Synchronized?", false);

  public KeywordLink(DBAttribute<Set<Long>> attribute) {
    super(attribute, BugzillaAttribute.KEYWORDS, typeKeyword, attrId, attrDescription);
  }

  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) {
    final DBAttribute<Set<Long>> attribute = getWorkspaceAttribute();

    final Set<Long> lastValue = Util.NN(bug.getElderValue(attribute), Collections15.<Long>emptySet());
    final Set<String> lastKeywords = new UniqueConvertor(bug.getReader()).collectSet(lastValue);
    info.getPrevValues().reput(getBugzillaAttribute(), TextUtil.separate(lastKeywords, " "));

    if(bug.isChanged(attribute)) {
      final Set<Long> v = Util.NN(bug.getNewerValue(attribute), Collections15.<Long>emptySet());
      final Set<String> newKeywords = new UniqueConvertor(bug.getReader()).collectSet(v);
      info.getNewValues().reput(getBugzillaAttribute(), TextUtil.separate(newKeywords, " "));
    }
  }          

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    BugzillaAttribute attribute = getBugzillaAttribute();
    String requestedValue = (String) updateInfo.getNewValues().getValue(attribute);
    if (requestedValue == null)
      return null;
    String newValue = (String) newInfo.getValues().getValue(attribute);

    String reqString = Util.NN(requestedValue, "");
    String[] requested = TextUtil.getKeywords(reqString);
    String recievedString = Util.NN(newValue, "");
    String[] received = TextUtil.getKeywords(recievedString);

    Arrays.sort(received, String.CASE_INSENSITIVE_ORDER);
    Arrays.sort(requested, String.CASE_INSENSITIVE_ORDER);

    if (Arrays.deepEquals(requested, received))
      return null;

    return detectFailedUpdateString(privateMetadata, attribute, reqString, recievedString);
  }

  @Override
  public ItemProxy createProxy(PrivateMetadata pm, String keyValue) {
    return new EnumItemProxy<String>(pm, keyValue, this) {
      @Override
      protected ItemVersionCreator createItem(DBDrain drain) {
        ItemVersionCreator creator = super.createItem(drain);
        creator.setValue(attrDisplay, getKeyValue());
        return creator;
      }
    };
  }

  @Override
  protected Collection<String> extractStrings(BugInfo bugInfo) {
    return Collections15.hashSet(TextUtil.getKeywords(bugInfo.getValues().getScalarValue(myBugzillaAttribute, null)));
  }

  @Override
  protected Set<Long> createContainer() {
    return Collections15.hashSet();
  }

  public KeywordKey createResolvedItem(long item, DBReader reader) throws BadItemException {
    return KeywordKey.keyword(item, reader);
  }
}
