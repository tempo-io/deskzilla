package com.almworks.export.pdf.itext;

import com.almworks.api.application.Attachment;
import com.almworks.api.application.ModelKey;
import com.almworks.api.application.viewer.Comment;
import com.lowagie.text.Document;
import org.almworks.util.Collections15;

import java.util.Collection;
import java.util.List;

/**
 * @author Alex
 */

public class PrintBuilder {
  private List<PrintElement<Document>> myPrintElements = Collections15.arrayList();
  private boolean myFromBlank;
  private IssueHeader myHeader;
  private AttributeTable myTableElements = new AttributeTable();
  private boolean myTableCompact;

  public void setAttaches(ModelKey<Collection<Attachment>> attachKey, boolean graphics, boolean text) {
    myPrintElements.add(new Attachments(attachKey, text, graphics));
  }

  public void addAttribute(ModelKey att) {
    myTableElements.addAttribute(att);
  }

  public <P> void addCollectionAttribute(String title, ModelKey<Collection<P>> key) {
    
  }

  public void newColumn() {

  }

  public void addComments(ModelKey<Collection<Comment>> commentsKey) {

    final Comments o = new Comments(commentsKey);

    myPrintElements.add(o);
  }

  public void addTextSection(String title, ModelKey<String> key) {
    final LargeTextElement o = new LargeTextElement(key);

    myPrintElements.add(o);
  }

  public List<PrintElement<Document>> createList() {
    List<PrintElement<Document>> resultList = Collections15.arrayList();

    if (myHeader != null) {
      resultList.add(myHeader);
    }

    resultList.add(myTableElements);
    resultList.addAll(myPrintElements);


    resultList.add(new PageBreak(myFromBlank));

    
    return resultList;
  }

  public void setNewFromBlank(boolean fromBlank) {
    myFromBlank = fromBlank;
  }

  public void setHeader(ModelKey<?> key, ModelKey<?> ... others ) {
    myHeader = new IssueHeader(key, others);
  }

  public void addLargeField(ModelKey<?> modelKey) {
    myPrintElements.add(new LargeTextElement(modelKey));
  }

  public void setTableCompact(boolean aBoolean) {
    myTableElements.setColCount(aBoolean ? 2 : 1);
  }

  /*public void setHasTableOfContents(boolean aBoolean) {
    myHeader.setHasAnchor(aBoolean);
  } */
}
