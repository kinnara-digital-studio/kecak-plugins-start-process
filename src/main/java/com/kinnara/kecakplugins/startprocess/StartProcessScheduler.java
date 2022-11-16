package com.kinnara.kecakplugins.startprocess;

import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessResult;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kecak.apps.app.model.DefaultSchedulerPlugin;
import org.quartz.JobExecutionContext;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

public class StartProcessScheduler extends DefaultSchedulerPlugin implements PluginWebSupport {
    private final DateFormat TIME_FORMAT = new SimpleDateFormat("hh:MM");


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
        return "Start Process Scheduler";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/StartProcessScheduler.json", new String[] {getClassName(), getClassName(), getClassName()}, false, "/messages/StartProcess");
    }

    @Override
    public boolean filter(JobExecutionContext context, Map<String, Object> properties) {
        final Date scheduledFireTime = context.getScheduledFireTime();
        LogUtil.info(getClassName(), "["+context.getFireTime()+"] ["+context.getScheduledFireTime()+"]");
        return Optional.ofNullable(getPropertyString("runScheduleTime"))
                .map(s -> s.split(";"))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(this::parseTime)
                .filter(Objects::nonNull)
                .map(d -> (d.getTime() - scheduledFireTime.getTime()) / (60 * 1000))
                .peek(l -> LogUtil.info(getClassName(), "Diff ["+l+"]"))
                .anyMatch(l -> l == 0);
    }

    private Date parseTime(String time) {
        try {
            return TIME_FORMAT.parse(time);
        } catch (ParseException e) {
            return null;
        }
    }

    @Override
    public void jobRun(@Nonnull JobExecutionContext context, @Nonnull Map<String, Object> properties) {
        AppDefinition appDefinition = (AppDefinition) properties.get("appDefinition");
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        String processDefId = AppUtil.getProcessDefIdWithVersion(appDefinition.getAppId(), appDefinition.getPackageDefinition().getVersion().toString(), properties.get("processId").toString());
        Map<String, String> workflowVariables = Arrays.stream(((Object[]) properties.get("workflowVariables")))
                .map(o -> (Map<String, Object>)o)
                .collect(HashMap::new, (m, o) -> m.put(o.get("variable").toString(), o.get("value").toString()), Map::putAll);

        String loginAs = properties.get("loginAs") == null || properties.get("loginAs").toString().isEmpty() ? "" : properties.get("loginAs").toString();

        // get result and set process id
        WorkflowProcessResult result = workflowManager.processStart(processDefId, workflowVariables, loginAs);

        if(result == null || result.getProcess() == null) {
            LogUtil.warn(getClassName(), "Error starting process ["+processDefId+"]");
        } else if (!properties.get("resultProcessId").toString().isEmpty()){
            workflowManager.processVariable(result.getProcess().getInstanceId(), properties.get("resultProcessId").toString(), result.getProcess().getInstanceId());
        }
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
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
            String appId = request.getParameter("appId");
            String appVersion = String.valueOf(appDefinitionDao.getPublishedVersion(appId));
            try {
                JSONArray jsonArray = new JSONArray();
                AppDefinition appDef = appService.getAppDefinition(appId, appVersion);
                PackageDefinition packageDefinition = appDef.getPackageDefinition();
                Long packageVersion = (packageDefinition != null) ? packageDefinition.getVersion() : new Long(1);
                Collection<WorkflowProcess> processList = workflowManager.getProcessList(appId, packageVersion.toString());

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
        } else if("scheduleTime".equalsIgnoreCase(action)) {
            JSONArray jsonArray = new JSONArray();
            for(int i = 0; i < 24;i++) {
                for(int j = 0; j < 60; j += 30) {
                    String time = String.format("%02d:%02d", i, j);
                    JSONObject jsonObject = new JSONObject();
                    try {
                        jsonObject.put("value", time);
                        jsonObject.put("label", time);
                    } catch (JSONException ignored) {}

                    jsonArray.put(jsonObject);
                }
            }

            try {
                jsonArray.write(response.getWriter());
            } catch (JSONException e) {
                LogUtil.error(this.getClass().getName(), e, "Get Time's options Error!");
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }
}
