package com.kinnara.kecakplugins.startprocess;

import com.kinnara.kecakplugins.startprocess.commons.StartProcessException;
import com.kinnara.kecakplugins.startprocess.commons.StartProcessUtils;
import com.kinnarastudio.commons.Try;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.service.DataListService;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Start process several times based on datalist entry
 */
public class DataListStartProcessTool extends DefaultApplicationPlugin implements StartProcessUtils {
    @Override
    public String getName() {
        return "DataList Start Process Tool";
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
        final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        final WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

        final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        final PackageDefinition packageDefinition = appDefinition.getPackageDefinition();

        final String processDefId = AppUtil.getProcessDefIdWithVersion(packageDefinition.getAppId(), packageDefinition.getVersion().toString(), properties.get("processId").toString());

        final String loginAs = properties.get("loginAs") == null || properties.get("loginAs").toString().isEmpty() ? "" : properties.get("loginAs").toString();

        try {
            final DataList dataList = generateDataList(getPropertyString("dataListId"), workflowAssignment);
            final Map<String, List<String>> filters = getPropertyDataListFilter(this, workflowAssignment);
            getCollectFilters(dataList, filters);
            final DataListCollection<Map<String, Object>> rows = Optional.of(dataList)
                    .map(DataList::getRows)
                    .orElseGet(DataListCollection::new);

            rows.forEach(Try.onConsumer(row -> {
                final Map<String, String> workflowVariables = Arrays.stream(((Object[]) properties.get("workflowVariables")))
                        .map(o -> (Map<String, Object>) o)
                        .collect(Collectors.toMap(m -> m.get("variable").toString(), Try.onFunction(m -> {
                            final String field = m.get("field").toString();

                            if (!field.isEmpty()) {
                                return row.get(field).toString();
                            } else {
                                final String value = m.get("value").toString();
                                return value;
                            }
                        })));

                final WorkflowProcessResult result = workflowManager.processStart(processDefId, workflowVariables, loginAs);

                if (result == null || result.getProcess() == null) {
                    throw new StartProcessException("Error starting process [" + processDefId + "]");
                }

                LogUtil.info(getClassName(), "New process [" + result.getProcess().getInstanceId() + "] has been started");

            }));
        } catch (StartProcessException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return null;
    }

    @Override
    public String getLabel() {
        return "DataList Start Process Tool";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        final String className = getClassName();
        return AppUtil.readPluginResource(getClass().getName(), "/properties/DataListStartProcessTool.json", new String[] {className, className}, true, "/messages/StartProcess");
    }
}
