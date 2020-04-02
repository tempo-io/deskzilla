package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.api.http.DefaultHttpMaterial;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.FrontPageData;
import com.almworks.http.HttpClientProviderImpl;
import com.almworks.http.HttpLoaderFactoryImpl;
import com.almworks.util.Pair;
import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.net.URL;
import java.util.*;

public class ParseFlagsHtmlRTests extends BaseTestCase {
  private static final CollectionsCompare CHECK = new CollectionsCompare();

  public void testBz340() throws ConnectorException {
    checkRequestCgi("request.cgi.bz340.html",
      Arrays.asList("f1e", "MR", "MRI", "MRS", "MRSI", "R", "RI", "RS", "RSI"),
      Arrays.asList(1, 1099, 1102, 1109, 1119));
  }

  public void testDwscoalitionOrg() throws ConnectorException {
    checkRequestCgi("request.cgi.dwscoalition.org.html",
      Arrays.asList("blocking-launch", "needs-design", "needs-spec", "needs-testing"),
      Arrays.asList(111, 115, 209));
  }

  private void checkBugPage(String dataFile, Object[] ... expectedFlags) throws ConnectorException {
    Document document = loadHtml(dataFile);
    Element form = OperUtils.findUpdateFormElement(document.getRootElement(), false);
    List<FrontPageData.FlagInfo> flags = FlagsExtractor.extractFlagInfo(form, null);
    List<BugInfo.Flag> missing = Collections15.arrayList();
    for (Object[] expectedFlag : expectedFlags) {
      int id = (Integer)expectedFlag[0];
      int typeId = (Integer) expectedFlag[1];
      String name = (String) expectedFlag[2];
      char status;
      if (expectedFlag.length > 3) status = (Character)expectedFlag[3];
      else status = 0;
      int index = findFlag(flags, id, typeId, name, status);
      if (index < 0) missing.add(new BugInfo.Flag(id, typeId, name, 'u', null, null));
      else flags.remove(index);
    }
    CHECK.empty(flags);
    CHECK.empty(missing);
  }

  private int findFlag(List<FrontPageData.FlagInfo> flags, int id, int typeId, String name, char status) {
    for (int i = 0; i < flags.size(); i++) {
      FrontPageData.FlagInfo info = flags.get(i);
      if (info.getFlagId() != id || info.getTypeId() != typeId || !Util.equals(name, info.getName())) continue;
      if (status != 0 && status == info.getStatus()) return i;
      if (status == 0 && info.isType()) return i;
    }
    return -1;
  }


  private void checkRequestCgi(String dataFile, Collection<String> bugFlagNames, Collection<Integer> bugIds) throws
    ConnectorException
  {
    Document document = loadHtml(dataFile);
    Pair<Pair<List<String>,List<Integer>>, Pair<List<String>, List<Integer>>> result =
      new LoadRequestPage.RequestCGIParser(document).parse();
    Pair<List<String>, List<Integer>> bugFlagInfo = result.getFirst();
    CHECK.unordered(bugFlagNames, bugFlagInfo.getFirst());
    CHECK.unordered(bugIds, bugFlagInfo.getSecond());
  }

  private Document loadHtml(String dataFile) throws ConnectorException {
    URL url = getClass().getClassLoader().getResource("com/almworks/bugzilla/integration/oper/" + dataFile);
    assert "file".equals(url.getProtocol()) : url;
    DefaultHttpMaterial material =
      new DefaultHttpMaterial(new HttpClientProviderImpl(null), new HttpLoaderFactoryImpl());
    DocumentLoader loader = new DocumentLoader(material, new File(url.getFile()));
    Document document = loader.fileLoad().loadHTML();
    return document;
  }
}
