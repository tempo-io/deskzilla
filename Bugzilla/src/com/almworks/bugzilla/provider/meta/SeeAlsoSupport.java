package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.api.application.tree.GenericNode;
import com.almworks.api.explorer.ItemUrlService;
import com.almworks.api.search.TextSearch;
import com.almworks.api.search.TextSearchExecutor;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.engine.gui.*;
import com.almworks.util.Terms;
import com.almworks.util.commons.Function2;
import com.almworks.util.components.renderer.RendererContext;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.CantPerformException;

import java.util.*;

import static com.almworks.bugzilla.integration.BugzillaAttribute.SEE_ALSO;
import static com.almworks.bugzilla.provider.meta.BugzillaKeys.seeAlso;

public class SeeAlsoSupport {
  static void addLeftField(ItemTableBuilder builder) {
    builder.addStringList(BugzillaMetaInfo.label(SEE_ALSO), seeAlso, true, null,
      Collections.<CellAction>singletonList(
        new CellAction(Icons.SEARCH_SMALL, "View related " + Local.parse(Terms.ref_artifacts)) {
          @Override
          public void perform(ActionContext context) throws CantPerformException {
            RendererContext rc = context.getSourceObject(RendererContext.RENDERER_CONTEXT);
            ModelMap modelMap = rc.getValue(LeftFieldsBuilder.MODEL_MAP);
            List<String> v = modelMap != null ? seeAlso.getValue(modelMap) : null;
            if (v == null || v.isEmpty())
              return;
            ItemUrlService.SearchBuilder builder = context.getSourceObject(ItemUrlService.ROLE).createSearchBuilder();
            for (String url : v)
              builder.addUrl(url);
            TextSearchExecutor executor = builder.createExecutor();
            if (executor == null)
              return;
            ExplorerComponent explorer = context.getSourceObject(ExplorerComponent.ROLE);
            Collection<GenericNode> nodes = Collections.<GenericNode>singleton(explorer.getRootNode());
            ItemSource source = executor.executeSearch(nodes);
            nodes = executor.getRealScope(nodes);
            ItemCollectionContext collectionContext =
              ItemCollectionContext.createTextSearch(executor.getSearchDescription(), nodes,
                TextSearch.SMART_SEARCH_KEY);
            explorer.showItemsInTab(source, collectionContext, true);
          }
        }), new Function2<ModelMap, String, String>() {
      @Override
      public String invoke(ModelMap modelMap, String value) {
        BugzillaConnection connection = BugzillaConnection.getInstance(modelMap);
        if (connection == null) return value;
        Integer bugId = connection.getBugIdFromUrl(value);
        return bugId != null ? String.valueOf(bugId) : value;
      }
    });
  }
}
