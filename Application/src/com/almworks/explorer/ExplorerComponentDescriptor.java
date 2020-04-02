package com.almworks.explorer;

import com.almworks.actions.ExplorerActions;
import com.almworks.actions.LocalChangesCounterComponent;
import com.almworks.api.application.*;
import com.almworks.api.application.qb.FilterEditorProvider;
import com.almworks.api.application.qb.QueryBuilderComponent;
import com.almworks.api.container.RootContainer;
import com.almworks.api.dynaforms.FaceletRegistry;
import com.almworks.api.explorer.*;
import com.almworks.api.explorer.rules.AutoAssignComponent;
import com.almworks.api.explorer.rules.RulesManager;
import com.almworks.api.platform.ComponentDescriptor;
import com.almworks.dynaforms.FaceletRegistryImpl;
import com.almworks.explorer.assign.AutoAssignComponentImpl;
import com.almworks.explorer.loader.ItemModelRegistryImpl;
import com.almworks.explorer.qbuilder.editor.FilterEditorProviderImpl;
import com.almworks.explorer.qbuilder.editor.QueryBuilderComponentImpl;
import com.almworks.explorer.rules.RulesManagerImpl;
import com.almworks.explorer.workflow.WorkflowComponent2Impl;
import com.almworks.explorer.workflow.WorkflowComponentImpl;
import com.almworks.util.properties.Role;

/**
 * @author : Dyoma
 */
public class ExplorerComponentDescriptor implements ComponentDescriptor {
  public void registerActors(RootContainer registrator) {
    registrator.registerActorClass(ExplorerComponent.ROLE, ExplorerComponentImpl.class);
    registrator.registerActorClass(QueryBuilderComponent.ROLE, QueryBuilderComponentImpl.class);
    registrator.registerActorClass(FilterEditorProvider.ROLE, FilterEditorProviderImpl.class);
    registrator.registerActorClass(Role.role("modalInquiriesDisplayer"), ModalInquiriesDisplayer.class);
    registrator.registerActorClass(ItemModelRegistry.ROLE, ItemModelRegistryImpl.class);
    registrator.registerActorClass(Role.role("explorerActions"), ExplorerActions.class);
    registrator.registerActorClass(WorkflowComponent.ROLE, WorkflowComponentImpl.class);
    registrator.registerActorClass(WorkflowComponent2.ROLE, WorkflowComponent2Impl.class);
    registrator.registerActorClass(LocalChangesCounterComponent.ROLE, LocalChangesCounterComponent.class);
    registrator.registerActorClass(ItemUrlService.ROLE, ItemUrlServiceImpl.class);
    registrator.registerActorClass(ApplicationToolbar.ROLE, ApplicationToolbarImpl.class);
    registrator.registerActorClass(ColumnsCollector.ROLE, ColumnsCollector.class);
    registrator.registerActorClass(ItemDownloadStageKey.ROLE, ItemDownloadStageKey.class);
    registrator.registerActorClass(AttachmentInfoKey.ROLE, AttachmentInfoKey.class);
    registrator.registerActor(DBStatusKey.ROLE, DBStatusKey.KEY);
    registrator.registerActorClass(RulesManager.ROLE, RulesManagerImpl.class);
    registrator.registerActorClass(AutoAssignComponent.ROLE, AutoAssignComponentImpl.class);
    registrator.registerActorClass(FaceletRegistry.ROLE, FaceletRegistryImpl.class);
    registrator.registerActorClass(HintScreen.ROLE, HintScreen.class);
  }
}