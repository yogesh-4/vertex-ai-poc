package org.example.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class JiraService {

    @Autowired
    private RestTemplate jiraRestTemplate;

    @Value("${jiraUrl}")
    private String jiraUrl;

    private static final Logger logger = LoggerFactory.getLogger(JiraService.class);

    public JiraFields fetchCommitHashFromJira(String issue){
        String commitHash= "";

        String url = jiraUrl+"/rest/api/3/issue/"+issue;
        JiraFields jiraFields = new JiraFields();
        ResponseEntity<JsonNode> response = jiraRestTemplate.getForEntity(url, JsonNode.class);
        if(response.getBody()!=null){
            logger.info("JIRA response:{} ", response.getBody());
            JsonNode respObject = response.getBody();
            commitHash= respObject.get("fields").get(JiraMapping.CUSTOMFIELDS_GLOBAL.get(JiraMapping.GIT_Commithash)).asText();
            //String processName= respObject.get("fields").get("summary").asText();
            logger.info("Commit Hash: {}", commitHash);
            //logger.info("Process Name: {}", processName);
            jiraFields.setCommitHash(commitHash);
            jiraFields.setProcessName(issue+"_cutomer_360");
            //jiraFields.setProcessName(processName);
        }

        return jiraFields;
    }

    public void addComment(String comment){
        String url = jiraUrl+"/rest/api/2/issue/SCRUM-2/comment";
        ObjectNode jsonObject = JsonNodeFactory.instance.objectNode();
        jsonObject.put("body",comment);
        ResponseEntity<String> jiraResponseEntity = jiraRestTemplate.postForEntity(url, jsonObject, String.class);
        if(jiraResponseEntity.getBody()!=null){
            logger.info("Commented in jira ticket {}",comment);
        }
    }

    public List<String> fetchJiraStatus(String jiraStatus){
        List<String> jiraIssues = new ArrayList<>();
        String url =  jiraUrl+"/rest/api/2/search?jql=project=\"SCRUM\"and(status=\""+jiraStatus+"\")";
        ResponseEntity<JsonNode> response = jiraRestTemplate.getForEntity(url, JsonNode.class);
        if(response.getBody()!=null){
            logger.info("Response : {}", response);
            JsonNode respObject = response.getBody();
              JsonNode issues= respObject.get("issues");
              for(JsonNode issue : issues){
                  jiraIssues.add(issue.get("key").textValue());
              }
        }
        return jiraIssues;
    }
}
