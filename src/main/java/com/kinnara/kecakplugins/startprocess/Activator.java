package com.kinnara.kecakplugins.startprocess;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(StartProcessTool.class.getName(), new StartProcessTool(), null));
        registrationList.add(context.registerService(StartFormProcessTool.class.getName(), new StartFormProcessTool(), null));
        registrationList.add(context.registerService(StartProcessScheduler.class.getName(), new StartProcessScheduler(), null));
        registrationList.add(context.registerService(StartProcessDataListAction.class.getName(), new StartProcessDataListAction(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}