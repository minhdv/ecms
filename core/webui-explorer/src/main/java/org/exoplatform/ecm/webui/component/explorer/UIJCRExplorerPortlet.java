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
package org.exoplatform.ecm.webui.component.explorer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.AccessDeniedException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.portlet.MimeResponse;
import javax.portlet.PortletMode;
import javax.portlet.PortletPreferences;
import javax.portlet.RenderResponse;

import org.exoplatform.ecm.jcr.model.Preference;
import org.exoplatform.ecm.utils.text.Text;
import org.exoplatform.ecm.webui.component.explorer.control.UIActionBar;
import org.exoplatform.ecm.webui.component.explorer.control.UIAddressBar;
import org.exoplatform.ecm.webui.component.explorer.control.UIControl;
import org.exoplatform.ecm.webui.component.explorer.control.action.AddDocumentActionComponent;
import org.exoplatform.ecm.webui.component.explorer.control.action.EditDocumentActionComponent;
import org.exoplatform.ecm.webui.component.explorer.sidebar.UISideBar;
import org.exoplatform.ecm.webui.component.explorer.sidebar.UITreeExplorer;
import org.exoplatform.ecm.webui.utils.JCRExceptionManager;
import org.exoplatform.ecm.webui.utils.Utils;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.webui.util.SessionProviderFactory;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.cms.drives.DriveData;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.cms.views.ManageViewService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.application.WebuiApplication;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.application.portlet.PortletRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIPopupContainer;
import org.exoplatform.webui.core.UIPortletApplication;
import org.exoplatform.webui.core.UIRightClickPopupMenu;
import org.exoplatform.webui.core.lifecycle.UIApplicationLifecycle;
import org.exoplatform.webui.core.model.SelectItemOption;
import org.exoplatform.webui.ext.filter.UIExtensionFilter;
import org.w3c.dom.Element;

@ComponentConfig(
    lifecycle = UIApplicationLifecycle.class
)
public class UIJCRExplorerPortlet extends UIPortletApplication {

  /**
   * Logger.
   */
  private static final Log LOG  = ExoLogger.getLogger(UIJCRExplorerPortlet.class);

  final static public String REPOSITORY         = "repository";

  final static public String CATEGORY_MANDATORY = "categoryMandatoryWhenFileUpload";

  final static public String MAX_SIZE_UPLOAD    = "uploadFileSizeLimitMB";

  final static public String ISDIRECTLY_DRIVE   = "isDirectlyDrive";

  final static public String DRIVE_NAME         = "driveName";

  final static public String USECASE            = "usecase";

  final static public String JAILED             = "jailed";

  final static public String SOCIAL             = "social";

  final static public String SELECTION          = "selection";

  final static public String PERSONAL           = "personal";

  final static public String PARAMETERIZE       = "parameterize";

  final static public String PARAMETERIZE_PATH  = "nodePath";

  final static public String SHOW_TOP_BAR       = "showTopBar";

  final static public String SHOW_ACTION_BAR    = "showActionBar";

  final static public String SHOW_SIDE_BAR      = "showSideBar";

  final static public String SHOW_FILTER_BAR    = "showFilterBar";

  final static public String EDIT_IN_NEW_WINDOW = "editInNewWindow";

  private boolean flagSelect = false;

  public UIJCRExplorerPortlet() throws Exception {
    UIJcrExplorerContainer explorerContainer = addChild(UIJcrExplorerContainer.class, null, null);
    explorerContainer.initExplorer();
    addChild(UIJcrExplorerEditContainer.class, null, null).setRendered(false);
  }

  public boolean isFlagSelect() { return flagSelect; }

  public void setFlagSelect(boolean flagSelect) { this.flagSelect = flagSelect; }

  public boolean isShowTopBar() {
    PortletPreferences portletpref = getPortletPreferences();
    return Boolean.valueOf(portletpref.getValue(UIJCRExplorerPortlet.SHOW_TOP_BAR, "false"));
  }

