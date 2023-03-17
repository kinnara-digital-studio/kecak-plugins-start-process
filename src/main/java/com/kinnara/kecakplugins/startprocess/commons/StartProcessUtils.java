package com.kinnara.kecakplugins.startprocess.commons;

import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.*;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListFilter;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.workflow.lib.AssignmentCompleteButton;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface StartProcessUtils extends PluginWebSupport {
    default void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
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
            Optional<String> optAppId = optParameter(request, "appId");

            final AppDefinition appDef;
            if(optAppId.isPresent()) {
                final String appId = optAppId.get();
                final String appVersion = String.valueOf(appDefinitionDao.getPublishedVersion(appId));
                appDef = appService.getAppDefinition(appId, appVersion);
            } else {
                appDef = AppUtil.getCurrentAppDefinition();
            }

            try {
                JSONArray jsonArray = new JSONArray();
                PackageDefinition packageDefinition = appDef.getPackageDefinition();
                Long packageVersion = (packageDefinition != null) ? packageDefinition.getVersion() : new Long(1);
                Collection<WorkflowProcess> processList = workflowManager.getProcessList(appDef.getAppId(), packageVersion.toString());

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

    @Nullable
    default Form generateForm(String formDefId) throws StartProcessException {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();

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
                return form;
            }
        }

        return null;
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
        @Nullable PackageActivityForm packageActivityForm = Optional.ofNullable(appService.viewStartProcessForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), processDefId, null, ""))
                .orElse(null);

        if(packageActivityForm == null || packageActivityForm.getForm() == null) {
            return Optional.of(processDefId)
                .map(s -> {
                    LogUtil.info(getClass().getName(), "Starting process [" + s + "]");
                    return workflowManager.processStart(s, workflowVariables);
                })
                    .map(workflowProcessResult -> {
                        LogUtil.info(getClass().getName(), "Process [" + workflowProcessResult.getProcess().getInstanceId() + "] has beeen started");
                        return workflowProcessResult;
                    })
                .orElseThrow(() -> new StartProcessException("Error starting process [" + processDefId + "]"));
        } else {
            final FormData formData = formService.retrieveFormDataFromRequestMap(null, new HashMap<>());
            formData.addRequestParameterValues(AssignmentCompleteButton.DEFAULT_ID, new String[]{AssignmentCompleteButton.DEFAULT_ID});

//            Map<String, String> workflowVariables = generateWorkflowVariable(form, formData);

            // trigger run process
            WorkflowProcessResult processResult = appService.submitFormToStartProcess(packageActivityForm, formData, workflowVariables, null);
            return Optional.ofNullable(processResult).orElseThrow(() -> {
                String message = Optional.of(formData)
                        .map(FormData::getFormErrors)
                        .map(Map::entrySet)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .map(e -> String.format("{%s=>%s}", e.getKey(), e.getValue()))
                        .collect(Collectors.joining(", "));

                return new StartProcessException("Error starting process [" + packageActivityForm.getProcessDefId() + "] message [" + message + "]");
            });

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

    default DataList generateDataList(String datalistId, WorkflowAssignment workflowAssignment) throws StartProcessException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        DataListService dataListService = (DataListService) appContext.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) appContext.getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

        return Optional.ofNullable(datalistDefinition)
                .map(DatalistDefinition::getJson)
                .map(s -> processHashVariable(s, workflowAssignment))
                .map(dataListService::fromJson)
                .orElseThrow(() -> new StartProcessException("DataList [" + datalistId + "] not found"));
    }

    default String processHashVariable(String content, @Nullable WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(content, assignment, null, null);
    }

    default Map<String, List<String>> getPropertyDataListFilter(PropertyEditable obj, WorkflowAssignment workflowAssignment) {
        final Map<String, List<String>> filters = Optional.of("dataListFilter")
                .map(obj::getProperty)
                .map(it -> (Object[]) it)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .map(o -> (Map<String, Object>) o)
                .map(m -> {
                    Map<String, List<String>> map = new HashMap<>();
                    String name = String.valueOf(m.get("name"));
                    String value = Optional.of("value")
                            .map(m::get)
                            .map(String::valueOf)
                            .map(s -> processHashVariable(s, workflowAssignment))
                            .orElse("");

                    map.put(name, Collections.singletonList(value));
                    return map;
                })
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .filter(Objects::nonNull)
                .collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> {
                            List<String> result = new ArrayList<>(e1);
                            result.addAll(e2);
                            return result;
                        })
                );

        return filters;
    }

    /**
     * Get collect filters
     *
     * @param dataList Input/Output parameter
     */
    default void getCollectFilters(@Nonnull final DataList dataList, @Nonnull final Map<String, List<String>> filters) {
        Optional.of(dataList)
                .map(DataList::getFilters)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(f -> Optional.of(f)
                        .map(DataListFilter::getName)
                        .map(filters::get)
                        .map(l -> !l.isEmpty())
                        .orElse(false))
                .forEach(f -> f.getType().setProperty("defaultValue", String.join(";", filters.get(f.getName()))));

        dataList.getFilterQueryObjects();
        dataList.setFilters(null);
    }

    @Nonnull
    default Optional<String> optParameter(HttpServletRequest request, @Nonnull String parameterName) {
        return Optional.of(parameterName).map(request::getParameter).filter(s -> !s.isEmpty());
    }

    @Nonnull
    default String getParameter(HttpServletRequest request, @Nonnull String parameterName) throws StartProcessException {
        return optParameter(request, parameterName)
                .orElseThrow(() -> new StartProcessException("Parameter [" + parameterName + "] is not supplied"));
    }
}
