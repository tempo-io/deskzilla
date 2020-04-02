package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.api.application.util.*;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.KeywordKey;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.util.commons.Condition;
import com.almworks.util.commons.Function;
import com.almworks.util.properties.PropertyMap;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.almworks.bugzilla.provider.meta.BugzillaKeys.*;
import static org.almworks.util.Collections15.*;

public class BugzillaVerifierManager extends VerifierManager.AbstractVerifierManager {
  private final List<ModelKeyVerifier> myVerifiers = arrayList();
  private final Map<ModelKey<?>, ModelKeyVerifier> myVerifiersMap = linkedHashMap();

  public BugzillaVerifierManager() {
    myVerifiers.add(put(summary, NotEmptyMkVerifier.forStringKey(summary)));
    myVerifiers.add(put(keywords, createKeywordsVerifier()));
    CompositeDependentMkVerifier byProject = new CompositeDependentMkVerifier(product);
    myVerifiers.add(byProject
      .add(put(component, new ProductDependentMkVerifier(product, component, BugzillaAttribute.COMPONENT)))
      .add(put(version, new ProductDependentMkVerifier(product, version, BugzillaAttribute.VERSION)))
      .add(put(milestone, new ProductDependentMkVerifier(product, milestone, BugzillaAttribute.TARGET_MILESTONE)))
    );
  }

  private ModelKeyVerifier put(ModelKey<?> mk, ModelKeyVerifier ver) {
    if(myVerifiersMap.put(mk, ver) != null) {
      assert false : ver + " " + mk;
    }
    return ver;
  }

  private static ModelKeyVerifier createKeywordsVerifier() {
    return AllowedRangeVerifier.create(keywords, new Function<PropertyMap, Set<ResolvedItem>>() {
      @Nullable
      @Override
      public Set<ResolvedItem> invoke(PropertyMap map) {
        LoadedItemServices lis = LoadedItemServices.VALUE_KEY.getValue(map);
        BugzillaConnection connection = lis == null ? null : lis.getConnection(BugzillaConnection.class);
        if (connection == null) {
          // treat everything as legal
          return null;
        }
        BugzillaContext context = connection.getContext();
        if (context == null) return null;
        CommonMetadata metadata = context.getMetadata();
        BaseEnumConstraintDescriptor type = metadata.getEnumDescriptor(BugzillaAttribute.KEYWORDS);
        return Condition.<KeywordKey, ResolvedItem>cast(KeywordKey.SYNCHRONIZED, KeywordKey.class).filterSet(hashSet(type.getAllValues(connection)));
      }
    }, ItemKey.DISPLAY_NAME, "unknown");
  }

  @NotNull
  @Override
  public Collection<ModelKeyVerifier> getVerifiers() {
    return Collections.unmodifiableCollection(myVerifiers);
  }

  @Override
  public ModelKeyVerifier getVerifier(@NotNull ModelKey<?> key) {
    return myVerifiersMap.get(key);
  }

  @Override
  protected Set<ModelKey<?>> verifiedKeys() {
    return myVerifiersMap.keySet();
  }
}
