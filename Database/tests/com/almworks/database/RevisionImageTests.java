package com.almworks.database;

import com.almworks.api.database.Artifact;
import com.almworks.api.database.RevisionImage;
import com.almworks.api.database.typed.AttributeImage;
import util.concurrent.CountDown;

import javax.swing.*;
import java.util.Iterator;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RevisionImageTests extends WorkspaceFixture {
  public void testImage() throws InterruptedException {
    Artifact artifact = createObjectAndSetValue(myAttributeOne, "one");
    final RevisionImage image = artifact.getLastRevision().getImage();
    final CountDown finished = new CountDown(1);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        for (Iterator<AttributeImage> iterator = image.getData().keySet().iterator(); iterator.hasNext();) {
          AttributeImage attributeImage = iterator.next();
          attributeImage.getArtifact();
          attributeImage.getName();
          attributeImage.getDisplayableName();
          attributeImage.getPointerKey();
          attributeImage.getType();
          attributeImage.getTypeImage();
          attributeImage.getValueType();
        }
        finished.release();
      }
    });
    finished.acquire();
  }
/*
  public void testImageEquality() {
    RevisionImage attributeOne = myAttributeOne.getArtifact().getLastRevision().getImage();
    assertEqualsBothWays(attributeOne, myAttributeOne);
    assertEqualsBothWays(attributeOne, myAttributeOne.getArtifact());
  }
*/
}
