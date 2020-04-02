package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.AttributeImage;

import java.util.Iterator;
import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RCBAsNormalArtifactsTests extends WorkspaceFixture {
  private Artifact myArtifact;

  protected void setUp() throws Exception {
    super.setUp();
    myArtifact = createRCB("y");
    changeArtifact(myArtifact, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "x");
  }

  protected void tearDown() throws Exception {
    myArtifact = null;
    super.tearDown();
  }

  public void testImage() throws InterruptedException {
    RevisionImage image = myArtifact.getLastRevision().getImage();
    Map<AttributeImage, Value> map = image.getData();
    for (Iterator<AttributeImage> ii = map.keySet().iterator(); ii.hasNext();) {
      AttributeImage attr = ii.next();
      String name = attr.getName();
      String value = map.get(attr).getValue(String.class);
      if (name.equals("one")) {
        assertEquals("y", value);
      } else if (name.equals("two")) {
        assertEquals("x", value);
      } else {
        fail("unexpected attribute " + name);
      }
    }
    image = myArtifact.getLastRevision(RevisionAccess.ACCESS_MAINCHAIN).getImage();
    map = image.getData();
    for (Iterator<AttributeImage> ii = map.keySet().iterator(); ii.hasNext();) {
      AttributeImage attr = ii.next();
      String name = attr.getName();
      String value = map.get(attr).getValue(String.class);
      if (name.equals("one")) {
        assertEquals("y", value);
      } else {
        fail("unexpected attribute " + name);
      }
    }
  }


}
