package com.almworks.tracker.alpha;

import com.almworks.util.xmlrpc.*;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class DeskzillaStub implements Runnable {
  private final EndPoint myEndPoint = new EndPoint(11011, 11012);
  private final Set<String> mySubscribed = new HashSet<String>();

  private int myCount = 0;

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new DeskzillaStub());
  }

  public void run() {
    myEndPoint.addIncomingMessageClasses(
      new Class[] {IMOpenArtifacts.class, IMPing.class});
    myEndPoint.addIncomingMessageFactory(new IncomingMessageFactory() {
      public String getRpcMethodName() {
        return AlphaProtocol.Messages.ToTracker.SUBSCRIBE;
      }

      public IncomingMessage createMessage(Vector parameters) throws Exception {
        return new IMSubscribe(parameters, myEndPoint);
      }
    });
    myEndPoint.addIncomingMessageFactory(new IncomingMessageFactory() {
      public String getRpcMethodName() {
        return AlphaProtocol.Messages.ToTracker.UNSUBSCRIBE;
      }

      public IncomingMessage createMessage(Vector parameters) throws Exception {
        return new IMUnsubscribe(parameters, myEndPoint);
      }
    });
    myEndPoint.start();
    Timer timer = new Timer(5000, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        newArtifactData();
      }
    });
    timer.start();
  }

  private void newArtifactData() {
    for (String url : mySubscribed) {
      myCount++;
      Hashtable table = new Hashtable();
      table.put(AlphaProtocol.Messages.ToClient.ArtifactInfo.URL, url);
      table.put(AlphaProtocol.Messages.ToClient.ArtifactInfo.TIMESTAMP_SECONDS, myCount);
      table.put(AlphaProtocol.Messages.ToClient.ArtifactInfo.SHORT_DESCRIPTION, "artifact number <b>" + myCount +"</b>...");
      table.put(AlphaProtocol.Messages.ToClient.ArtifactInfo.LONG_DESCRIPTION, "<b>#" + myCount +"</b><br><br>This is a very very long<br>html descripion!");
      myEndPoint.getOutbox().enqueue(new SimpleOutgoingMessage(AlphaProtocol.Messages.ToClient.ARTIFACT_INFO, table));
    }
  }

  private abstract static class UrlSetMessage extends EndpointIncomingMessage {
    protected final Set<String> myUrls = new HashSet<String>();

    protected UrlSetMessage(Vector requestParameters, MessageEndPoint endPoint) {
      super(requestParameters, endPoint);
      for (Object parameter : requestParameters) {
        myUrls.add((String) parameter);
      }
    }

    protected void process() throws MessageProcessingException {
      for (String url : myUrls) {
        process(url);
      }
    }

    protected void process(String url) {
    }
  }

  private static class IMOpenArtifacts extends UrlSetMessage {
    public IMOpenArtifacts(Vector requestParameters, MessageEndPoint endPoint) {
      super(requestParameters, endPoint);
    }

    protected void process(String url) {
      System.out.println("open artifact " + url);
    }

    public static String name(Void x) {
      return AlphaProtocol.Messages.ToTracker.OPEN_ARTIFACTS;
    }
  }

  private static class IMPing extends EndpointIncomingMessage {
    public IMPing(Vector requestParameters, MessageEndPoint endPoint) {
      super(requestParameters, endPoint);
    }

    protected void process() throws MessageProcessingException {
      System.out.println("pinged");
      Hashtable hashtable = new Hashtable();
      hashtable.put(AlphaProtocol.Messages.ToClient.Ping.TRACKER_NAME, "DeskzillaStub");
      hashtable.put(AlphaProtocol.Messages.ToClient.Ping.TRACKER_VERSION, "0.1");
      getEndPoint().getOutbox().enqueue(new SimpleOutgoingMessage("pong", hashtable));
    }

    public static String name(Void x) {
      return AlphaProtocol.Messages.ToTracker.PING;
    }
  }

  private class IMSubscribe extends UrlSetMessage {
    public IMSubscribe(Vector requestParameters, MessageEndPoint endPoint) {
      super(requestParameters, endPoint);
    }

    protected void process() throws MessageProcessingException {
      for (String url : myUrls) {
        System.out.println("subscribe " + url);
      }
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          mySubscribed.addAll(myUrls);
        }
      });
    }
  }

  private class IMUnsubscribe extends UrlSetMessage {
    public IMUnsubscribe(Vector requestParameters, MessageEndPoint endPoint) {
      super(requestParameters, endPoint);
    }

    protected void process() throws MessageProcessingException {
      for (String url : myUrls) {
        System.out.println("unsubscribe " + url);
      }
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          mySubscribed.removeAll(myUrls);
        }
      });
    }
  }
}
