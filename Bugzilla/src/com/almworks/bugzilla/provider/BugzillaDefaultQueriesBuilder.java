package com.almworks.bugzilla.provider;

import com.almworks.api.engine.Connection;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.datalink.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.items.api.*;
import com.almworks.spi.provider.DefaultQueriesBuilderSupport;
import com.almworks.util.commons.LongObjFunction2;
import com.almworks.util.text.parser.FormulaWriter;
import com.almworks.util.threads.Threads;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.util.List;

public class BugzillaDefaultQueriesBuilder extends DefaultQueriesBuilderSupport {
  private static final List<String> OPEN = Collections15.arrayList(
    "UNCONFIRMED", "NEW", "CONFIRMED", "ASSIGNED", "IN_PROGRESS", "REOPENED", "NEEDINFO");

  private final BugzillaContextImpl myContext;

  public BugzillaDefaultQueriesBuilder(BugzillaContextImpl context) {
    super();
    myContext = context;
  }

  public final String buildXML() {
    Element xml = new Element("defaultQueries");
    Element root = createFolder(xml, "Sample Queries");
    root.addContent(new Element("hideEmptyChildren").addContent("true"));
    createQuery(root, "All Bugs", "");
    String username = getUsername();
    if (username != null) {
      String usernameQuoted = FormulaWriter.quoteIfNeeded(username);
      Element assigned = createQuery(root, "Assigned to Me and Open",
        "(\"assigned_to\" in (" + usernameQuoted + ")) & (" + getStatusesFormula(OPEN) + ")");
      DefaultQueriesBuilderSupport.createDistributionNode(assigned, "Priority", "priority", true);
      Element reported = createQuery(root, "Reported by Me", "(reporter in (" + usernameQuoted + "))");
      DefaultQueriesBuilderSupport.createDistributionNode(reported, "Status", "status", true);
    }
    createQuery(root, "Updated Today", "(\"modification_timestamp\" during d 0 0)");
    if (username != null && myContext.getConnection().hasFlags()) {
      String usernameQuoted = FormulaWriter.quoteIfNeeded(username);
      Element requests = createFolder(root, "Requests");
      createQuery(requests, "I Requested", "(flags ((flagStatuses (\"?\"))&(flagSetters intersects (" + usernameQuoted + " ))))");
      createQuery(requests, "I Was Requested", "flagRequestees intersects (" + usernameQuoted +  " )");
    }
    return new XMLOutputter(Format.getPrettyFormat()).outputString(xml);
  }

  private String getUsername() {
    final OurConfiguration config = myContext.getConfiguration().getValue();
    if(config == null || config.isAnonymousAccess()) {
      return null;
    }

    String username = Database.require().readBackground(new ReadTransaction<String>() {
      @Override
      public String transaction(DBReader reader) throws DBOperationCancelledException {
        final long connection = reader.findMaterialized(myContext.getPrivateMetadata().thisConnection);
        if(connection > 0L) {
          final long user = reader.getValue(connection, Connection.USER);
          if(user > 0L) {
            return reader.getValue(user, User.EMAIL);
          }
        }
        return null;
      }
    }).waitForCompletion();

    if(username == null) {
      username = Util.NN(config.getUsername()).trim();
    }
    if(username.isEmpty()) {
      return null;
    }
    return username;
  }

  private String getStatusesFormula(List<String> limitList) {
    List<String> statuses = getStatuses(limitList);
    if (statuses.size() == 0)
      return null;
    return "status in (" + stackValues(statuses) + ")";
  }

  private List<String> getStatuses(List<String> limitList) {
    List<String> statuses = UPPERCASE.collectList(loadStatusValues());
    statuses.retainAll(limitList);
    return statuses;
  }

  private List<String> loadStatusValues() {
    Threads.assertLongOperationsAllowed();

    BugzillaAttributeLink attributeLink = CommonMetadata.ATTR_TO_LINK.get(BugzillaAttribute.STATUS);
    if (attributeLink == null)
      return Collections15.emptyList();

    if (!(attributeLink instanceof ReferenceLink) || (attributeLink instanceof ReferenceArrayLink)) {
      // do not handle arrays and not references
      return Collections15.emptyList();
    }

    final ReferenceLink referenceLink = (ReferenceLink) attributeLink;
    final DBFilter view = referenceLink.getReferentsView(myContext.getPrivateMetadata());
    final DBAttribute key = referenceLink.getReferentUniqueKey();

    return myContext.getActor(Database.ROLE).readBackground(new ReadTransaction<List<String>>() {
      @Override
      public List<String> transaction(final DBReader reader) throws DBOperationCancelledException {
        return view.query(reader).fold(
          Collections15.<String>arrayList(),
          new LongObjFunction2<List<String>>() {
            @Override
            public List<String> invoke(long a, List<String> b) {
              final Object value = reader.getValue(a, key);
              if(value != null) {
                b.add(referenceLink.toString(value));
              }
              return b;
            }
          });
      }
    }).waitForCompletion();
  }
}
