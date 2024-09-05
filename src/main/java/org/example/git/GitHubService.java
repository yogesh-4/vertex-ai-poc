package org.example.git;


import org.apache.commons.lang3.StringUtils;
import org.eclipse.egit.github.core.*;
import org.eclipse.egit.github.core.service.IssueService;
import org.example.jira.JiraFields;
import org.example.jira.JiraService;
import org.example.model.DMLResponse;
import org.example.model.GithubPushSqlFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class GitHubService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);

    @Value("${github.owner}")
    private String owner;

    @Value("${github.target.owner}")
    private String targetOwner;

    @Autowired
    private GithubHelper sourceGitHubHelper;

    @Autowired
    private GithubHelper targetGitHelper;


    @Autowired
    private JiraService jiraService;

    private static final String NEW_LINE = "\n";


    public Map<String,String> fetchGitHubFilesFromCommitHash(JiraFields jiraFields) throws IOException {
        String sqlFilesPath =null;
        String processName = jiraFields.getProcessName();
        String commitHash = jiraFields.getCommitHash();

        List<GithubFile> sqlFiles = sourceGitHubHelper.getFiles(owner, "complaints360", sqlFilesPath, commitHash);
        Map<String, String> sqlFilesDecodedMap = new HashMap<>();
        sqlFiles.forEach(githubFile -> {
            byte[] contentDecoded = Base64.getDecoder().decode(githubFile.getEncodedBase64Content().replace("\n", "").getBytes());
            sqlFilesDecodedMap.put(githubFile.getName(), new String(contentDecoded, StandardCharsets.UTF_8));
        });
        try {
            removeDataSetCatalogue(processName, sqlFilesDecodedMap);
        }catch (Exception e){
            logger.error("Error occurred during git persist:",e.fillInStackTrace());
        }
        return sqlFilesDecodedMap;
    }

    private void removeDataSetCatalogue(String processName,Map<String,String> sqlMap) throws Exception {
         String ddl = sqlMap.get("ddl.sql");
         String dml = sqlMap.get("dml.sql");

        //logger.info("PROD DDL code : {}", prodSQLs);
        DMLResponse response = new DMLResponse();
        try {
            //DMLResponse dmlRes = bigQueryParserService.getPipelineDependencies(sqlMap.get("dml.sql"));
            //Persisting DDL files
            //Persisting DML files from dependency graph
            String[] dmlArray = dml.split("\\;");
            Map<String,String> verificationDMLMap = new HashMap<>();
            String sql_placeholder = "sql";
            int index = 0;
            HashMap<Integer, Set<Integer>> dependencyGraph = new HashMap<>();
            List<Integer> executionOrder = new ArrayList<>();
            HashMap<Integer,String> sqlMapping = new HashMap<>();
            Map<Integer,String> indexTableMap = new HashMap<>();
            indexTableMap.put(2,"ds_gads.complaints_tracking");
            indexTableMap.put(1,"ds_gads.company_category");
            indexTableMap.put(0,"ds_gads.w_company_score");
            indexTableMap.put(3,"ds_gads.company_360");
            dependencyGraph.put(2,new HashSet<>(Arrays.asList()));
            dependencyGraph.put(3,new HashSet<>(Arrays.asList(2,1)));
            dependencyGraph.put(0,new HashSet<>(Arrays.asList()));
            dependencyGraph.put(1,new HashSet<>(Arrays.asList(0)));
            executionOrder.add(0);
            executionOrder.add(1);
            executionOrder.add(2);
            executionOrder.add(3);
            sqlMapping.put(0,"insert ds_gads.w_company_score\n" +
                    "SELECT\n" +
                    "    complaints.company_name,\n" +
                    "    count(CASE\n" +
                    "      WHEN complaints.timely_response THEN 1\n" +
                    "      ELSE CAST(NULL as INT64)\n" +
                    "    END) AS timely_response_count,\n" +
                    "    count(*) AS total_complaints\n" +
                    "  FROM\n" +
                    "    `dk4learning-433311.ds_ent.complaints` AS complaints\n" +
                    "  GROUP BY 1;");
            sqlMapping.put(1,"insert into ds_gads.company_category\n" +
                    "SELECT\n" +
                    "    intermediate_table.company_name,\n" +
                    "    CASE\n" +
                    "      WHEN intermediate_table.timely_response_count / intermediate_table.total_complaints >= 0.8 THEN 'good'\n" +
                    "      WHEN intermediate_table.timely_response_count / intermediate_table.total_complaints >= 0.5 THEN 'average'\n" +
                    "      ELSE 'poor'\n" +
                    "    END AS category,\n" +
                    "    current_timestamp()\n" +
                    "  FROM\n" +
                    "    ds_gads.w_company_score as intermediate_table;");
            sqlMapping.put(2,"insert into ds_gads.complaints_tracking\n" +
                    "SELECT\n" +
                    "    company.company_name,\n" +
                    "    count(CASE\n" +
                    "      WHEN DATE_DIFF(CURRENT_DATE(), complaints.date_sent_to_company, DAY) <= 30 THEN 1\n" +
                    "      ELSE CAST(NULL as INT64)\n" +
                    "    END) AS complaints_30_days,\n" +
                    "    count(CASE\n" +
                    "      WHEN DATE_DIFF(CURRENT_DATE(), complaints.date_sent_to_company, DAY) <= 90 THEN 1\n" +
                    "      ELSE CAST(NULL as INT64)\n" +
                    "    END) AS complaints_90_days,\n" +
                    "    count(CASE\n" +
                    "      WHEN DATE_DIFF(CURRENT_DATE(), complaints.date_sent_to_company, DAY) <= 180 THEN 1\n" +
                    "      ELSE CAST(NULL as INT64)\n" +
                    "    END) AS complaints_180_days,\n" +
                    "    count(CASE\n" +
                    "      WHEN DATE_DIFF(CURRENT_DATE(), complaints.date_sent_to_company, DAY) <= 365 THEN 1\n" +
                    "      ELSE CAST(NULL as INT64)\n" +
                    "    END) AS complaints_365_days,\n" +
                    "    CURRENT_DATETIME() as complaints_timestamp\n" +
                    "  FROM\n" +
                    "    `dk4learning-433311.ds_ent.company` AS company\n" +
                    "    left JOIN `dk4learning-433311.ds_ent.complaints` AS complaints ON company.company_name = complaints.company_name\n" +
                    "  GROUP BY 1;\n");

            sqlMapping.put(3,"insert into ds_gads.company_360\n" +
                    "SELECT\n" +
                    "    complaints_tracking.company_name,\n" +
                    "    company_category.category,\n" +
                    "    complaints_tracking.complaints_30_days,\n" +
                    "    complaints_tracking.complaints_90_days,\n" +
                    "    complaints_tracking.complaints_180_days,\n" +
                    "    complaints_tracking.complaints_365_days,\n" +
                    "    complaints_tracking.complaints_timestamp\n" +
                    "    \n" +
                    "  FROM\n" +
                    "    `dk4learning-433311.ds_gads.complaints_tracking` AS complaints_tracking\n" +
                    "    LEFT OUTER JOIN `dk4learning-433311.ds_gads.company_category` AS company_category ON complaints_tracking.company_name = company_category.company_name;");



            for(String sql :dmlArray) {
                verificationDMLMap.put(sql_placeholder+index, sql);
                //executionOrder.add(index);
                //sqlMapping.put(index,)
            }

            response.setDependencyGraph(dependencyGraph);
            response.setExecutionOrder(executionOrder);
            response.setIndexTableMap(indexTableMap);
            response.setSqlMapping(sqlMapping);
            persistFiles(processName, "ynandhagopal", sqlMap);
        }catch(Exception e){
            logger.error("Error occurred in persisting ",e.fillInStackTrace());
            persistFiles(processName, "ynandhagopal", response);

        }

    }

    public Response persistFiles(String processName, String adminUsername, Map<String,String> sqlMap) throws Exception {
        logger.info("Starting to persist files for processName : {}", processName);
        String mergeCommit = "";

        String commitMessage = createCommitMessage(processName, adminUsername,true);

        //check or create this processName repo is already present or not.
        Repository repository = getRepository(processName);


        List<RepositoryContents> repoContents = createDataFormGitStructureForSQL(sqlMap, processName);

        String adminUserName = "ynandhagopal";

        GithubPushSqlFiles gitHubPushFiles = createGitHubFile(repoContents, processName, commitMessage, adminUserName);

        //Create repo , Push to the master , raise PR and merge pr.
        try {
            mergeCommit = startActualGitPushProcess(commitMessage, gitHubPushFiles, repository, processName, true);
        }catch (Exception e){
            logger.error("Error occurred during merging",e.fillInStackTrace());
        }

        logger.info("Merge commit {}", mergeCommit);
        return Response.ok(mergeCommit).build();
    }

    public Response persistFiles(String processName, String adminUsername, DMLResponse dmlResponse) throws Exception {
        logger.info("Starting to persist files for processName : {}", processName);
        String mergeCommit = "";

        String commitMessage = createCommitMessage(processName, adminUsername,false);

        //check or create this processName repo is already present or not.
        Repository repository = getRepository(processName);


        List<RepositoryContents> repoContents = createDataFormGitStructureForSQL(dmlResponse, processName);

        String adminUserName = "ynandhagopal";

        GithubPushSqlFiles gitHubPushFiles = createGitHubFile(repoContents, processName, commitMessage, adminUserName);

        //Create repo , Push to the master , raise PR and merge pr.
        mergeCommit = startActualGitPushProcess(commitMessage, gitHubPushFiles, repository, processName, false);

        logger.info("Merge commit {}", mergeCommit);
        return Response.ok(mergeCommit).build();
    }

    private String createCommitMessage(String processName, String adminUsername,boolean isDDL) {
        StringBuilder message = new StringBuilder("Name : " + processName);
        String sql = isDDL?"DDL":"DML";
        message.append(sql).append(" files are uploaded").append("\nDeployer : ").append(adminUsername)
                .append("\n*************************************\n");
        return message.toString();
    }

    private Repository getRepository(String processName) throws IOException {
        Repository repository = this.targetGitHelper.fetchRepository(processName, targetOwner);

        if (Objects.isNull(repository)){
            logger.info("Repo is not present in organization : " + targetOwner);
            logger.info("Creating new Repo ...");
            repository = this.targetGitHelper.createRepository(processName, targetOwner, getRepoDescription(processName));
        }else{
            //check if main branch is already present if not create the main branch
            /*Boolean branchExists = githubHelper.checkIfBranchExists(GithubHelper.MAIN, repository);
            if (!branchExists) {
                logger.info("Creating MAIN branch.....");
                this.githubHelper.createBranch(repository, GithubHelper.MAIN);
                logger.info("MAIN branch is created successfully.....");
            }*/
            logger.info("Checking if DDL_PROD branch already exists.....");
            Boolean ddlBranchExists = targetGitHelper.checkIfBranchExists(GithubHelper.DDL_PROD, repository);
            if (!ddlBranchExists) {
                logger.info("Creating DDL_PROD branch.....");
                this.targetGitHelper.createBranch(repository, GithubHelper.DDL_PROD);
                logger.info("DDL_PROD branch is created successfully.....");
            }
            Boolean dmlBranchExists = targetGitHelper.checkIfBranchExists(GithubHelper.DML_PROD, repository);
            logger.info("Checking if DML_PROD branch already exists.....");
            if (!dmlBranchExists) {
                logger.info("Creating DML_PROD branch.....");
                this.targetGitHelper.createBranch(repository, GithubHelper.DML_PROD);
                logger.info("DML_PROD branch is created successfully.....");
            }


        }
        return repository;
    }

    private String getRepoDescription(String processName) {
        return " This repo is created for persisting DDL and DML with SQLx files";
    }

    /*private GithubPushFiles createGitHubFile(String sql, String processName, String commitMessage, String authorName) throws IOException {
        GithubPushFiles githubPushFiles = new GithubPushFiles();
        String commitMessagePRM = "";
        List<RepositoryContents> githubFiles = new ArrayList<>();
        for (DeploymentObject deploymentObject : roeDeploymentObjects.getDeploymentObjects()){

            if (deploymentObject.getPath().endsWith(PRM)){
                commitMessagePRM = new String(deploymentObject.getContents());
                continue;
            }

            RepositoryContents gitFile = new RepositoryContents();
            gitFile.setContent(new String(sql.getBytes(), StandardCharsets.UTF_8));
            gitFile.setPath("/definitions");
            githubFiles.add(gitFile);
        }
        githubPushFiles.setGithubFiles(githubFiles);
        githubPushFiles.setRepo(processName);
        githubPushFiles.setOwner(owner);
        githubPushFiles.setCommitMessage(commitMessage + "\n" + commitMessagePRM);
        githubPushFiles.setAuthorName(authorName);
        return githubPushFiles;
    }*/

    private List<RepositoryContents> createDataFormGitStructureForSQL(Map<String,String> sqls,String processName) throws IOException {
        List<RepositoryContents> repositoryContents = new ArrayList<>();
        for(String fileName: sqls.keySet()) {
            if(fileName.equals("dml.sql"))
                continue;
            RepositoryContents gitFile = new RepositoryContents();
            gitFile.setContent(sqls.get(fileName));
            //gitFile.setName(processName+"_ddl");
            gitFile.setPath("definitions/"+processName+"_ddl.sql");
            gitFile.setType("file");
            repositoryContents.add(gitFile);
        }
        /*RepositoryContents gitFile = new RepositoryContents();
        StringBuilder sb = new StringBuilder();
        sb.append(formConfiguration());
        sb.append(NEW_LINE);
        sb.append(NEW_LINE);
        sb.append(sqls.get("ddl.sql"));
        gitFile.setName(processName+".sqlx");
        gitFile.setContent(sb.toString());
        gitFile.setType("file");
        gitFile.setPath("definitions/"+processName+".sqlx");
        repositoryContents.add(gitFile);

        RepositoryContents gitYaml = new RepositoryContents();
        gitYaml.setContent(formWorkFlowYaml());
        gitYaml.setType("file");
        gitYaml.setPath("workflow.yaml");
        repositoryContents.add(gitYaml);*/

        return repositoryContents;
    }

    private List<RepositoryContents> createDataFormGitStructureForSQL(DMLResponse dmlResponse, String processName) {
        List<RepositoryContents> repositoryContents = new ArrayList<>();
        HashMap<Integer, String> sqlMapping = dmlResponse.getSqlMapping();
        HashMap<Integer, Set<Integer>> dependencyGraph = dmlResponse.getDependencyGraph();
        List<Integer> execOrder = dmlResponse.getExecutionOrder();
        Map<Integer, String> indexTableMap = dmlResponse.getIndexTableMap();
        for(int order: execOrder) {
            StringBuilder content = new StringBuilder();
            if(CollectionUtils.isEmpty(dependencyGraph.get(order))) {
                logger.info("No dependency found.May be Source node:");
            }else{
                Set<String> dependencies = dependencyGraph.get(order).stream().map(depIndex ->
                        {
                            String tableName = fetchTableFromFullName(indexTableMap.get(depIndex));
                            return depIndex +"_"+ tableName;
                        })
                        .collect(Collectors.toSet());
            }
            content.append(NEW_LINE);
            String sqlContent = sqlMapping.get(order);
            content.append(sqlContent);
            RepositoryContents gitFile = new RepositoryContents();
            gitFile.setContent(content.toString());
            gitFile.setPath("definitions/"+order+"_"+fetchTableFromFullName(indexTableMap.get(order))+".sql");
            gitFile.setType("file");
            repositoryContents.add(gitFile);
        }

        return repositoryContents;
    }

    private String fetchTableFromFullName(String fullTableName) {
        String[] tableArray = fullTableName.split("\\.");
        return tableArray[tableArray.length-1];
    }


    private GithubPushSqlFiles createGitHubFile(List<RepositoryContents> githubFiles, String processName, String commitMessage, String authorName) throws IOException {
        GithubPushSqlFiles githubPushFiles = new GithubPushSqlFiles();

        githubPushFiles.setGithubFiles(githubFiles);
        githubPushFiles.setRepo(processName);
        githubPushFiles.setOwner(targetOwner);
        githubPushFiles.setCommitMessage(commitMessage);
        githubPushFiles.setAuthorName(authorName);
        return githubPushFiles;
    }

    private String sanitizePath(String path) {

        if (path.startsWith("/")){
            return path.substring(1);
        }
        return path;
    }


    private String startActualGitPushProcess(String commitMessage, GithubPushSqlFiles githubPushFiles, IRepositoryIdProvider repositoryIdProvider, String processName, boolean isDDL) throws Exception {
        String branchName = isDDL?GithubHelper.DDL_PROD:GithubHelper.DML_PROD;
        logger.info("Starting to push files.");
        MergeStatus mergeStatus;
        String masterCommitHash = this.targetGitHelper.updateFiles(githubPushFiles, (Repository) repositoryIdProvider,branchName);
        logger.info("Push to master branch is done for processName : " + processName);
        if (StringUtils.isNotBlank(masterCommitHash)) {
            //comment in JIRA
            //jiraService.addComment("DataForm files been pushed to branch "+branchName+" with commit hash "+masterCommitHash);
            Boolean branchExists = targetGitHelper.checkIfBranchExists(GithubHelper.RELEASE, repositoryIdProvider);
            if (!branchExists){
                this.targetGitHelper.createBranch((Repository) repositoryIdProvider,GithubHelper.RELEASE);
                logger.info("PR is not needed as this branch is not yet created. so master and release branch will have same commit.");
                return masterCommitHash;
            }

            logger.info("Checking if there is any existing PR.");
            PullRequest pullRequest = this.targetGitHelper
                    .checkIfPRExists((Repository) repositoryIdProvider, IssueService.STATE_OPEN, GithubHelper.MAIN,branchName);

            if (Objects.isNull(pullRequest)) {
                logger.info("PR does not exist. Starting to create PR for processName : " +  processName);
                pullRequest = this.targetGitHelper.createPullRequest( GithubHelper.MAIN,branchName, processName, targetOwner, getTitle(processName),
                        "Auto merge from Master to Release branch.");
            } else {
                logger.info("Existing PR number : " + pullRequest.getNumber());
            }
            logger.info("PR for process : " + processName + " with pull number : " + pullRequest.getId());

            logger.info("Starting to merge pull request : " + pullRequest.getNumber());
            mergeStatus = this.targetGitHelper.mergePullRequest(pullRequest,
                    this.targetGitHelper.fetchRepository(processName, targetOwner),
                    commitMessage);

            if (Objects.isNull(mergeStatus)){
                throw new Exception("Please merge pull request for processName : "
                        + processName + " manually. Since auto merged failed.");
            }

            logger.info("Merge is done for pull number : " + pullRequest.getNumber() + " with commithash : " + mergeStatus.getSha());
        }
        else
            throw new Exception("Please manually push code to Master branch and raise PR to release. Since Auto push failed");
        return mergeStatus.getSha();
    }

    private String getTitle(String processName) {
        return "Process : " + processName ;

    }

    /*private String persisToReleaseBranch(String adminUsername,String processName,String commitMessage,int persistCount) throws Exception {
        String mergeCommit = "";
        try {
            if(persistCount >= 2){
                logger.info("Persist count exceeded maximum limit. Please try resolving manually..");
                throw new Exception("Merge Conflicts occurred.Please resolve conflicts and try again");
            }
            //check or create this processName repo is already present or not.
            Repository repository = getRepository(processName);

            GithubPushFiles githubPushFiles =  createGitHubFile(deploymentObjects, roeProcess.getProcessName(), commitMessage,adminUsername);

            //Create repo , Push to the master , raise PR and merge pr.
            mergeCommit = startActualGitPushProcess( commitMessage, githubPushFiles, repository,roeProcess.getProcessName());

        }catch(MergeException e){
            logger.info("Conflict occurred during merging PR.Trying to resolve..");
            mergeCommit = persisToReleaseBranch(adminUsername,roeProcess,deploymentObjects,commitMessage,persistCount+1);
            logger.info("Merge conflicts resolved...");
        }
        return mergeCommit;
    }*/

    private String formConfiguration() {
        return "config { type:\"operations\" }";
    }

    private String formConfiguration(Set<String> dependencies) {
        StringBuilder depConfigString =  new StringBuilder();
        depConfigString.append("config { type:\"operations\", \n" +
                "dependencies:[");
        int i=0;
        for(String dependency:dependencies ) {
            depConfigString.append("\"").append(dependency).append("\"");
            if (i != dependencies.size() - 1)
                depConfigString.append(",");
            i++;
        }

        depConfigString.append("]\n}");
        return depConfigString.toString();
    }

    private String formWorkFlowYaml() {
        return "defaultProject: dk4learning-433311\n"+
        "defaultLocation: northamerica-northeast1\n"+
        "defaultDataset: ds_gads\n"+
        "defaultAssertionDataset: dataform_assertions\n"+
        "dataformCoreVersion: 3.0.0";
    }




}
