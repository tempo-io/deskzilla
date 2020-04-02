package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.*;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.provider.*;
import com.almworks.items.api.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.collections.Convertor;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.Set;

public abstract class ReferenceArrayLink<C extends Collection<Long>> extends ReferenceLink<C, String> {
  public ReferenceArrayLink(DBAttribute<C> attribute, BugzillaAttribute bugzillaAttribute,
    DBItemType referentType, DBAttribute<String> referentUniqueKey, DBAttribute<String> referentVisualKey)
  {
    super(attribute, bugzillaAttribute, referentType, referentUniqueKey, referentVisualKey, true, null, true);
  }

  public void updateRevision(PrivateMetadata privateMetadata, ItemVersionCreator bugCreator, BugInfo bugInfo, @NotNull BugzillaContext context) {
    if(cannotTell(bugInfo)) return;
    final Collection<String> newKeys = extractStrings(bugInfo);
    final C items = createContainer();
    for(final String newKey : newKeys) {
      long referent = getOrCreateReferent(privateMetadata, newKey, bugCreator);
      if(referent > 0) {
        items.add(referent);
      } else {
        assert false : newKey;
      }
    }
    bugCreator.setValue(getAttribute(), items);
  }

  protected Collection<String> extractStrings(BugInfo bugInfo) {
    return bugInfo.getValues().getTupleValues(myBugzillaAttribute);
  }

  protected abstract C createContainer();

  public boolean cannotTell(BugInfo bugInfo) {
    return false;
  }

  @Override
  protected String createRemoteString(C value, ItemVersion lastServerVersion) {
    // used *only* by AbstractAttributeLink's implementation of buildUploadInfo(),
    // which is overridden in subclasses, so this method should never be called.
    assert false;
    return null;
  }

  public String getBCElementParameter(OneFieldConstraint constraint, DBReader reader, BugzillaConnection connection) {
    final FieldSubsetConstraint subset = Constraints.cast(FieldSubsetConstraint.INTERSECTION, constraint);
    if(subset == null) {
      return super.getBCElementParameter(constraint, reader, connection);
    }

    final Set<Long> referents = subset.getSubset();
    if(referents == null || referents.isEmpty()) {
      return null;
    }

    final StringBuilder builder = new StringBuilder();
    for(final Long referent : referents) {
      if(referent == null || referent <= 0) {
        continue;
      }
      try {
        String string = getLocalString(SyncUtils.readTrunk(reader, referent), connection);
        if(string == null || string.equalsIgnoreCase(myDefaultValue)) {
          continue;
        }
        string = string.trim();
        if(string.isEmpty() || string.equalsIgnoreCase(myDefaultValue)) {
          continue;
        }
        if(builder.length() > 0) {
          builder.append(' ');
        }
        builder.append(string);
      } catch (BadReferent e) {
        Log.warn(e);
      }
    }
    return builder.length() == 0 ? null : builder.toString();
  }

  protected class UniqueConvertor extends Convertor<Long, String> {
    private final DBReader myReader;

    public UniqueConvertor(DBReader reader) {
      myReader = reader;
    }

    @Override
    public String convert(Long item) {
      return myReader.getValue(item, getReferentUniqueKey());
    }
  }
}
