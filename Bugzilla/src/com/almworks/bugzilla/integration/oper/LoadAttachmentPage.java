package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.integration.ServerInfo;
import com.almworks.bugzilla.integration.data.FrontPageData;
import com.almworks.util.RunnableRE;
import org.jdom.Document;
import org.jdom.Element;

import java.util.List;

public class LoadAttachmentPage extends BugzillaOperation {
  private final String myUrl;
  private final ServerInfo myServerInfo;

  public LoadAttachmentPage(ServerInfo serverInfo, int attachmentId) {
    super(serverInfo);
    myServerInfo = serverInfo;
    myUrl = BugzillaIntegration.getShowAttachmentURL(serverInfo.getBaseURL(), attachmentId);
  }

  public List<FrontPageData.FlagInfo> loadAttachmentPage() throws ConnectorException {
    return runOperation(new RunnableRE<List<FrontPageData.FlagInfo>, ConnectorException>() {
      public List<FrontPageData.FlagInfo> run() throws ConnectorException {
        Document document = getDocumentLoader(myUrl, true).httpGET().loadHTML();
        Element form = OperUtils.findUpdateAttachmentFormElement(document.getRootElement(), true);
        return FlagsExtractor.extractFlagInfo(form, myServerInfo.getEmailSuffix());
      }
    });
  }
}
