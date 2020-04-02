package com.almworks.download;

import com.almworks.api.download.*;
import com.almworks.api.http.HttpLoader;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Detach;

import java.io.File;
import java.util.Map;

public class DownloadOwnerResolverImpl implements DownloadOwnerResolver {
  private final Map<String, DownloadOwner> myMap = Collections15.hashMap();

  public DownloadOwner getOwner(String ownerId) {
    DownloadOwner owner = getOwnerImpl(ownerId);
    if (owner == null)
      owner = new DelegatingOwner(ownerId);
    return owner;
  }

  private synchronized DownloadOwner getOwnerImpl(String ownerId) {
    return myMap.get(ownerId);
  }

  public synchronized Detach register(final DownloadOwner owner) {
    assert owner != null;
    final String id = owner.getDownloadOwnerID();
    DownloadOwner existing = myMap.get(id);
    if (owner.equals(existing))
      return Detach.NOTHING;
    if (existing != null) {
      assert false : existing + " " + owner;
      return Detach.NOTHING;
    }
    myMap.put(id, owner);
    return new Detach() {
      protected void doDetach() {
        synchronized (DownloadOwnerResolverImpl.this) {
          DownloadOwner removed = myMap.remove(id);
          if (!owner.equals(removed)) {
            assert false : owner + " " + removed;
            myMap.put(id, removed);
          }
        }
      }
    };
  }

  private class DelegatingOwner implements DownloadOwner {
    private final String myOwnerId;

    public DelegatingOwner(String ownerId) {
      myOwnerId = ownerId;
    }

    private DownloadOwner getDelegate() {
      DownloadOwner owner = getOwnerImpl(myOwnerId);
      return owner != null ? owner : DisabledOwner.INSTANCE;
    }

    public HttpLoader createLoader(String argument, boolean retrying) throws CannotCreateLoaderException {
      return getDelegate().createLoader(argument, retrying);
    }

    public String getDownloadOwnerID() {
      return getDelegate().getDownloadOwnerID();
    }

    public boolean isValid() {
      return getDelegate().isValid();
    }

    public void validateDownload(String argument, File downloadedFile, String mimeType) throws DownloadInvalidException {
      getDelegate().validateDownload(argument, downloadedFile, mimeType);
    }
  }

  public static class DisabledOwner implements DownloadOwner {
    static final DisabledOwner INSTANCE = new DisabledOwner();

    public String getDownloadOwnerID() {
      return "no-owner";
    }

    public HttpLoader createLoader(String argument, boolean retrying) throws CannotCreateLoaderException {
      throw new CannotCreateLoaderException();
    }

    public boolean isValid() {
      return false;
    }

    public void validateDownload(String argument, File downloadedFile, String mimeType) throws DownloadInvalidException {
    }
  }
}
