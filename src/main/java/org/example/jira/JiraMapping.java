package org.example.jira;

import java.util.HashMap;
import java.util.Map;

public class JiraMapping {

    public static Map<String,String> customFieldMapping  = new HashMap<>();

    public static final Map<String, String> CUSTOMFIELDS_GLOBAL = new HashMap<>();
    public static final String GIT_Commithash = "commitHash";

    static {
        CUSTOMFIELDS_GLOBAL.put(GIT_Commithash, "customfield_10056");
        //CUSTOMFIELDS_GLOBAL.put(DAG Name (DDL), "customfield_21355");
//        CUSTOMFIELDS_GLOBAL.put("BU name", "customfield_20549");
//        CUSTOMFIELDS_GLOBAL.put("initiativeId", "customfield_20654");
    }

}
