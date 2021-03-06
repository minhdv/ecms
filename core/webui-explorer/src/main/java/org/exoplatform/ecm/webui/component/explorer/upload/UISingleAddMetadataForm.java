/*
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.ecm.webui.component.explorer.upload;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.PropertyDefinition;

import org.exoplatform.services.log.Log;
import org.exoplatform.ecm.webui.component.explorer.UIJCRExplorer;
import org.exoplatform.ecm.webui.form.UIDialogForm;
import org.exoplatform.resolver.ResourceResolver;
import org.exoplatform.services.cms.metadata.MetadataService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.lifecycle.UIFormLifecycle;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.event.Event.Phase;
import org.exoplatform.webui.form.UIFormDateTimeInput;
import org.exoplatform.webui.form.UIFormInput;
import org.exoplatform.webui.form.UIFormMultiValueInputSet;
import org.exoplatform.webui.form.UIFormSelectBox;
import org.exoplatform.webui.form.UIFormStringInput;

/**
 * Created by The eXo Platform SARL
 * Author : Dang Van Minh
 *          minh.dang@exoplatform.com
 * May 25, 2007 8:58:25 AM
 */
@ComponentConfig(
    lifecycle = UIFormLifecycle.class,
    events = {
      @EventConfig(listeners = UISingleAddMetadataForm.SaveActionListener.class),
      @EventConfig(listeners = UISingleAddMetadataForm.CancelActionListener.class, phase=Phase.DECODE),
      @EventConfig(listeners = UISingleAddMetadataForm.AddActionListener.class, phase = Phase.DECODE),
      @EventConfig(listeners = UISingleAddMetadataForm.RemoveActionListener.class, phase = Phase.DECODE)
    }
)
public class UISingleAddMetadataForm extends UIDialogForm {
  private static final Log LOG  = ExoLogger.getLogger("explorer.UISingleAddMetadataForm");
  private String nodeType_ ;
  public UISingleAddMetadataForm() throws Exception {
    setActions(ACTIONS) ;
  }

  public void setNodeType(String nodeType) { nodeType_ = nodeType ; }
  public String getNodeType() { return nodeType_ ; }

  public String getDialogTemplatePath() {
    repositoryName = getAncestorOfType(UIJCRExplorer.class).getRepositoryName() ;
    MetadataService metadataService = getApplicationComponent(MetadataService.class) ;
    try {
      return metadataService.getMetadataPath(nodeType_, true) ;
    } catch (Exception e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("Unexpected error", e);
      }
    }
    return null ;
  }

  public String getTemplate() { return getDialogTemplatePath() ; }

  @SuppressWarnings("unused")
  public ResourceResolver getTemplateResourceResolver(WebuiRequestContext context, String template) {
    return getAncestorOfType(UIJCRExplorer.class).getJCRTemplateResourceResolver() ;
  }

  @SuppressWarnings("unchecked")
  static public class SaveActionListener extends EventListener<UISingleAddMetadataForm> {
    public void execute(Event<UISingleAddMetadataForm> event) throws Exception {
      UISingleAddMetadataForm uiForm = event.getSource();
      UIJCRExplorer uiJCRExplorer = uiForm.getAncestorOfType(UIJCRExplorer.class) ;
      UISingleUploadContainer uiUploadContainer = uiForm.getAncestorOfType(UISingleUploadContainer.class) ;
      Node node = uiUploadContainer.getEditNode(uiForm.nodeType_) ;
      NodeTypeManager ntManager = uiJCRExplorer.getSession().getWorkspace().getNodeTypeManager();
      PropertyDefinition[] props = ntManager.getNodeType(uiForm.getNodeType()).getPropertyDefinitions();
      List<Value> valueList = new ArrayList<Value>();
      for (PropertyDefinition prop : props) {
        String name = prop.getName();
        String inputName = uiForm.fieldNames.get(name) ;
        if (!prop.isProtected()) {
          int requiredType = prop.getRequiredType();
          if (prop.isMultiple()) {
            if (requiredType == 5) { // date
              UIFormDateTimeInput uiFormDateTime = (UIFormDateTimeInput) uiForm.getUIInput(inputName);
              valueList.add(uiJCRExplorer.getSession().getValueFactory().createValue(uiFormDateTime.getCalendar())) ;
              node.setProperty(name, valueList.toArray(new Value[] {}));
            } else {
              List<String> values = (List<String>) ((UIFormMultiValueInputSet)uiForm.getUIInput(inputName)).getValue() ;
              node.setProperty(name, values.toArray(new String[values.size()]));
            }
          } else {
            if (requiredType == 6) { // boolean
              UIFormInput uiInput = uiForm.getUIInput(inputName) ;
              String value = "false";
              if(uiInput instanceof UIFormSelectBox) value =  ((UIFormSelectBox)uiInput).getValue() ;
              node.setProperty(name, Boolean.parseBoolean(value));
            } else if (requiredType == 5) { // date
              UIFormDateTimeInput cal = (UIFormDateTimeInput) uiForm.getUIInput(inputName);
              node.setProperty(name, cal.getCalendar());
            } else if(requiredType == 1) {
              String value = ((UIFormStringInput)uiForm.getUIInput(inputName)).getValue() ;
              if(value == null) value = "" ;
              node.setProperty(name, value);
            }
          }
        }
      }
      node.save();
      node.getSession().save();
      uiUploadContainer.setRenderedChild(UISingleUploadContent.class) ;
      uiUploadContainer.removeChild(UISingleAddMetadataForm.class) ;
      event.getRequestContext().addUIComponentToUpdateByAjax(uiUploadContainer) ;
    }
  }

  static public class CancelActionListener extends EventListener<UISingleAddMetadataForm> {
    public void execute(Event<UISingleAddMetadataForm> event) throws Exception {
      UISingleUploadContainer uiUploadContainer = event.getSource().getParent() ;
      uiUploadContainer.removeChild(UISingleAddMetadataForm.class) ;
      uiUploadContainer.setRenderedChild(UISingleUploadContent.class) ;
      event.getRequestContext().addUIComponentToUpdateByAjax(uiUploadContainer) ;
    }
  }

  static public class AddActionListener extends EventListener<UISingleAddMetadataForm> {
    public void execute(Event<UISingleAddMetadataForm> event) throws Exception {
      event.getRequestContext().addUIComponentToUpdateByAjax(event.getSource().getParent()) ;
    }
  }

  static public class RemoveActionListener extends EventListener<UISingleAddMetadataForm> {
    public void execute(Event<UISingleAddMetadataForm> event) throws Exception {
      event.getRequestContext().addUIComponentToUpdateByAjax(event.getSource().getParent()) ;
    }
  }
}
