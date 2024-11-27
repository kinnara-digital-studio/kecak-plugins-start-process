package com.kinnarastudio.kecakplugins.startprocess;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.kecakplugins.startprocess.commons.StartProcessUtils;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageActivityForm;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.workflow.lib.AssignmentCompleteButton;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.kecak.apps.form.service.FormDataUtil;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

public class StartFormProcessTool extends DefaultApplicationPlugin implements StartProcessUtils {
    @Override
    public String getName() {
        return "Start Form Process Tool";
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
        if (appVersion == null)
            appVersion = appDefinitionDao.getLatestVersion("appId");

        AppDefinition appDefinition = appDefinitionDao.loadVersion(appId, appVersion);
        if (appDefinition == null) {
            LogUtil.warn(getClassName(), "No Application Definition found for ID [" + appId + "] version [" + appVersion + "]");
            return null;
        }

        // get processDefId
        String processDefId = appService.getWorkflowProcessForApp(appId, appVersion.toString(), map.get("processId").toString()).getId();
        PackageDefinition packageDefinition = appDefinition.getPackageDefinition();
        final FormData formData = new FormData();
        PackageActivityForm packageActivityForm = appService.viewStartProcessForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), processDefId, formData, "");

        Optional<Form> optForm = Optional.of(packageActivityForm)
                .map(PackageActivityForm::getFormId)
                .map(Try.onFunction(this::generateForm));

        if (optForm.isEmpty()) {
            LogUtil.warn(getClassName(), "Error generating form for process [" + processDefId + "]");
            return null;
        }

        Form form = optForm.get();

        Arrays.stream(((Object[]) map.get("formFields")))
                .map(o -> (Map<String, Object>) o)
                .forEach(m -> {
                    // convert json to field data
                    String field = m.get("field").toString();
                    String value = m.get("value").toString();

                    Element element = FormUtil.findElement(field, form, new FormData(), true);
                    if (element != null)
                        formData.addRequestParameterValues(FormUtil.getElementParameterName(element), new String[]{value});

                });

        formData.addRequestParameterValues(AssignmentCompleteButton.DEFAULT_ID, new String[]{"true"});
        formData.addRequestParameterValues(FormUtil.getElementParameterName(form) + "_SUBMITTED", new String[]{""});
        formData.setDoValidation(true);

        Map<String, String> workflowVariables = generateWorkflowVariable(form, formData);

        WorkflowProcessResult processResult = appService.submitFormToStartProcess(appDefinition.getAppId(), appDefinition.getVersion().toString(), processDefId, formData, workflowVariables, null, null);

        if (processResult == null || processResult.getProcess() == null) {
            LogUtil.warn(getClassName(), "Error starting process [" + processDefId + "]");
        } else if (!map.get("resultProcessId").toString().isEmpty()) {
            workflowManager.processVariable(processResult.getProcess().getInstanceId(), map.get("resultProcessId").toString(), processResult.getProcess().getInstanceId());
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
        return AppUtil.readPluginResource(getClassName(), "/properties/StartFormProcessTool.json", new String[]{StartProcessTool.class.getName(), StartProcessTool.class.getName()}, false, "/messages/StartProcess");
    }

    @Override
    @Nonnull
    public Map<String, String> generateWorkflowVariable(@Nonnull final Form form, @Nonnull final FormData formData) {
        return FormDataUtil.elementStream(form, formData)
                .filter(e -> !e.getPropertyString("workflowVariable").isEmpty())
                .collect(Collectors.toMap(e -> e.getPropertyString("workflowVariable"), e -> {
                    final String parameterName = FormUtil.getElementParameterName(e);
                    return formData.getRequestParameter(parameterName);
                }));
    }
}