  public boolean isShowActionBar() {
    PortletPreferences portletpref = getPortletPreferences();
    return Boolean.valueOf(portletpref.getValue(UIJCRExplorerPortlet.SHOW_ACTION_BAR, "false")) &&
           !this.findFirstComponentOfType(UIJCRExplorer.class).isAddingDocument();
  }

  public boolean isShowSideBar() {
    PortletPreferences portletpref = getPortletPreferences();
    return Boolean.valueOf(portletpref.getValue(UIJCRExplorerPortlet.SHOW_SIDE_BAR, "false"));
  }

  public boolean isShowFilterBar() {
    PortletPreferences portletpref = getPortletPreferences();
    return Boolean.valueOf(portletpref.getValue(UIJCRExplorerPortlet.SHOW_FILTER_BAR, "false"));
  }

  public void  processRender(WebuiApplication app, WebuiRequestContext context) throws Exception {
    UIJcrExplorerContainer explorerContainer = getChild(UIJcrExplorerContainer.class);
    UIJcrExplorerEditContainer editContainer = getChild(UIJcrExplorerEditContainer.class);
    PortletRequestContext portletReqContext = (PortletRequestContext) context ;
    HashMap<String, String> map = getElementByContext(context);
    PortalRequestContext pcontext = Util.getPortalRequestContext();
    String backToValue = Util.getPortalRequestContext().getRequestParameter(org.exoplatform.ecm.webui.utils.Utils.URL_BACKTO);
    if (backToValue != null && backToValue.length() > 0) {
      getPortletPreferences().setValue(Utils.URL_BACKTO, backToValue);
    }
    HashMap<String, String> changeDrive = (HashMap<String, String>)pcontext.getAttribute("jcrexplorer-show-document");
    if (changeDrive!=null) {
      map = changeDrive;
      context.setAttribute("jcrexplorer-show-document", null);
    }
    if (portletReqContext.getApplicationMode() == PortletMode.VIEW) {
      if (map.size() > 0) {
        showDocument(context, map);
      } else {
        initwhenDirect(explorerContainer, editContainer);
      }
      explorerContainer.setRendered(true);
      UIJCRExplorer uiExplorer = explorerContainer.getChild(UIJCRExplorer.class);
      if(uiExplorer != null) {
        try {
          uiExplorer.getSession();
          try {
            uiExplorer.getSession().getItem(uiExplorer.getRootPath());
          } catch(PathNotFoundException e) {
            reloadWhenBroken(uiExplorer);
            super.processRender(app, context);
            return;
          }
        } catch(RepositoryException repo) {
          super.processRender(app, context);
        }
      }
      getChild(UIJcrExplorerEditContainer.class).setRendered(false);
    } else if(portletReqContext.getApplicationMode() == PortletMode.HELP) {
      if (LOG.isDebugEnabled()) LOG.debug("\n\n>>>>>>>>>>>>>>>>>>> IN HELP  MODE \n");
    } else if(portletReqContext.getApplicationMode() == PortletMode.EDIT) {
      explorerContainer.setRendered(false);
      getChild(UIJcrExplorerEditContainer.class).setRendered(true);
    }

    RenderResponse response = context.getResponse();
    Element elementS = response.createElement("script");
    elementS.setAttribute("type", "text/javascript");
    elementS.setAttribute("src", "/eXoWCMResources/javascript/eXo/wcm/backoffice/public/Components.js");
    response.addProperty(MimeResponse.MARKUP_HEAD_ELEMENT,elementS);

    super.processRender(app, context);
  }

