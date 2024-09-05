package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.git.GitHubService;
import org.example.git.GithubHelper;
import org.example.jira.JiraFields;
import org.example.jira.JiraService;
import org.example.model.FeatureResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FeatureService {

    @Autowired
    JiraService jiraService;

    private static final Logger logger = LoggerFactory.getLogger(FeatureService.class);


    @Autowired
    GitHubService gitHubService;


    public FeatureResponse prelimCheck()  {
        FeatureResponse response = new FeatureResponse();
        List<String> issues= jiraService.fetchJiraStatus("PRELIMINARY CHECK");
        for(String issue:issues) {
            if(!"SCRUM-18".equals(issue))
                continue;
            //Read commit hash from JIRA
            try {
                JiraFields jiraResponse = jiraService.fetchCommitHashFromJira(issue);
                //Using commit go to git and fetch sqls
                gitHubService.fetchGitHubFilesFromCommitHash(jiraResponse);
            } catch (Exception e) {
                logger.error("Error occurred in prelim check", e.fillInStackTrace());
            }
        }

        //form dependency graph

        return response;
    }


}
