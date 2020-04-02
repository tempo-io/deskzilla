package com.almworks.services;

import com.almworks.api.container.RootContainer;
import com.almworks.api.http.*;
import com.almworks.api.http.auth.HttpAuthDialog;
import com.almworks.api.platform.ComponentDescriptor;
import com.almworks.http.HttpClientProviderImpl;
import com.almworks.http.HttpLoaderFactoryImpl;
import com.almworks.http.ui.HttpAuthDialogImpl;
import com.almworks.http.ui.HttpProxyInfoImpl;

public class HttpComponentsDescriptor implements ComponentDescriptor {
  public void registerActors(RootContainer container) {
    container.registerActorClass(HttpAuthDialog.ROLE, HttpAuthDialogImpl.class);
    container.registerActorClass(HttpProxyInfo.ROLE, HttpProxyInfoImpl.class);
    container.registerActorClass(HttpProxyAction.ROLE, HttpProxyAction.class);
    container.registerActorClass(HttpClientProvider.ROLE, HttpClientProviderImpl.class);
    container.registerActorClass(HttpLoaderFactory.ROLE, HttpLoaderFactoryImpl.class);
  }
}
