package com.kinnarastudio.kecakplugins.startprocess.datalist;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageActivityForm;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.workflow.lib.AssignmentCompleteButton;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

public class DuplicateRowDataListAction extends DataListActionDefault {
    public final static String LABEL = "Duplicate";

    @Override
    public String getLinkLabel() {
        String label = getPropertyString("label");
        if (label == null || label.isEmpty()) {
            label = "Start Process";
        }
        return label;
    }

    @Override
    public String getHref() {
        return getPropertyString("href");
    }

    @Override
    public String getTarget() {
        return "post";
    }

    @Override
    public String getHrefParam() {
        return getPropertyString("hrefParam");
    }

    @Override
    public String getHrefColumn() {
        return getPropertyString("hrefColumn");
    }

    @Override
    public String getConfirmation() {
        return getPropertyString("confirmation");
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
        if (request != null && !"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }

        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");
        AppService appService = (AppService) applicationContext.getBean("appService");
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();

        String username = WorkflowUtil.getCurrentUsername();
        WorkflowProcessResult workflowProcessResult = Optional.ofNullable(rowKeys)
                .stream()
                .flatMap(Arrays::stream)
                .filter(Objects::nonNull)
                .map(s -> workflowManager.getAssignmentByRecordId(s, null, null, username))
                .filter(Objects::nonNull)
                .map(WorkflowAssignment::getProcessDefId)
                .filter(Objects::nonNull)
                .findFirst()
                .map(pid -> {
                    FormData formData = new FormData();
                    formData.addRequestParameterValues(AssignmentCompleteButton.DEFAULT_ID, new String[]{AssignmentCompleteButton.DEFAULT_ID});

                    Map<String, String> workflowVariables = new HashMap<>();

                    PackageActivityForm packageActivityForm = appService.viewStartProcessForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), pid,  formData, "");
                    if(packageActivityForm == null) return null;

                    final String appId = packageActivityForm.getPackageDefinition().getAppDefinition().getAppId();
                    final String appVersion = packageActivityForm.getPackageDefinition().getAppDefinition().getVersion().toString();
                    final String processDefId = packageActivityForm.getProcessDefId();
                    return appService.submitFormToStartProcess(appId, appVersion, packageActivityForm, processDefId, formData, workflowVariables, null);
                })
                .orElse(null);

        String primaryKey = getRecordId(workflowProcessResult);

        final DataListActionResult result = new DataListActionResult();
        result.setType(DataListActionResult.TYPE_REDIRECT);
        result.setUrl("REFERER");

        return result;
    }

    protected String getRecordId(WorkflowProcessResult workflowProcessResult) {
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        return Optional.ofNullable(workflowProcessResult)
                .map(WorkflowProcessResult::getProcess)
                .map(WorkflowProcess::getInstanceId)
                .map(workflowManager::getRunningProcessById)
                .map(WorkflowProcess::getRecordId)
                .orElse("");
    }

    @Override
    public String getName() {
        return LABEL;
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
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return DuplicateRowDataListAction.class.getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/datalist/StartProcessDuplicateAction.json", null, true, "messages/StartProcessDuplicateAction");
    }

    @Override
    public Boolean supportList() {
        return false;
    }
}
