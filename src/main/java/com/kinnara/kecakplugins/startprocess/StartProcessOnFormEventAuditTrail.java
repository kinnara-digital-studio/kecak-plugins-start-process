package com.kinnara.kecakplugins.startprocess;

import com.kinnara.kecakplugins.startprocess.commons.StartProcessException;
import com.kinnara.kecakplugins.startprocess.commons.StartProcessUtils;
import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.AuditTrail;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDaoImpl;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultAuditTrailPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Triggrer start process during form data event
 */
public class StartProcessOnFormEventAuditTrail extends DefaultAuditTrailPlugin implements StartProcessUtils {
    public final static String LABEL = "Start Process On Form Event";

    public final static Collection<Class> parametersSignature = Arrays.asList(new Class[]{String.class, String.class, FormRowSet.class});

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
    public Object execute(Map properties) {
        final AuditTrail auditTrail = (AuditTrail) properties.get("auditTrail");

        final String clazz = auditTrail.getClazz();
        final String method = auditTrail.getMethod();

        LogUtil.info(getClassName(), "execute : clazz [" + clazz + "] method [" + method + "]");
        final Collection<String> methods = getPropertySet("methods");

        if (FormDataDaoImpl.class.getName().equals(clazz) && methods.contains(method)) {
            Arrays.stream(auditTrail.getArgs()).map(String::valueOf).forEach(s -> LogUtil.info(getClassName(), "args [" + s + "]"));

            try {
                final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
                final PackageDefinition packageDefinition = appDefinition.getPackageDefinition();
                final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

                final Collection<String> formFilter = getFormDefId();

                if (!formFilter.isEmpty()) {
                    final Optional<String> optFormDefId = Optional.ofNullable(auditTrail.getArgs())
                            .map(Arrays::stream)
                            .orElseGet(Stream::empty)
                            .findFirst()
                            .filter(o -> o instanceof String)
                            .map(String::valueOf);

                    if (!optFormDefId.isPresent()) {
                        throw new StartProcessException("Form is not defined in arguments [" + Arrays.toString(auditTrail.getArgs()) + "] filter [" + String.join(";", formFilter) + "]");
                    }

                    if (!formFilter.contains(optFormDefId.get())) {
                        LogUtil.debug(getClassName(), "Skipping form [" + optFormDefId.get() + "]");
                        return null;
                    }
                }

                final String processDefId = AppUtil.getProcessDefIdWithVersion(packageDefinition.getAppId(), packageDefinition.getVersion().toString(), properties.get("processId").toString());

                final Class[] paramTypes = auditTrail.getParamTypes();
                final Object[] args = auditTrail.getArgs();

                final Optional<FormRow> optRow = optRow(paramTypes, args);

                final String loginAs = getPropertyString("loginAs");
                final Map<String, String> workflowVariables = Arrays.stream(getPropertyGrid("workflowVariables"))
                        .map(m -> {
                            final String name = m.get("name");
                            final String field = m.getOrDefault("field", "");

                            final String value;
                            if (field.isEmpty()) {
                                value = ifNull(m.get("value"), "");
                            } else if (optRow.isPresent()) {
                                value = ifNull(optRow.get().getProperty(field), "");
                            } else {
                                LogUtil.warn(getClassName(), "execute : argument is not found for field [" + field + "]");
                                value = "";
                            }

                            return new AbstractMap.SimpleEntry<String, String>(name, value);
                        })
                        .filter(e -> !e.getValue().isEmpty())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (accept, ignored) -> accept));

                final String wfVariableFormDefId = getPropertyString("wfVariableFormDefId");
                if (!wfVariableFormDefId.isEmpty() && args.length > 0) {
                    final String formDefId = String.valueOf(args[0]);
                    LogUtil.debug(getClassName(), "execute : setting formId variable [" + wfVariableFormDefId + "] with [" + formDefId + "]");
                    workflowVariables.put(wfVariableFormDefId, formDefId);
                }

                final String wfVariableFormTable = getPropertyString("wfVariableFormTable");
                if (!wfVariableFormTable.isEmpty() && args.length > 1) {
                    final String tableName = String.valueOf(args[1]);
                    LogUtil.debug(getClassName(), "execute : setting table variable [" + wfVariableFormTable + "] with [" + tableName + "]");
                    workflowVariables.put(wfVariableFormTable, tableName);
                }


                if(isAsynchronous()) {
                    LogUtil.info(getClassName(), "Running start process in background");
                    new Thread(Try.onRunnable(() -> processStart(processDefId, workflowVariables, loginAs)))
                            .start();
                } else {
                    processStart(processDefId, workflowVariables, loginAs);
                }

            } catch (StartProcessException e) {
                LogUtil.error(getClassName(), e, e.getMessage());
            }
        }

        return null;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        final String[] args = new String[]{getClassName(), getClassName(), getClassName(), getClassName()};
        return AppUtil.readPluginResource(getClassName(), "/properties/StartProcessOnFormEventAuditTrail.json", args, true, "/messages/StartProcess");
    }

    protected Map<String, Collection<String>> getMethods() {
        final Map<String, Collection<String>> result = new HashMap<>();

        result.put(FormDataDaoImpl.class.getName(),
                Stream.of("loadWithoutTransaction", "saveOrUpdate", "updateSchema")
                        .collect(Collectors.toList()));

        return result;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            final String action = getParameter(request, "action");

            if (action.equals("methods")) {
                final String className = FormDataDaoImpl.class.getName();
                final JSONArray result = getMethods().entrySet().stream()
                        .filter(e -> className.equals(e.getKey()))
                        .map(Map.Entry::getValue)
                        .flatMap(Collection::stream)
                        .map(Try.onFunction(s -> {
                            final JSONObject json = new JSONObject();
                            json.put("value", s);
                            json.put("label", s);
                            return json;
                        }))
                        .collect(JSONCollectors.toJSONArray());
                result.write(response.getWriter());

            } else {
                StartProcessUtils.super.webService(request, response);
            }
        } catch (StartProcessException | JSONException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    @Nonnull
    public Set<String> getPropertySet(String property) {
        return Arrays.stream(this.getPropertyString(property).split(";")).collect(Collectors.toSet());
    }

    @Nonnull
    public Map<String, String>[] getPropertyGrid(String property) {
        return Optional.ofNullable(getProperty(property))
                .map(o -> (Object[]) o)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(o -> (Map<String, String>) o)
                .toArray(Map[]::new);
    }

    protected Collection<String> getFormDefId() {
        return getPropertySet("formDefId");
    }

    protected Optional<FormRow> optRow(Class[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            final Class<?> type = types[i];
            final Object arg = args[i];

            if (type == FormRowSet.class) {
                final FormRowSet rowSet = (FormRowSet) arg;
                return Optional.ofNullable(rowSet)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .findFirst();
            } else if (type == FormRow.class) {
                final FormRow row = (FormRow) arg;
                return Optional.ofNullable(row);
            }
        }

        return Optional.empty();
    }

    protected String ifNull(String value, String ifNull) {
        return value == null ? ifNull : value;
    }

    protected WorkflowProcessResult processStart(String processDefId, Map<String, String> workflowVariables, String loginAs) throws StartProcessException {
        final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        LogUtil.info(getClassName(), "execute : starting process [" + processDefId + "] variables [" + workflowVariables.entrySet().stream().map(e -> e.getKey() + "->" + e.getValue()).collect(Collectors.joining(";")) + "] loginAs [" + loginAs + "]");

        final WorkflowProcessResult result = workflowManager.processStart(processDefId, workflowVariables, loginAs);

        if (result == null || result.getProcess() == null) {
            throw new StartProcessException("Error starting process [" + processDefId + "]");
        }

        final WorkflowProcess process = result.getProcess();
        LogUtil.info(getClassName(), "Process [" + process.getInstanceId() + "] has been started");

        return result;
    }

    protected boolean isAsynchronous() {
        return "true".equalsIgnoreCase(getPropertyString("asynchronous"));
    }
}
