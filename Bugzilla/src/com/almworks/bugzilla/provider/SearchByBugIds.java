package com.almworks.bugzilla.provider;

import com.almworks.api.application.ItemSource;
import com.almworks.api.application.tree.GenericNode;
import com.almworks.api.constraint.*;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.Engine;
import com.almworks.api.search.*;
import com.almworks.api.syncreg.SyncCubeRegistry;
import com.almworks.api.syncreg.SyncRegistry;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.DP;
import com.almworks.items.dp.DPCompare;
import com.almworks.items.dp.DPEquals;
import com.almworks.util.Pair;
import com.almworks.util.Terms;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.i18n.Local;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;

public class SearchByBugIds implements TextSearchType {
  private final Engine myEngine;
  private final SyncCubeRegistry myCubeRegistry;

  public SearchByBugIds(Engine engine, SyncRegistry registry) {
    myEngine = engine;
    myCubeRegistry = registry.getSyncCubeRegistry();
  }

  @Nullable
  public TextSearchExecutor parse(String searchString) {
    if (searchString == null)
      return null;
    searchString = searchString.trim();
    if (searchString.length() == 0)
      return null;
    try {
      return parseForSpec(searchString).createSearch();
    } catch (ParseException e) {
      return null;
    }
  }

  private SearchBuilder parseForSpec(String searchString) throws ParseException {
    SearchBuilder builder = new SearchBuilder(myEngine, myCubeRegistry, this);
    String[] commaDelimited = searchString.split("[\\,\\;\\s]+");
    for (String ea : commaDelimited) {
      builder.addSearch(ea);
    }
    return builder;
  }

  public String getDisplayableShortName() {
    return "id";
  }

  public int getWeight() {
    return TextSearchType.Weight.ID_SEARCH;
  }

  public static class SearchBuilder {
    private final Engine myEngine;
    private final SyncCubeRegistry myCubeRegistry;
    private final SearchByBugIds mySearchType;
    private final List<Pair<Integer, Integer>> mySearch = Collections15.arrayList();

    public SearchBuilder(Engine engine, SyncCubeRegistry cubeRegistry, SearchByBugIds searchType) {
      myEngine = engine;
      myCubeRegistry = cubeRegistry;
      mySearchType = searchType;
    }

    @Nullable
    public TextSearchExecutor createSearch() {
      return mySearch.isEmpty() ? null : new Executor(myEngine, myCubeRegistry, mySearchType, mySearch);
    }

    public void addSearch(String ea) throws ParseException {
      int rangeDelimiter = ea.indexOf('-');
      if (rangeDelimiter < 0) {
        int id = parseForId(ea);
        mySearch.add(Pair.create(id, (Integer) null));
      } else {
        int from = parseForId(ea.substring(0, rangeDelimiter));
        int to = parseForId(ea.substring(rangeDelimiter + 1));
        mySearch.add(Pair.create(from, to));
      }
    }

    private int parseForId(String string) throws ParseException {
      String s = string.trim();
      if (s.length() > 0 && s.charAt(0) == '#')
        s = s.substring(1).trim();
      if (s.length() == 0)
        throw new ParseException(string, 0);
      int v = Util.toInt(s, -1);
      if (v <= 0 || v >= 100000000)
        throw new ParseException(string, 0);
      return v;
    }
  }

  private static class Executor extends FilterBasedSearchExecutor {
    private final List<Pair<Integer, Integer>> mySpec;
    private String myDescription;
    private final SearchByBugIds mySearchType;

    public Executor(Engine engine, SyncCubeRegistry cubeRegistry, SearchByBugIds searchType, List<Pair<Integer, Integer>> spec) {
      super(engine, cubeRegistry);
      mySearchType = searchType;
      assert spec != null && spec.size() > 0 : spec;
      mySpec = spec;
    }


    @Override
    @NotNull
    public ItemSource executeSearch(Collection<? extends GenericNode> scope) {
      Collection<GenericNode> nodes = TextSearchUtils.escalateToConnectionNodes(scope);
      return super.executeSearch(nodes);
    }

    @Override
    @Nullable
    protected BoolExpr<DP> getFilter(Connection connection) {
      final List<BoolExpr<DP>> ors = Collections15.arrayList();

      for(final Pair<Integer, Integer> range : mySpec) {
        final Integer from = range.getFirst();
        final Integer to = range.getSecond();
        if(from == null) {
          assert false : mySpec;
          continue;
        }

        if(to == null) {
          ors.add(DPEquals.create(Bug.attrBugID, from));
        } else {
          ors.add(BoolExpr.and(
            DPCompare.greaterOrEqual(Bug.attrBugID, from, false),
            DPCompare.lessOrEqual(Bug.attrBugID, to, false)));
        }
      }

      return ors.isEmpty() ? null : BoolExpr.or(ors);
    }

    @Override
    @Nullable
    public Constraint getConstraint(Connection connection) {
      List<Constraint> constraints = Collections15.arrayList();
      for (Pair<Integer, Integer> range : mySpec) {
        assert range != null;
        Integer from = range.getFirst();
        Integer to = range.getSecond();
        if (from == null) {
          assert false : mySpec;
          continue;
        }
        if (to == null) {
          constraints.add(FieldIntConstraint.Simple.equals(Bug.attrBugID, BigDecimal.valueOf(from)));
        } else {
          constraints.add(CompositeConstraint.Simple.and(
            FieldIntConstraint.Simple.greaterOrEqual(Bug.attrBugID, BigDecimal.valueOf(from)),
            FieldIntConstraint.Simple.lessOrEqual(Bug.attrBugID, BigDecimal.valueOf(to))));
        }
      }
      if (constraints.size() == 0) {
        return null;
      } else {
        return CompositeConstraint.Simple.or(constraints);
      }
    }

    @Override
    @NotNull
    public TextSearchType getType() {
      return mySearchType;
    }

    @Override
    @NotNull
    public String getSearchDescription() {
      if (myDescription == null)
        myDescription = createDescription();
      return myDescription;
    }

    private String createDescription() {
      StringBuilder result = new StringBuilder();
      boolean multiple = mySpec.size() > 1;
      for (Pair<Integer, Integer> pair : mySpec) {
        Integer first = pair.getFirst();
        Integer second = pair.getSecond();
        assert first != null : pair;
        if (result.length() > 0) {
          result.append(',');
        }
        if (second == null) {
          result.append(first);
        } else {
          result.append(first).append('-').append(second);
          multiple = true;
        }
      }
      if (multiple) {
        result.insert(0, " ## ").insert(0, Local.parse(Terms.ref_Artifacts));
      } else {
        result.insert(0, " #").insert(0, Local.parse(Terms.ref_Artifact));
      }
      return result.toString();
    }

    @NotNull
    public Collection<GenericNode> getRealScope(@NotNull Collection<GenericNode> nodes) {
      return TextSearchUtils.escalateToConnectionNodes(nodes);
    }
  }
}
