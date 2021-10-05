package com.kinnara.kecakplugins.startprocess;

import com.kinnara.kecakplugins.startprocess.commons.StartProcessException;
import com.kinnara.kecakplugins.startprocess.commons.StartProcessUtils;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.form.model.Form;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowProcessResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
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
            label = "Hyperlink";
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
        return null;
    }

    @Override
    public String getHrefColumn() {
        return null;
    }

    protected String getParameterAssignment() {
        return getPropertyString("parameterAssignment");
    }

    @Override
    public String getConfirmation() {
        return getPropertyString("confirmation");
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        try {
            WorkflowProcessResult workflowProcessResult = startProcess(getProcessId(), getWorkflowVariables());

            Form form = generateForm(getFormDefId());
            if(form != null) {
                Optional.ofNullable(rowKeys)
                        .map(Arrays::stream)
                        .orElseGet(Stream::empty)
                        .forEach(s -> updateFormField(form, s, getFieldFormProcessId(), workflowProcessResult.getProcess().getInstanceId()));
            }

            DataListActionResult result = new DataListActionResult();
            result.setType(DataListActionResult.TYPE_REDIRECT);
            Optional.of(workflowProcessResult)
                    .map(WorkflowProcessResult::getActivities)
                    .map(Collection::stream)
                    .orElseGet(Stream::empty)
                    .findFirst()
                    .map(WorkflowActivity::getId)
                    .ifPresent(s -> result.setUrl(constructHref(s)));

            return result;

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
        return getClass().getPackage().getImplementationVersion();
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

    protected Map<String, String> getWorkflowVariables() {
        return Optional.of("workflowVariables")
                .map(this::getProperty)
                .map(o -> (Object[])o)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(o -> (Map<String, Object>)o)
                .collect(Collectors.toMap(m -> AppUtil.processHashVariable(m.get("name").toString(), null, null, null), m -> AppUtil.processHashVariable(m.get("value").toString(), null, null, null)));
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
}
