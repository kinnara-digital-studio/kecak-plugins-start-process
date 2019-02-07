package com.kinnara.kecakplugins.startprocess;

import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class StartProcessTool extends DefaultApplicationPlugin implements PluginWebSupport {
    @Override
    public String getName() {
        return AppPluginUtil.getMessage("startProcess.startProcessTool", getClassName(), "/messages/StartProcess");
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public Object execute(Map map) {
        AppDefinitionDao appDefinitionDao = (AppDefinitionDao) AppUtil.getApplicationContext().getBean("appDefinitionDao");
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        String appId = String.valueOf(map.get("appId"));

        Long appVersion = appDefinitionDao.getPublishedVersion(appId);
        if(appVersion == null)
            appVersion = appDefinitionDao.getLatestVersion("appId");

        String processDefId = AppUtil.getProcessDefIdWithVersion(appId, appVersion.toString(), map.get("processId").toString());
        Map<String, String> workflowVariables = Arrays.stream(((Object[]) map.get("workflowVariables")))
                .map(o -> (Map<String, Object>)o)
                .collect(HashMap::new, (m, o) -> m.put(o.get("variable").toString(), o.get("value").toString()), Map::putAll);

        String loginAs = map.get("loginAs") == null || map.get("loginAs").toString().isEmpty() ? "" : map.get("loginAs").toString();

        // get result and set process id
        WorkflowProcessResult result = workflowManager.processStart(processDefId, workflowVariables, loginAs);

        if(result == null || result.getProcess() == null) {
            LogUtil.warn(getClassName(), "Error starting process ["+processDefId+"]");
        } else if(!map.get("resultProcessId").toString().isEmpty()) {
            workflowManager.processVariable(result.getProcess().getInstanceId(), map.get("resultProcessId").toString(), result.getProcess().getInstanceId());
        }

        return null;
    }

    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/StartProcessTool.json", new String[] {getClassName(), getClassName()}, false, "/messages/StartProcess");
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN);
        if (!isAdmin) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        ApplicationContext ac = AppUtil.getApplicationContext();
        AppService appService = (AppService) ac.getBean("appService");
        AppDefinitionDao appDefinitionDao = (AppDefinitionDao) ac.getBean("appDefinitionDao");
        WorkflowManager workflowManager = (WorkflowManager) ac.getBean("workflowManager");

        String action = request.getParameter("action");

        if ("apps".equalsIgnoreCase(action)) {
            try {
                JSONArray jsonArray = new JSONArray();

                Map<String, String> empty = new HashMap<>();
                empty.put("value", "");
                empty.put("label", "");
                jsonArray.put(empty);

                Collection<AppDefinition> appList = appDefinitionDao.findPublishedApps(null, null, null, null);

                for (AppDefinition app : appList) {
                    Map<String, String> option = new HashMap<String, String>();
                    option.put("value", app.getAppId());
                    option.put("label", app.getName() + " v"+app.getVersion()+" (" + app.getAppId() + ")");
                    jsonArray.put(option);
                }

                jsonArray.write(response.getWriter());

            } catch (Exception ex) {
                LogUtil.error(this.getClass().getName(), ex, "Get Run Process's options Error!");
            }
        } else if ("processes".equalsIgnoreCase(action)) {
            String appId = request.getParameter("appId");
            String appVersion = String.valueOf(appDefinitionDao.getPublishedVersion(appId));
            try {
                JSONArray jsonArray = new JSONArray();
                AppDefinition appDef = appService.getAppDefinition(appId, appVersion);
                PackageDefinition packageDefinition = appDef.getPackageDefinition();
                Long packageVersion = (packageDefinition != null) ? packageDefinition.getVersion() : new Long(1);
                Collection<WorkflowProcess> processList = workflowManager.getProcessList(appId, packageVersion.toString());

                Map<String, String> empty = new HashMap<>();
                empty.put("value", "");
                empty.put("label", "");
                jsonArray.put(empty);

                for (WorkflowProcess p : processList) {
                    Map<String, String> option = new HashMap<String, String>();
                    option.put("value", p.getIdWithoutVersion());
                    option.put("label", p.getName() + " (" + p.getIdWithoutVersion() + ")");
                    jsonArray.put(option);
                }

                jsonArray.write(response.getWriter());
            } catch (Exception ex) {
                LogUtil.error(this.getClass().getName(), ex, "Get Run Process's options Error!");
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }
}