  public void initwhenDirect(UIJcrExplorerContainer explorerContainer,
      UIJcrExplorerEditContainer editContainer) throws Exception {
    if (editContainer.getChild(UIJcrExplorerEditForm.class).isFlagSelectRender()) {
      PortletPreferences portletPref = getPortletPreferences();
//      String driveName = portletPref.getValue("driveName", "").trim();
//      String repository = portletPref.getValue("repository", "").trim();
//      String userId = Util.getPortalRequestContext().getRemoteUser();
//      UIJCRExplorer uiJCRExplorer = explorerContainer.getChild(UIJCRExplorer.class);
      explorerContainer.initExplorer();
      editContainer.getChild(UIJcrExplorerEditForm.class).setFlagSelectRender(false);
//      ManageDriveService driveService = getApplicationComponent(ManageDriveService.class);
//      DriveData driveData = driveService.getDriveByName(driveName, repository);
//      String nodePath = portletPref.getValue("nodePath", "").trim();
//      if (!nodePath.startsWith("/")) nodePath = "/" + nodePath;
//      String homePath = (driveData.getHomePath().concat(nodePath)).replaceAll("/+", "/");
//      if(!canUseConfigDrive(repository, driveName)) {
//        homePath = getUserDrive(repository, "private").getHomePath();
//      }
//      if (homePath.contains("${userId}")) homePath = homePath.replace("${userId}", userId);
//      uiJCRExplorer.setSelectNode(driveData.getWorkspace(), homePath);
//      uiJCRExplorer.refreshExplorer();
    }
  }

  public String getPreferenceRepository() {
    PortletRequestContext pcontext = (PortletRequestContext)WebuiRequestContext.getCurrentInstance() ;
    PortletPreferences portletPref = pcontext.getRequest().getPreferences() ;
    String repository = portletPref.getValue(Utils.REPOSITORY, "") ;
    return repository ;
  }

  public String getPreferenceTrashHomeNodePath() {
    return getPortletPreferences().getValue(Utils.TRASH_HOME_NODE_PATH, "");
  }

  public String getPreferenceTrashRepository() {
    return getPortletPreferences().getValue(Utils.TRASH_REPOSITORY, "");
  }

  public String getPreferenceTrashWorkspace() {
    return getPortletPreferences().getValue(Utils.TRASH_WORKSPACE, "");
  }

  public boolean isEditInNewWindow() {
    return Boolean.parseBoolean(getPortletPreferences().getValue(UIJCRExplorerPortlet.EDIT_IN_NEW_WINDOW, "true"));
  }

  public PortletPreferences getPortletPreferences() {
    PortletRequestContext pcontext = (PortletRequestContext)WebuiRequestContext.getCurrentInstance();
    return pcontext.getRequest().getPreferences();
  }

  @Deprecated
  public DriveData getUserDrive(String repoName, String userType) throws Exception {
    ManageDriveService manageDriveService = getApplicationComponent(ManageDriveService.class);
    List<String> userRoles = Utils.getMemberships();
    String userId = Util.getPortalRequestContext().getRemoteUser();
    for(DriveData userDrive : manageDriveService.getPersonalDrives(userId, userRoles)) {
      if(userDrive.getName().equalsIgnoreCase(userType)) {
        return userDrive;
      }
    }
    return null;
  }
  
  public DriveData getUserDrive(String userType) throws Exception {
    ManageDriveService manageDriveService = getApplicationComponent(ManageDriveService.class);
    List<String> userRoles = Utils.getMemberships();
    String userId = Util.getPortalRequestContext().getRemoteUser();
    for(DriveData userDrive : manageDriveService.getPersonalDrives(userId, userRoles)) {
      if(userDrive.getName().equalsIgnoreCase(userType)) {
        return userDrive;
      }
    }
    return null;
  }  

  @Deprecated
  public boolean canUseConfigDrive(String repoName, String driveName) throws Exception {
    ManageDriveService dservice = getApplicationComponent(ManageDriveService.class);
    String userId = Util.getPortalRequestContext().getRemoteUser();
    List<String> userRoles = Utils.getMemberships();
    for(DriveData drive : dservice.getDriveByUserRoles(userId, userRoles)) {
      if(drive.getName().equals(driveName)) return true;
    }
    return false;
  }
  
  public boolean canUseConfigDrive(String driveName) throws Exception {
    ManageDriveService dservice = getApplicationComponent(ManageDriveService.class);
    String userId = Util.getPortalRequestContext().getRemoteUser();
    List<String> userRoles = Utils.getMemberships();
    for(DriveData drive : dservice.getDriveByUserRoles(userId, userRoles)) {
      if(drive.getName().equals(driveName)) return true;
    }
    return false;
  }

