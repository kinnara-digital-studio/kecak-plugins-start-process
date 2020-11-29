package com.kinnara.kecakplugins.startprocess.commons;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.model.PackageActivityForm;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.workflow.lib.AssignmentCompleteButton;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public interface StartProcessUtils {
    Map<String, Form> formCache = new HashMap<>();

    @Nonnull
    default Form generateForm(String formDefId) throws StartProcessException {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();

        if(formCache.containsKey(formDefId))
            return formCache.get(formDefId);

        // proceed without cache
        ApplicationContext appContext = AppUtil.getApplicationContext();
        FormService formService = (FormService) appContext.getBean("formService");


        if (appDefinition != null && formDefId != null && !formDefId.isEmpty()) {
            FormDefinitionDao formDefinitionDao =
                    (FormDefinitionDao)AppUtil.getApplicationContext().getBean("formDefinitionDao");

            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDefinition);
            if (formDef != null) {
                String json = formDef.getJson();
                Form form = (Form)formService.createElementFromJson(json);

                // put in cache if possible
                formCache.put(formDefId, form);

                return form;
            }
        }

        throw new StartProcessException("Error generating form [" + formDefId + "]");
    }

    @Nonnull
    default WorkflowProcessResult startProcess(String processId, Map<String, String> workflowVariables) throws StartProcessException {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");
        AppService appService = (AppService) applicationContext.getBean("appService");
        FormService formService = (FormService) applicationContext.getBean("formService");

        String processDefId = Optional.of(processId)
                .map(s -> appService.getWorkflowProcessForApp(appDefinition.getAppId(), appDefinition.getVersion().toString(), s))
                .map(WorkflowProcess::getId)
                .orElseThrow(() -> new StartProcessException("Unknown process [" + processId + "]"));

        // check for permission
        if (!workflowManager.isUserInWhiteList(processDefId)) {
            throw new StartProcessException("User [" + WorkflowUtil.getCurrentUsername() + "] is not allowed to start process [" + processDefId + "]");
        }

        // get process form
        PackageActivityForm packageActivityForm = Optional.ofNullable(appService.viewStartProcessForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), processDefId, null, ""))
                .orElseThrow(() -> new StartProcessException("Start Process [" + processDefId + "] has not been mapped to form"));

        Form form = packageActivityForm.getForm();
        if(form == null) {
            return Optional.of(processDefId)
                .map(s -> workflowManager.processStart(s, workflowVariables))
                .orElseThrow(() -> new StartProcessException("Error starting process [" + processDefId + "]"));
        } else {
            final FormData formData = formService.retrieveFormDataFromRequestMap(null, new HashMap<>());
            formData.addRequestParameterValues(AssignmentCompleteButton.DEFAULT_ID, new String[]{AssignmentCompleteButton.DEFAULT_ID});

//            Map<String, String> workflowVariables = generateWorkflowVariable(form, formData);

            // trigger run process
            WorkflowProcessResult processResult = appService.submitFormToStartProcess(packageActivityForm, formData, workflowVariables, null);
            return Optional.ofNullable(processResult).orElseThrow(() -> new StartProcessException("Error starting process [" + packageActivityForm.getProcessDefId() + "]"));

        }
    }

    default FormRow updateFormField(Form form, String primaryKey, String fieldId, String value) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        FormDataDao formDataDao = (FormDataDao) applicationContext.getBean("formDataDao");

        FormRowSet rowSet = new FormRowSet();
        FormRow row = new FormRow();
        row.setId(primaryKey);
        row.setProperty(fieldId, value);
        rowSet.add(row);
        formDataDao.saveOrUpdate(form, rowSet);

        return formDataDao.load(form, primaryKey);
    }

    /**
     * Generate Workflow Variable
     *
     * @param form     Form
     * @param formData Form Data
     * @return
     */
    @Nonnull
    default Map<String, String> generateWorkflowVariable(@Nonnull final Form form, @Nonnull final FormData formData) {
        return formData.getRequestParams().entrySet().stream().collect(HashMap::new, (m, e) -> {
            Element element = FormUtil.findElement(e.getKey(), form, formData, true);
            if (Objects.isNull(element))
                return;

            String workflowVariable = element.getPropertyString("workflowVariable");

            if (workflowVariable.isEmpty())
                return;

            m.put(element.getPropertyString("workflowVariable"), String.join(";", e.getValue()));
        }, Map::putAll);
    }
}
