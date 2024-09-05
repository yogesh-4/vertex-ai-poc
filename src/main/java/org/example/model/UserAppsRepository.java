package org.example.model;

import org.eclipse.egit.github.core.Repository;


public class UserAppsRepository extends Repository {

    //auto_init

    private boolean autoInit;

    public boolean isAutoInit() {
        return autoInit;
    }

    public void setAutoInit(boolean autoInit) {
        this.autoInit = autoInit;
    }
}
