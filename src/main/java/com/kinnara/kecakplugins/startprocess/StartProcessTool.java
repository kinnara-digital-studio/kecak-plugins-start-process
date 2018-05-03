package com.kinnara.kecakplugins.startprocess;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class StartProcessTool extends DefaultApplicationPlugin {
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
        AppDefinition appDefinition = (AppDefinition) map.get("appDef");
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) map.get("workflowAssignment");
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        String processDefId = appDefinition.getAppId() + "#latest#" + map.get("processDefId");
        Map<String, String> workflowVariables = Arrays.stream(((Object[]) map.get("workflowVariables")))
                .map(o -> (Map<String, Object>)o)
                .collect(HashMap::new, (m, o) -> m.put(o.get("variable").toString(), o.get("value").toString()), Map::putAll);

        String loginAs = map.get("loginAs") == null || map.get("loginAs").toString().isEmpty() ? "" : map.get("loginAs").toString();

        // get result and set process id
        WorkflowProcessResult result = workflowManager.processStart(processDefId, workflowVariables, loginAs);

        if(result == null || result.getProcess() == null) {
            LogUtil.warn(getClassName(), "Error starting process ["+processDefId+"]");
        } else {
            workflowManager.processVariable(workflowAssignment.getProcessId(), map.get("resultProcessId").toString(), result.getProcess().getInstanceId());
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
        return AppUtil.readPluginResource(getClassName(), "/properties/StartProcessTool.json", null, false, "/messages/StartProcess");
    }
}
