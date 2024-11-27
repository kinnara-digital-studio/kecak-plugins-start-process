package com.kinnarastudio.kecakplugins.startprocess;

import com.kinnarastudio.kecakplugins.startprocess.commons.StartProcessUtils;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;

import java.util.*;

public class StartProcessTool extends DefaultApplicationPlugin implements PluginWebSupport, StartProcessUtils {
    @Override
    public String getName() {
        return "Start Process Tool";
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("build.number");
        return buildNumber;
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public Object execute(Map map) {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        AppDefinitionDao appDefinitionDao = (AppDefinitionDao) AppUtil.getApplicationContext().getBean("appDefinitionDao");
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        String appId = String.valueOf(map.get("appId"));

        Long appVersion = appDefinitionDao.getPublishedVersion(appId);
        if(appVersion == null)
            appVersion = appDefinitionDao.getLatestVersion("appId");

        AppDefinition appDefinition = appService.getAppDefinition(appId, String.valueOf(appVersion));
        PackageDefinition packageDefinition = appDefinition.getPackageDefinition();

        String processDefId = AppUtil.getProcessDefIdWithVersion(packageDefinition.getAppId(), packageDefinition.getVersion().toString(), map.get("processId").toString());
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
}