  public void reloadWhenBroken(UIJCRExplorer uiExplorer) {
    uiExplorer.getChild(UIControl.class).setRendered(false);
    UIWorkingArea uiWorkingArea = uiExplorer.getChild(UIWorkingArea.class);
    uiWorkingArea.setRenderedChild(UIDrivesArea.class);
    UIRightClickPopupMenu uiRightClickPopupMenu = uiWorkingArea.getChild(UIRightClickPopupMenu.class);
    if(uiRightClickPopupMenu!=null)
      uiRightClickPopupMenu.setRendered(true);
  }

  private HashMap<String, String> getElementByContext(WebuiRequestContext context) {
    HashMap<String, String> mapParam = new HashMap<String, String>();
    //In case access by ajax request
    if (context.useAjax()) return mapParam;
    Pattern patternUrl = Pattern.compile("([^/]+)/([^/]+)/([^/]+)/(.*)");
    PortalRequestContext pcontext = Util.getPortalRequestContext();
    Matcher matcher;

    String nodePathParam = pcontext.getRequestParameter("path");
    if (nodePathParam!=null) {
      patternUrl = Pattern.compile("([^/]+)/([^/]+)/(.*)");
      matcher = patternUrl.matcher(nodePathParam);
      if (matcher.find()) {
        mapParam.put("repository", matcher.group(1));
        mapParam.put("drive", matcher.group(2));
        mapParam.put("path", matcher.group(3));
        return mapParam;
      }
    }

    String nodePathUrl = pcontext.getNodePath().substring(1);
    String[] uri = nodePathUrl.split("/");
    if (uri == null || uri.length < 3) return mapParam;
    patternUrl = Pattern.compile("([^/]+)/([^/]+)/([^/]+)/(.*)");
    matcher = patternUrl.matcher(nodePathUrl);
    if (matcher.find()) {
      mapParam.put("repository", matcher.group(2));
      mapParam.put("drive", matcher.group(3));
      mapParam.put("path", "/" + matcher.group(4));
    } else {
      patternUrl = Pattern.compile("([^/]+)/([^/]+)/(.*)");
      matcher = patternUrl.matcher(nodePathUrl);
      if (matcher.find()) {
        mapParam.put("repository", matcher.group(2));
        mapParam.put("drive", matcher.group(3));
        mapParam.put("path", "/");
      }
    }
    return mapParam;
  }

