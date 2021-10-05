package com.kinnara.kecakplugins.startprocess;

import com.kinnara.kecakplugins.startprocess.commons.StartProcessException;
import com.kinnara.kecakplugins.startprocess.commons.StartProcessUtils;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.LinkButton;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowProcessResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StartProcessFormButton extends LinkButton implements StartProcessUtils {
    @Override
    public FormData actionPerformed(Form form, FormData formData) {
        try {
            WorkflowProcessResult workflowProcessResult = startProcess(getProcessId(), getWorkflowVariables());
            String activityId = Optional.of(workflowProcessResult)
                    .map(WorkflowProcessResult::getActivities)
                    .map(Collection::stream)
                    .orElseGet(Stream::empty)
                    .findFirst()
                    .map(WorkflowActivity::getId)
                    .orElse("");

            Element element = FormUtil.findElement(getFieldFormProcessId(), form, formData);
            if(element != null) {
                formData.addRequestParameterValues(FormUtil.getElementParameterName(element), new String[]{activityId});
            }

        } catch (StartProcessException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return super.actionPerformed(form, formData);
    }

    @Override
    public String getName() {
        return getLabel();
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
        return "Start Process Form Button";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/StartProcessFormButton.json", null, false, "/messages/StartProcess");
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
}
