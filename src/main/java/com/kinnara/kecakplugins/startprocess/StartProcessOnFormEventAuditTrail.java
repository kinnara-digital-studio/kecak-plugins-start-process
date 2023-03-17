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
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
        final Set<String> methods = getPropertySet("methods");

        if (FormDataDaoImpl.class.getName().equals(clazz) && methods.contains(method)) {
            try {
                final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
                final PackageDefinition packageDefinition = appDefinition.getPackageDefinition();
                final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

                final String processDefId = AppUtil.getProcessDefIdWithVersion(packageDefinition.getAppId(), packageDefinition.getVersion().toString(), properties.get("processId").toString());

                Arrays.stream(auditTrail.getParamTypes()).forEach(c -> LogUtil.info(getClassName(), "getParamTypes [" + c.getName() + "]"));
                final String loginAs = getPropertyString("loginAs");
                final Map<String, String> workflowVariables = Arrays.stream(getPropertyGrid("workflowVariables"))
                        .collect(Collectors.toMap(m -> m.get("name"), m -> {
                            final String field = m.getOrDefault("field", "");
                            if (field.isEmpty()) {
                                final String value = m.get("value");
                                return value;
                            } else {
                                for (int i = 0; i < auditTrail.getParamTypes().length; i++) {
                                    final Class<?> type = auditTrail.getParamTypes()[i];
                                    if (type == FormRowSet.class) {
                                        final FormRowSet rowSet = (FormRowSet) auditTrail.getArgs()[i];
                                        return Optional.ofNullable(rowSet)
                                                .map(Collection::stream)
                                                .orElseGet(Stream::empty)
                                                .findFirst()
                                                .map(r -> r.getProperty(field))
                                                .orElse("");
                                    } else if (type == FormRow.class) {
                                        final FormRow row = (FormRow) auditTrail.getArgs()[i];
                                        return Optional.ofNullable(row)
                                                .map(r -> r.getProperty(field))
                                                .orElse("");
                                    }
                                }

                                return "";
                            }
                        }));

                final WorkflowProcessResult result = workflowManager.processStart(processDefId, workflowVariables, loginAs);
                if (result == null || result.getProcess() == null) {
                    throw new StartProcessException("Error starting process [" + processDefId + "]");
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
}
