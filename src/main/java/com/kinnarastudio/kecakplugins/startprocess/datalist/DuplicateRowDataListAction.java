package com.kinnarastudio.kecakplugins.startprocess.datalist;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageActivityForm;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.workflow.lib.AssignmentCompleteButton;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nullable;
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
        AppService appService = (AppService) applicationContext.getBean("appService");
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");

        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();

        String originalKey = Optional.ofNullable(rowKeys)
                .filter(keys -> keys.length > 0)
                .map(keys -> keys[0])
                .orElse("");


        final FormData originalFormData = new FormData() {{
            setPrimaryKeyValue(originalKey);
        }};

        final FormData newFormData = new FormData() {{
            addRequestParameterValues(AssignmentCompleteButton.DEFAULT_ID, new String[]{AssignmentCompleteButton.DEFAULT_ID});
        }};

        final Optional<WorkflowProcess> optWorkflowProcess = optProcessByRecordId(originalKey);
        final String processDefId = optWorkflowProcess
                .map(WorkflowProcess::getId)
                .orElse("");
        final String appId = appDefinition.getAppId();
        final String appVersion = appDefinition.getVersion().toString();
        final PackageActivityForm packageActivityForm = appService.viewStartProcessForm(appId, appVersion, processDefId, originalFormData, "");

        final boolean startNewProcess = startNewProcess() && optWorkflowProcess.isPresent();

        @Nullable final Form form;
        if (startNewProcess) {
            form = Optional.ofNullable(packageActivityForm).map(PackageActivityForm::getForm).orElse(null);
        } else if (getFormDefId() != null && !getFormDefId().isEmpty()) {
            form = appService.viewDataForm(appId, appVersion, getFormDefId(), null, null, null, originalFormData, null, null);
        } else {
            form = null;
        }

        if(form == null) throw new UnsupportedOperationException("No form found to duplicate data");

        FormUtil.executeLoadBinders(form, originalFormData);
        FormRowSet originalRowSet = originalFormData.getLoadBinderData(form);

        Set<String> ignores = new HashSet<>() {{
            add(FormUtil.PROPERTY_ID);
            add(FormUtil.PROPERTY_DATE_CREATED);
            add(FormUtil.PROPERTY_DATE_MODIFIED);
            add(FormUtil.PROPERTY_CREATED_BY);
            add(FormUtil.PROPERTY_MODIFIED_BY);
            add(FormUtil.PROPERTY_DELETED);
        }};

        final Map<String, String> workflowVariables = new HashMap<>();

        Optional.ofNullable(originalRowSet)
                .stream()
                .flatMap(Collection::stream)
                .findFirst()
                .map(FormRow::getCustomProperties)
                .map(Map::entrySet)
                .stream()
                .flatMap(Collection<Map.Entry<String, String>>::stream)
                .filter(e -> e.getKey() != null && !ignores.contains(e.getKey()))
                .forEach(e -> {
                    final String fieldName = e.getKey();
                    final String value = e.getValue();

                    final Element element = FormUtil.findElement(fieldName, form, newFormData);
                    if (element == null) return;

                    final String workflowVariable = element.getPropertyString("workflowVariable");
                    workflowVariables.put(workflowVariable, value);

                    final String parameterName = FormUtil.getElementParameterName(element);
                    newFormData.addRequestParameterValues(parameterName, new String[]{value});

                    if (generateChildren()) {
                        FormLoadBinder loadBinder = element.getLoadBinder();
                        FormStoreBinder storeBinder = element.getStoreBinder();

                        if (storeBinder != null) {
                            FormRowSet storeBinderValues;
                            if (loadBinder != null) {
                                storeBinderValues = originalFormData.getLoadBinderData(element);
                            } else {
                                storeBinderValues = new FormRowSet() {{
                                    add(new FormRow() {{
                                        setProperty(fieldName, value);
                                    }});
                                }};
                            }

                            newFormData.setStoreBinderData(storeBinder, storeBinderValues);
                        }
                    }
                });

        if (startNewProcess) {
            final String processId = packageActivityForm.getProcessDefId();
            WorkflowAssignment workflowAssignment = Optional.ofNullable(appService.submitFormToStartProcess(appId, appVersion, packageActivityForm, processId, newFormData, workflowVariables, null))
                    .map(WorkflowProcessResult::getProcess)
                    .map(WorkflowProcess::getInstanceId)
                    .map(workflowManager::getAssignmentByProcess)
                    .orElse(null);
            LogUtil.info(getClassName(), "New assignment [" + workflowAssignment.getActivityId() + "]");
        } else {
            FormData formData = appService.submitForm(form, newFormData, true);
            LogUtil.info(getClassName(), "New record [" + formData.getPrimaryKeyValue() + "]");
        }


        final DataListActionResult result = new DataListActionResult();
        result.setType(DataListActionResult.TYPE_REDIRECT);
        result.setUrl("?");

        return result;
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
        return AppUtil.readPluginResource(getClass().getName(), "/properties/datalist/DuplicateRowDataListAction.json", null, true, "messages/StartProcess");
    }

    @Override
    public Boolean supportList() {
        return false;
    }

    protected Optional<WorkflowProcess> optProcessByRecordId(final String recordId) {
        assert recordId != null;

        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        @Nullable
        WorkflowProcess workflowProcess = Optional.ofNullable(workflowManager.getRunningProcessList(null, null, null, null, recordId, null, null, null, 0, 1))
                .stream()
                .flatMap(Collection::stream)
                .findFirst()
                .orElseGet(() -> Optional.ofNullable(workflowManager.getCompletedProcessList(null, null, null, null, recordId, null, null, null, 0, 1))
                        .stream()
                        .flatMap(Collection::stream)
                        .findFirst()
                        .orElse(null));

        return Optional.ofNullable(workflowProcess);
    }

    protected boolean generateChildren() {
        return "true".equalsIgnoreCase(getPropertyString("generateChildren"));
    }

    protected boolean startNewProcess() {
        return "true".equalsIgnoreCase(getPropertyString("startNewProcess"));
    }

    protected String getFormDefId() {
        return getPropertyString("formDefId");
    }
}
