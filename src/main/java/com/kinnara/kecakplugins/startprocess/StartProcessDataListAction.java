package com.kinnara.kecakplugins.startprocess;

import com.kinnara.kecakplugins.startprocess.commons.StartProcessException;
import com.kinnara.kecakplugins.startprocess.commons.StartProcessUtils;
import com.kinnarastudio.commons.Try;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.form.model.Form;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowProcessResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 *
 * Trigger new process based on current
 */
public class StartProcessDataListAction extends DataListActionDefault implements StartProcessUtils {
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
        return getPropertyString("target");
    }

    @Override
    public String getHrefParam() {
        return getPropertyString("hrefParam");
    }

    @Override
    public String getHrefColumn() {
        return getPropertyString("hrefColumn");
    }

    protected String getParameterAssignment() {
        return getPropertyString("parameterAssignment");
    }

    @Override
    public String getConfirmation() {
        return getPropertyString("confirmation");
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, @Nullable String[] rowKeys) {
        try {
            @Nullable final Form form = generateForm(getFormDefId());

            final DataListCollection<Map<String, String>> rows = dataList.getRows(Integer.MAX_VALUE, 0);
            rows.sort(Comparator.comparing(m -> m.get(dataList.getBinder().getPrimaryKeyColumnName())));

            final Set<DataListActionResult> results = Optional.ofNullable(rowKeys)
                    .map(Arrays::stream)
                    .orElseGet(Stream::empty)
                    .map(Try.onFunction(key -> {
                        Map<String, Object> row = getRow(dataList, rows, key);
                        WorkflowProcessResult workflowProcessResult = startProcess(getProcessId(), getWorkflowVariables(row));

                        if(form != null) {
                            updateFormField(form, key, getFieldFormProcessId(), workflowProcessResult.getProcess().getInstanceId());
                        }

                        DataListActionResult result = new DataListActionResult();
                        result.setType(DataListActionResult.TYPE_REDIRECT);
                        Optional.of(workflowProcessResult)
                                .map(WorkflowProcessResult::getActivities)
                                .map(Collection::stream)
                                .orElseGet(Stream::empty)
                                .filter(Objects::nonNull)
                                .findFirst()
                                .map(WorkflowActivity::getId)
                                .ifPresent(s -> result.setUrl(constructHref(s)));

                        return result;
                    }))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            return results.stream().findFirst().orElse(null);

        } catch (StartProcessException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return null;
        }
    }

    @Override
    public String getName() {
        return getLabel() + getVersion();
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
        return "Start Process";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/StartProcessDataListAction.json", null, true, "/messages/StartProcess");
    }

    protected String getFormDefId() {
        return getPropertyString("formDefId");
    }

    protected String getProcessId() {
        return getPropertyString("processId");
    }

    protected String getFieldFormProcessId() {
        return getPropertyString("fieldToStoreProcessId");
    }

    protected Map<String, String> getWorkflowVariables(Map<String, Object> row) {
        return Optional.of("workflowVariables")
                .map(this::getProperty)
                .map(o -> (Object[])o)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(o -> (Map<String, Object>)o)
                .collect(Collectors.toMap(m -> AppUtil.processHashVariable(String.valueOf(m.get("name")), null, null, null), m -> {
                    final String field = String.valueOf(m.get("field"));

                    if(!field.isEmpty()) {
                        final String rowValue = String.valueOf(row.getOrDefault(field, ""));
                        return rowValue;
                    } else {
                        final String value = String.valueOf(m.get("value"));
                        return AppUtil.processHashVariable(value, null, null, null);
                    }
                }));
    }

    /**
     * Constuct href
     *
     * @return
     */
    protected String constructHref(String assignmentId) {
        StringBuilder url = new StringBuilder(getHref());

        if(!getParameterAssignment().isEmpty()) {
            url.append(getUrlSeparator(url.toString()))
                    .append(getParameterAssignment())
                    .append("=")
                    .append(assignmentId);
        }

        LogUtil.info(getClassName(), "constructHref : url ["+url.toString()+"]");

        return url.toString();
    }

    protected String getUrlSeparator(String url) {
        return url.contains("?") ? "&" : "?";
    }

    @Nonnull
    protected Map<String, Object> getRow(DataList dataList, DataListCollection rows, String key) {
        final String keyField = dataList.getBinder().getPrimaryKeyColumnName();
        return Optional.ofNullable(rows)
                .map(DataListCollection<Map<String, Object>>::stream)
                .orElseGet(Stream::empty)
                .filter(row -> {
                    final String primaryKey = String.valueOf(row.get(keyField));
                    return key.equals(primaryKey);
                })
                .findFirst()
                .orElseGet(HashMap::new);



    }
}
