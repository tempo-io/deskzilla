package com.almworks.database.debug;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.ArtifactType;
import com.almworks.api.database.typed.AttributeImage;
import org.almworks.util.Util;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class DatabaseXMLDumper {

//  public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  public static void dump(Workspace workspace, String fileName) throws IOException, InterruptedException {
    Document result = new Document();
    Element root = new Element("dump");
    root.setAttribute("date", DatabaseDumper.DATE_FORMAT.format(new Date()));
    result.setRootElement(root);
    FilterManager filterManager = workspace.getFilterManager();
    Filter typesFilter = filterManager.attributeEquals(SystemObjects.ATTRIBUTE.TYPE, SystemObjects.TYPE.TYPE, true);
    Collection<Revision> types = workspace.getViews().getRootView().filter(typesFilter).getAllArtifacts();
    for (Iterator<Revision> iterator = types.iterator(); iterator.hasNext();) {
      dumpTyped(workspace, root, iterator.next());
    }

    XMLOutputter outputter = new XMLOutputter();
    outputter.setFormat(Format.getPrettyFormat());
    FileOutputStream output = new FileOutputStream(fileName);
    outputter.output(result, output);
    output.close();
  }

  public static void dumpTyped(Workspace workspace, Element parent, ArtifactPointer type) throws InterruptedException {
    Element element = new Element(xmlify(type.getArtifact().getTyped(ArtifactType.class).getName()));
    FilterManager filterManager = workspace.getFilterManager();
    Filter typedObjectsFilter = filterManager.attributeEquals(SystemObjects.ATTRIBUTE.TYPE, type, true);
    Collection<Revision> typedCollection = workspace.getViews().getRootView().filter(typedObjectsFilter).getAllArtifacts();
    for (Iterator<Revision> iterator = typedCollection.iterator(); iterator.hasNext();) {
      dumpArtifact(element, iterator.next());
    }
    parent.addContent(element);
  }

  public static void dumpArtifact(Element parent, Revision revision) {
    Artifact artifact = revision.getArtifact();
    Element element = new Element("artifact");
    element.setAttribute("key", String.valueOf(artifact.getKey()));
    while (revision != null) {
      dumpRevision(element, revision);
      revision = revision.getPrevRevision();
    }
    parent.addContent(element);
  }

  public static void dumpRevision(Element parent, Revision revision) {
    Element revisionXML = new Element("revision");
    revisionXML.setAttribute("key", String.valueOf(revision.getKey()));
//    revisionXML.setAttribute("chainKey", revision.getChain().getKey().toString());
    revisionXML.setAttribute("artifactKey", String.valueOf(revision.getArtifact().getKey()));
    revisionXML.setAttribute("wcn", revision.getWCN().toString());
    Map<AttributeImage, Value> data = revision.getImage().getData();
    for (Iterator<AttributeImage> iterator = data.keySet().iterator(); iterator.hasNext();) {
      AttributeImage image = iterator.next();
      Value value = data.get(image);
      String name = xmlify(image.getName());
      Element attributeXML = new Element(name);
      String valueTypeName = image.getValueType().toString();
      if (!"PlainTextValueType".equals(valueTypeName))
        attributeXML.setAttribute("type", valueTypeName);
      dumpValue(attributeXML, value);
      revisionXML.addContent(attributeXML);
    }
    parent.addContent(revisionXML);
  }

  public static void dumpValue(Element parent, Value value) {
    Value[] arrayValues = value.getValue(Value[].class);
    if (arrayValues != null) {
      Element element = new Element("array");
      for (int i = 0; i < arrayValues.length; i++)
        dumpValue(element, arrayValues[i]);
      parent.addContent(element);
      return;
    }

    Artifact reference = value.getValue(Artifact.class);
    if (reference != null) {
      Element element = new Element("reference");
      element.setAttribute("artifactKey", String.valueOf(reference.getKey()));
      Revision lastRevision = reference.getLastRevision();
      element.setAttribute("lastRevisionKey", String.valueOf(lastRevision.getKey()));
      ArtifactType type = lastRevision.getValue(SystemObjects.ATTRIBUTE.TYPE);
      String typeName = type == null ? "<null>" : type.getName();
      String name = lastRevision.getValue(SystemObjects.ATTRIBUTE.NAME);
      String id = lastRevision.getValue(SystemObjects.ATTRIBUTE.ID);
      element.addContent(new Element("type").addContent(typeName));
      if (name != null)
        element.addContent(new Element("name").addContent(name));
      if (id != null)
        element.addContent(new Element("id").addContent(id));
      parent.addContent(element);
      return;
    }

    Integer integer = value.getValue(Integer.class);
    if (integer != null) {
      parent.addContent(new Element("int").addContent(integer.toString()));
      return;
    }

    Date date = value.getValue(Date.class);
    if (date != null) {
      parent.addContent(new Element("date").addContent(DatabaseDumper.DATE_FORMAT.format(date)));
      return;
    }

    String string = value.getValue(String.class);
    if (string != null) {
      parent.addContent(string);
      return;
    }

    parent.addContent(new Element("unknown-value"));
  }

  public static String xmlify(String name) {
    while (true) {
      int k = name.indexOf(' ');
      if (k < 0)
        break;
      String n1 = name.substring(0, k).trim();
      String n2 = name.substring(k).trim();
      if (n2.length() > 0)
        n2 = Util.upper(n2.substring(0, 1)) + n2.substring(1);
      name = n1 + n2;
    }

    if (name.startsWith("-"))
      name = "ESCAPE" + name;
    return name;
  }
}