  private void showDocument(WebuiRequestContext context, HashMap<String, String> map) throws Exception {
    String repositoryName = String.valueOf(map.get("repository"));
    String driveName = String.valueOf(map.get("drive"));
    String path = String.valueOf(map.get("path"));
    if(!path.equals("/")) {
      ArrayList<String> encodeNameArr = new ArrayList<String>();
      for(String name : path.split("/")) {
        if(name.length() > 0) {
          encodeNameArr.add(Text.escapeIllegalJcrChars(name));
        }
      }
      StringBuilder encodedPath = new StringBuilder();
      for(String encodedName : encodeNameArr) {
        encodedPath.append("/").append(encodedName);
      }
      path = encodedPath.toString();
    }
    UIApplication uiApp = findFirstComponentOfType(UIApplication.class);
    ManageDriveService manageDrive = getApplicationComponent(ManageDriveService.class);
    DriveData driveData = null;
    try {
      driveData = manageDrive.getDriveByName(driveName);
      if (driveData == null) throw new PathNotFoundException();
    } catch (PathNotFoundException e) {
      Object[] args = { driveName };
      uiApp.addMessage(new ApplicationMessage("UIDrivesBrowser.msg.drive-not-exist", args, ApplicationMessage.WARNING));
      context.addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
      return;
    }
    RepositoryService rservice = getApplicationComponent(RepositoryService.class);
    String userId = Util.getPortalRequestContext().getRemoteUser();
    List<String> viewList = new ArrayList<String>();

    for (String role : Utils.getMemberships()) {
      for (String viewName : driveData.getViews().split(",")) {
        if (!viewList.contains(viewName.trim())) {
          Node viewNode =
            getApplicationComponent(ManageViewService.class).getViewByName(viewName.trim(),
                SessionProviderFactory.createSystemProvider());
          String permiss = viewNode.getProperty("exo:accessPermissions").getString();
          if (permiss.contains("${userId}")) permiss = permiss.replace("${userId}", userId);
          String[] viewPermissions = permiss.split(",");
          if (permiss.equals("*")) viewList.add(viewName.trim());
          if (driveData.hasPermission(viewPermissions, role)) viewList.add(viewName.trim());
        }
      }
    }
    String viewListStr = "";
    List<SelectItemOption<String>> viewOptions = new ArrayList<SelectItemOption<String>>();
    ResourceBundle res = context.getApplicationResourceBundle();
    String viewLabel = null;
    for (String viewName : viewList) {
      try {
        viewLabel = res.getString("Views.label." + viewName) ;
      } catch (MissingResourceException e) {
        viewLabel = viewName;
      }
      viewOptions.add(new SelectItemOption<String>(viewLabel, viewName));
      if(viewListStr.length() > 0) viewListStr = viewListStr + "," + viewName;
      else viewListStr = viewName;
    }
    driveData.setViews(viewListStr);
    String homePath = driveData.getHomePath();
    if (homePath.contains("${userId}")) 
      homePath = org.exoplatform.services.cms.impl.Utils.getPersonalDrivePath(homePath, userId);
    setFlagSelect(true);
    UIJCRExplorer uiExplorer = findFirstComponentOfType(UIJCRExplorer.class);
//    String mode = getPortletPreferences().getValue(UIJCRExplorerPortlet.MODE, "");
//    setStandardMode(UIJCRExplorerPortlet.STANDARD_MODE.equals(mode));

    uiExplorer.setDriveData(driveData);
    uiExplorer.setIsReferenceNode(false);

    SessionProvider provider = SessionProviderFactory.createSessionProvider();
    ManageableRepository repository = rservice.getCurrentRepository();
    try {
      Session session = provider.getSession(driveData.getWorkspace(),repository);
      // check if it exists
      // we assume that the path is a real path
      session.getItem(homePath);
    } catch(AccessDeniedException ace) {
      Object[] args = { driveName };
      uiApp.addMessage(new ApplicationMessage("UIDrivesBrowser.msg.access-denied", args,
          ApplicationMessage.WARNING));
      context.addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
      return;
    } catch(NoSuchWorkspaceException nosuchWS) {
      Object[] args = { driveName };
      uiApp.addMessage(new ApplicationMessage("UIDrivesBrowser.msg.workspace-not-exist", args,
          ApplicationMessage.WARNING));
      context.addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
      return;
    } catch(Exception e) {
      JCRExceptionManager.process(uiApp, e);
      return;
    }
    uiExplorer.setRepositoryName(repositoryName);
    uiExplorer.setWorkspaceName(driveData.getWorkspace());
    uiExplorer.setRootPath(homePath);
    path = homePath.concat(path).replaceAll("/+", "/");
    Preference pref = uiExplorer.getPreference();
    pref.setShowSideBar(driveData.getViewSideBar());
    pref.setShowNonDocumentType(driveData.getViewNonDocument());
    pref.setShowPreferenceDocuments(driveData.getViewPreferences());
    pref.setAllowCreateFoder(driveData.getAllowCreateFolders());
    pref.setShowHiddenNode(driveData.getShowHiddenNode());
    uiExplorer.setIsReferenceNode(false);
    UIControl uiControl = uiExplorer.getChild(UIControl.class);

    UIAddressBar uiAddressBar = uiControl.getChild(UIAddressBar.class);
    uiAddressBar.setViewList(viewList);
    uiAddressBar.setSelectedViewName(viewList.get(0));
    uiAddressBar.setRendered(isShowTopBar());
//    if (viewList.size() == 1) uiAddressBar.setRendered(false);

    UIActionBar uiActionbar = uiControl.getChild(UIActionBar.class);
//  uiActionbar.setTabOptions(viewList.get(0));
    boolean isShowActionBar = isShowActionBar() ;
    uiActionbar.setTabOptions(viewList.get(0));
    uiActionbar.setRendered(isShowActionBar);
    uiExplorer.setSelectNode(driveData.getWorkspace(), path);

    UIWorkingArea uiWorkingArea = findFirstComponentOfType(UIWorkingArea.class);
    UISideBar uiSideBar = uiWorkingArea.findFirstComponentOfType(UISideBar.class);
    if (uiSideBar.isRendered()) {
      uiSideBar.updateSideBarView();
      uiSideBar.getChild(UITreeExplorer.class).buildTree();
    }

    UIDocumentWorkspace uiDocWorkspace = uiWorkingArea.getChild(UIDocumentWorkspace.class);
    uiDocWorkspace.setRenderedChild(UIDocumentContainer.class);
    uiDocWorkspace.setRendered(true);

    UIDrivesArea uiDrive = uiWorkingArea.getChild(UIDrivesArea.class);
    if (uiDrive != null) uiDrive.setRendered(false);
    context.addUIComponentToUpdateByAjax(uiDocWorkspace);
    UIPopupContainer popupAction = getChild(UIPopupContainer.class);
    if (popupAction != null && popupAction.isRendered()) {
      popupAction.deActivate();
      context.addUIComponentToUpdateByAjax(popupAction);
    }

    Boolean isEdit = Boolean.valueOf(Util.getPortalRequestContext().getRequestParameter("edit"));
    Node selectedNode = uiExplorer.getCurrentNode();
    if (isEdit) {
      if (uiExplorer.getCurrentPath().equals(path) &&
          canManageNode(selectedNode, uiApp, uiExplorer, uiActionbar, context, EditDocumentActionComponent.getFilters())) {
        EditDocumentActionComponent.editDocument(null, context, this, uiExplorer, selectedNode, uiApp);
      } else {
        uiApp.addMessage(new ApplicationMessage("UIJCRExplorerPortlet.msg.file-access-denied", null, ApplicationMessage.WARNING));
        context.addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
      }
    }

    boolean isAddNew = Boolean.valueOf(Util.getPortalRequestContext().getRequestParameter("addNew"));
    if (!isEdit && isAddNew) {
      if (canManageNode(selectedNode, uiApp, uiExplorer, uiActionbar, context, AddDocumentActionComponent.getFilters())) {
        AddDocumentActionComponent.addDocument(null, uiExplorer, uiApp, this, context);
      } else {
        uiApp.addMessage(new ApplicationMessage("UIJCRExplorerPortlet.msg.file-access-denied", null, ApplicationMessage.WARNING));
        context.addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
      }
    }
    uiExplorer.refreshExplorer(null, (isAddNew && isEdit) && isEditInNewWindow());
    uiExplorer.setRenderSibling(UIJCRExplorer.class);
  }

  private boolean canManageNode(Node selectedNode,
                                UIApplication uiApp,
                                UIJCRExplorer uiExplorer,
                                UIActionBar uiActionBar,
                                Object context,
                                List<UIExtensionFilter> filters) throws Exception {
    Map<String, Object> ctx = new HashMap<String, Object>();
    ctx.put(UIActionBar.class.getName(), uiActionBar);
    ctx.put(UIJCRExplorer.class.getName(), uiExplorer);
    ctx.put(UIApplication.class.getName(), uiApp);
    ctx.put(Node.class.getName(), selectedNode);
    ctx.put(WebuiRequestContext.class.getName(), context);
    for (UIExtensionFilter filter : filters)
      try {
        if (!filter.accept(ctx))
          return false;
      } catch (Exception e) {
        return false;
      }
    return true;
  }
}
