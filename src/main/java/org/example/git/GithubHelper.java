package org.example.git;


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.egit.github.core.*;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.*;
import org.example.Exception.BranchCreationException;
import org.example.Exception.MergeException;
import org.example.Exception.PullRequestException;
import org.example.Exception.PushException;
import org.example.model.UserAppsRepository;
import org.example.model.GithubPushSqlFiles;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GithubHelper {

    private static final Log logger = LogFactory.getLog(GithubHelper.class);

    private RepositoryService repositoryService;
    private ContentsService contentsService;
    private PullRequestService pullRequestService;
    private DataServiceExtend dataService;
    private CommitService commitService;
    private GitHubClient gitHubClient;
    private UserService userService;
    public static final String MAIN = "main";
    public static final String RELEASE = "release";
    public static final String CLOSED_STATE = "closed";
    public static final String RELEASE_BCK = "release_bck";
    private static final String REFS = "refs";
    private static final String HEADS = "heads";
    private static final String SEPARATOR = "/";
    public static final String RESTART_PERSIST = "restart persist";
    public static final String CONFLICT_ERROR_CODE = "405";
    public static final String EMAIL_SUFFIX = "@paypal.com";
    public static final String MASTER = "master";
    public static final String DDL_PROD = "ddl_prod";
    public static final String DML_PROD = "dml_prod";


    // Default use will be this constructor
    public GithubHelper(String accessToken, String hostName) {
        gitHubClient = new GitHubClient(hostName);
        gitHubClient.setOAuth2Token(accessToken);
        this.repositoryService = new RepositoryService(gitHubClient);
        this.contentsService = new ContentsService(gitHubClient);
        this.pullRequestService = new PullRequestService(gitHubClient);
        this.dataService = new DataServiceExtend(gitHubClient);
        this.commitService = new CommitService(gitHubClient);
        this.userService = new UserService(gitHubClient);
    }

    public GithubHelper() {

    }

    /**
     * Get all files from a specific path in github (based on owner, repo, path and commit hash)
     * Will return all the files
     *
     * @param givenOwner      user/organisation
     * @param givenRepo       name of the repository in github
     * @param givenPath       within the repository
     * @param givenCommitHash immutable, present current state of repo
     * @return The list of github files, with some metadata added
     * @throws IOException
     */
    public List<GithubFile> getFiles(String givenOwner, String givenRepo,
                                     String givenPath, String givenCommitHash) throws IOException {

        logger.info("Start to get files from github using next details Owner: " + givenOwner + " | Repository: " + givenRepo
                + " | Path: " + givenPath + " | Commit Hash: " + givenCommitHash);

        if (givenCommitHash == null || givenCommitHash.isEmpty()) {
            throw new RuntimeException("Commit Hash is missing!");
        }

        List<GithubFile> result = new ArrayList<>();

        Repository repository = repositoryService.getRepository(givenOwner, givenRepo);
        List<RepositoryContents> contents = contentsService.getContents(repository, givenPath, givenCommitHash);
        for (RepositoryContents content : contents) {

            String fileName = content.getName();
            if (isIgnorableFile(fileName)) {
                logger.info("Skipping .DS_Store / .gitignore / README.md file from Github"); // TODO: need to create a black list of files to ignore like gitignore
                continue;
            }

            String contentBase64;

            if (content.getType().equals("file")) {
                if (content.getContent() == null) {
                    contentBase64 = contentsService.getContents(repository, content.getPath(), givenCommitHash).get(0).getContent();
                } else {
                    contentBase64 = content.getContent();
                }

                GithubFile githubFile = new GithubFile();
                githubFile.setName(content.getName());
                githubFile.setPath(content.getPath());
                githubFile.setCommitHash(givenCommitHash);
                githubFile.setOwner(givenOwner);
                githubFile.setRepo(givenRepo);
                githubFile.setEncodedBase64Content(contentBase64);
                result.add(githubFile);

            } else {
                // TODO: handle recursive in future (this is the area for directories in given path
            }
        }

        return result;

    }

    private boolean isIgnorableFile(String fileName) {
        return fileName == null
                || fileName.contains(".DS_Store")
                || fileName.contains(".gitignore")
                || fileName.contains("README.md");
    }


    /**
     * Create repository for given repositoryName, organization with given description.
     *
     * @param repoName
     * @param organization
     * @param description
     * @return Repository
     */
    public Repository  createRepository(String repoName, String organization, String description) {
        UserAppsRepository repository = new UserAppsRepository();
        repository.setName(repoName);
        repository.setDescription(description);
        repository.setMasterBranch(MAIN);
        repository.setAutoInit(true);
        Repository createdRepository = null;
        try {
            //TO create new repo.
            logger.info("Creating new Repository : " + repoName);
            createdRepository = this.repositoryService.createRepository(organization, repository);
            if (!checkIfBranchExists(MAIN, createdRepository))
                createBranch(createdRepository, MAIN);
            logger.info("Repository created : " + repoName);
        } catch (IOException e) {
            logger.error("Error while creating repository : " + repoName, e);
        }
        return createdRepository;
    }


    /**
     * Fetch repository for given repository name under given organization.
     *
     * @param repoName
     * @param organization
     * @return Repository.
     */
    public Repository fetchRepository(String repoName, String organization) {
        Repository fetchedRepo = null;
        try {
            //TO create new repo.
            logger.info("Fetching Repository : " + repoName);
            fetchedRepo = this.repositoryService.getRepository(organization, repoName);
            logger.info("Repository Fetched : " + repoName);
        } catch (IOException e) {
            logger.error("Error while fetching repository : " + repoName, e);
        }
        return fetchedRepo;
    }

    /**
     * Create pull request for given from, to , repository Name, organization, and PR title and its description.
     *
     * @param fromBranch
     * @param toBranch
     * @param repoName
     * @param organization
     * @param title
     * @param body
     * @return Pull request details.
     */

    public PullRequest createPullRequest(String fromBranch, String toBranch, String repoName, String organization, String title, String body) throws PullRequestException {
        PullRequest pullRequest = null;
        try {
            Repository repository = repositoryService.getRepository(organization, repoName);
            PullRequest request = new PullRequest();
            request.setTitle(title);
            request.setBody(body);
            request.setHead(new PullRequestMarker().setRef(fromBranch).setLabel(fromBranch));
            request.setBase(new PullRequestMarker().setRef(toBranch).setLabel(toBranch));
            logger.info("Creating pull request from : " + fromBranch + " to branch : " + toBranch);
            pullRequest = pullRequestService.createPullRequest(repository, request);
            logger.info("PR created.");

        } catch (IOException e) {
            logger.error("Error while creating PR : ", e);
            throw new PullRequestException(e.getLocalizedMessage());
        }
        return pullRequest;
    }

    public PullRequest checkIfPRExists(Repository repository, String stateOpen, String fromBranch, String toBranch) throws IOException {
        List<PullRequest> pullRequests = this.pullRequestService.getPullRequests(repository, stateOpen);
        for (PullRequest pullRequest : pullRequests) {
            if (pullRequest.getBase().getRef().equalsIgnoreCase(toBranch) && pullRequest.getHead().getRef().equalsIgnoreCase(fromBranch))
                return pullRequest;
        }
        return null;
    }

    public Boolean checkIfBranchExists(String branch, IRepositoryIdProvider repository) throws IOException {
        for (RepositoryBranch repositoryBranch : this.repositoryService.getBranches(repository)) {
            if (repositoryBranch.getName().equalsIgnoreCase(branch)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Merge pull request for given pullnumber and repository.
     *
     * @param pullRequest
     * @param repository
     * @param commitMessage
     * @return commithash
     */
    public MergeStatus mergePullRequest(PullRequest pullRequest, IRepositoryIdProvider repository, String commitMessage) throws Exception {

        String commitHash = "";
        MergeStatus merge = null;
        int pullNumber = pullRequest.getNumber();
        try {
            logger.info("Trying to merge pull request : " + pullNumber);
            merge = this.pullRequestService.merge(repository, pullNumber, commitMessage);
            logger.info("Merge successful for pull request : " + pullNumber);
        } catch (Exception e) {
            logger.error("Error while merging. It could not be merged.", e);
            if (e.getMessage().contains(CONFLICT_ERROR_CODE)) {
                this.resetReleaseBranch(repository, pullRequest);
                throw new MergeException(RESTART_PERSIST);
            }
            throw new MergeException(pullNumber, e);
        }
        return merge;
    }

    /**
     * Create branch for given Repository Name and organization, branch.
     *
     * @param repoName
     * @param organization
     * @param branch
     * @return Reference
     */
    public Reference createBranch(String repoName, String organization, String branch) {
        Reference createdBranchRef = null;
        try {
            Repository repository = repositoryService.getRepository(organization, repoName);

            Reference reference = new Reference();
            TypedResource typedResource = new TypedResource();
            typedResource.setSha(getBranchSha(this.repositoryService.getBranches(repository)));
            reference.setRef(REFS + SEPARATOR + HEADS + SEPARATOR + branch);
            reference.setObject(typedResource);

            logger.info("Creating branch : " + branch + " for repository : " + repoName + " in organization : " + organization);
            createdBranchRef = this.dataService.createReference(repository, reference);
            logger.info("Branch created :" + branch);

        } catch (IOException e) {
            logger.error("Error while creating branch : " + branch, e);
        }
        return createdBranchRef;
    }

    /**
     * Create branch on given Repository and branch name.
     *
     * @param repository
     * @param branch
     * @return reference to new branch.
     */
    public Reference createBranch(Repository repository, String branch) {
        Reference createdBranchRef = null;
        try {

            Reference reference = new Reference();
            TypedResource typedResource = new TypedResource();
            typedResource.setSha(getBranchSha(this.repositoryService.getBranches(repository)));
            //reference.setRef("refs/heads/" + branch);
            reference.setRef(REFS + SEPARATOR + HEADS + SEPARATOR + branch);
            reference.setObject(typedResource);

            logger.info("Creating branch : " + branch + " for repository : " + repository.getName()
            );
            createdBranchRef = this.dataService.createReference(repository, reference);
            logger.info("Branch created :" + branch);

        } catch (IOException e) {
            logger.error("Error while creating branch : " + branch, e);
            new BranchCreationException(e);
        }
        return createdBranchRef;
    }


    /**
     * This will take input as files to be committed.
     * returns commit id.
     *
     * @param files
     * @return
     */
    public String pushFiles(GithubPushSqlFiles files) throws PushException {
        String commitHash = "";
        try {

            Repository repository = repositoryService
                    .getRepository(files.getOwner(), files.getRepo());

            commitHash = pushFilesInRepo(files, repository);


        } catch (IOException e) {
            logger.error("Error while pushing files.", e);
            throw new PushException(e);
        }

        return commitHash;
    }

    /**
     * This will take input as files to be committed.
     * returns commit id.
     *
     * @param files
     * @return
     */
    public String pushFiles(GithubPushSqlFiles files, Repository repository) throws PushException {
        String commitHash = "";
        try {

            commitHash = pushFilesInRepo(files, repository);


        } catch (IOException e) {
            logger.error("Error while pushing files.", e);
            throw new PushException(e);
        }

        return commitHash;
    }

    /**
     * This will take input as files to be committed. It will also delete the file which are not present
     * in given list from git repo except for ignorable files.
     * returns commit id.
     *
     * @param files
     * @return
     */
    public String updateFiles(GithubPushSqlFiles files, Repository repository) throws PushException {
        String commitHash = "";
        try {

            commitHash = pushAndRemoveFilesInRepo(files, repository, null);


        } catch (IOException e) {
            logger.error("Error while pushing files.", e);
            throw new PushException(e);
        }

        return commitHash;
    }

    public String updateFiles(GithubPushSqlFiles files, Repository repository, String branchName) throws PushException {
        String commitHash = "";
        try {

            commitHash = pushAndRemoveFilesInRepo(files, repository, branchName);


        } catch (IOException e) {
            logger.error("Error while pushing files.", e);
            throw new PushException(e);
        }

        return commitHash;
    }

    private String pushFilesInRepo(GithubPushSqlFiles files, Repository repository) throws IOException {
        String commitHash;
        List<RepositoryCommit> commits = commitService.getCommits(repository);
        RepositoryCommit latestCommit = getLatestCommit(commits);
        String treeSha = latestCommit.getSha();
        logger.info("Starting committing files...");
        Tree baseTree = dataService.getTree(repository, treeSha);

        logger.debug("Creating Tree entries.");
        List<TreeEntry> treeEntries = getTreeEntries(files, repository);
        logger.debug("Tree entries created.");

        logger.debug("Creating new Tree based on previous tree.");
        Tree newTree = dataService.createTree(repository, treeEntries, baseTree.getSha());
        logger.debug("New Tree created.");
        //Starting commit from here.


        Commit newCommit = createCommit(files, repository, latestCommit, treeSha, newTree);
        //Committed.

        // create resource
        updateMasterBranch(repository, newCommit, null);

        commitHash = newCommit.getSha();

        logger.info("Commit successful.");
        return commitHash;
    }

    private String pushAndRemoveFilesInRepo(GithubPushSqlFiles files, Repository repository, String branchName) throws IOException {
        String commitHash;
        List<RepositoryCommit> commits = commitService.getCommits(repository);
        RepositoryCommit latestCommit = getLatestCommit(commits);
        String treeSha = latestCommit.getSha();
        logger.info("Starting committing files...");
        Tree baseTree = dataService.getTree(repository, treeSha, true);

        List<TreeEntry> existingTreeEntries = baseTree.getTree();

        logger.debug("Creating Tree entries.");
        List<TreeEntry> treeEntries = getTreeEntries(files, repository, existingTreeEntries);
        logger.debug("Tree entries created.");


        if (Objects.isNull(treeEntries) || treeEntries.size() == 0) {
            logger.info("There is nothing to commit. returning last commit. : " + latestCommit.getSha());
            return latestCommit.getSha();
        }


        logger.debug("Creating new Tree based on previous tree.");
        Tree newTree = dataService.createTree(repository, treeEntries, baseTree.getSha());
        logger.debug("New Tree created.");
        //Starting commit from here.


        Commit newCommit = createCommit(files, repository, latestCommit, treeSha, newTree);
        //Committed.

        // create resource
        updateMasterBranch(repository, newCommit, branchName);

        commitHash = newCommit.getSha();

        logger.info("Commit successful.");
        return commitHash;
    }

    private List<TreeEntry> getTreeEntries(GithubPushSqlFiles files, Repository repository, List<TreeEntry> existingTreeEntries) throws IOException {

        Map<String, TreeEntry> existingTreeMap = existingTreeEntries
                .stream()
                .filter(treeEntry -> treeEntry.getType().equalsIgnoreCase(TreeEntry.TYPE_BLOB))
                .filter(treeEntry -> !isIgnorableFile(treeEntry.getPath()))
                .collect(Collectors.toMap(treeEntry -> treeEntry.getPath(), treeEntry -> treeEntry));
        List<TreeEntry> treeEntries = new ArrayList<>();
        for (RepositoryContents file : files.getGithubFiles()) {
            TreeEntry existingTree = existingTreeMap.get(file.getPath());
            if (Objects.nonNull(existingTree)) {
                Blob blob = this.dataService.getBlob(repository, existingTree.getSha());
                String encodedContent = blob.getContent().replace("\n", "");
                String existingFileContent = new String(Base64.getDecoder().decode(encodedContent));
                if (file.getContent().equals(existingFileContent)) {
                    //removing from tobedeletedList.
                    existingTreeMap.remove(file.getPath());
                    logger.info("Skipping file : " + file.getPath() + " Since its same.");
                    continue;
                }

            }
            //if (file.getContent().equals(existingFile.get))

            Blob blob = new Blob();
            blob.setContent(file.getContent()).setEncoding(Objects.isNull(file.getEncoding()) || file.getEncoding().length() == 0
                    ? Blob.ENCODING_UTF8 : file.getEncoding());
            String blob_sha = this.dataService.createBlob(repository, blob);
            TreeEntry treeEntry = new TreeEntry();
            treeEntry.setPath(file.getPath());
            //removing from tobedeletedList.
            existingTreeMap.remove(file.getPath());
            treeEntry.setMode(TreeEntry.MODE_BLOB);
            treeEntry.setType(TreeEntry.TYPE_BLOB);
            treeEntry.setSha(blob_sha);
            treeEntry.setSize(blob.getContent().length());
            treeEntries.add(treeEntry);
        }

        //For deleting older files.
        if (existingTreeMap.size() > 0) {
            for (TreeEntry existingTree : existingTreeMap.values()) {
                existingTree.setSha(null);
                treeEntries.add(existingTree);
            }
        }


        return treeEntries;

    }


    public String getBranchLatestCommit(Repository repository, String branch) throws IOException {

        List<RepositoryBranch> branches = this.repositoryService.getBranches(repository);
        return getBranchLatestCommit(branches, branch);
    }


    /**
     * This will take input as files to be committed and branch.
     * returns commit id.
     *
     * @param files
     * @return
     */
    public String pushFiles(GithubPushSqlFiles files, String branch) {
        String commitHash = "";
        try {

            Repository repository = repositoryService
                    .getRepository(files.getOwner(), files.getRepo());

            List<RepositoryBranch> branches = this.repositoryService.getBranches(repository);
            String branchLastestCommit = getBranchLatestCommit(branches, branch);
            RepositoryCommit latestCommit = this.commitService.getCommit(repository, branchLastestCommit);
            String treeSha = latestCommit.getSha();
            logger.info("Starting committing files...");
            Tree baseTree = dataService.getTree(repository, treeSha);

            logger.debug("Creating Tree entries.");
            List<TreeEntry> treeEntries = getTreeEntries(files, repository);
            logger.debug("Tree entries created.");

            logger.debug("Creating new Tree based on previous tree.");
            Tree newTree = dataService.createTree(repository, treeEntries, baseTree.getSha());
            logger.debug("New Tree created.");
            //Starting commit from here.


            Commit newCommit = createCommit(files, repository, latestCommit, treeSha, newTree);
            //Committed.

            // create resource
            updateBranchRef(repository, newCommit, branch);

            commitHash = newCommit.getSha();

            logger.info("Commit successful.");


        } catch (IOException e) {
            logger.error("Error while pushing files.", e);
        }

        return commitHash;
    }

    private String getBranchLatestCommit(List<RepositoryBranch> branches, String branch) {
        return branches.stream().filter(br -> br.getName().equalsIgnoreCase(branch)).findFirst().get().getCommit().getSha();
    }


    private void updateMasterBranch(Repository repository, Commit newCommit, String branchName) throws IOException {
        TypedResource commitResource = new TypedResource();
        commitResource.setSha(newCommit.getSha());
        commitResource.setType(TypedResource.TYPE_COMMIT);
        commitResource.setUrl(newCommit.getUrl());

        // get master reference and update it
        Reference reference = dataService.getReference(repository, HEADS + SEPARATOR + branchName);
        reference.setObject(commitResource);
        dataService.editReference(repository, reference, true);
    }


    private void updateBranchRef(Repository repository, Commit newCommit, String branch) throws IOException {
        TypedResource commitResource = new TypedResource();
        commitResource.setSha(newCommit.getSha());
        commitResource.setType(TypedResource.TYPE_COMMIT);
        commitResource.setUrl(newCommit.getUrl());

        // get master reference and update it
        Reference reference = dataService.getReference(repository, HEADS + SEPARATOR + branch);
        reference.setObject(commitResource);
        dataService.editReference(repository, reference, true);
    }


    private Commit createCommit(GithubPushSqlFiles files, Repository repository, RepositoryCommit latestCommit, String treeSha, Tree newTree) throws IOException {
        Commit commit = new Commit();
        commit.setMessage(files.getCommitMessage());
        commit.setTree(newTree);
        CommitUser author = getAuthor(files.getAuthorName());
        commit.setAuthor(author);
        commit.setCommitter(author);
        List<Commit> listOfCommits = new ArrayList<>();
        listOfCommits.add(new Commit().setSha(treeSha));
        commit.setParents(listOfCommits);
        return dataService.createCommit(repository, commit);
    }

    private CommitUser getAuthor(String author) throws IOException {
        CommitUser user = new CommitUser();
        user.setDate(new Date());

        if (StringUtils.isBlank(author)) {
            User loggedUser = this.userService.getUser();
            user.setName(loggedUser.getName());
            user.setEmail(Objects.isNull(loggedUser.getEmail()) ? loggedUser.getName() + EMAIL_SUFFIX : loggedUser.getEmail());
        } else {
            user.setEmail(author + EMAIL_SUFFIX);
            user.setName(author);
        }
        return user;
    }

    private List<TreeEntry> getTreeEntries(GithubPushSqlFiles files, IRepositoryIdProvider repository) throws IOException {
        List<TreeEntry> treeEntries = new ArrayList<>();
        for (RepositoryContents file : files.getGithubFiles()) {
            Blob blob = new Blob();
            blob.setContent(file.getContent()).setEncoding(Objects.isNull(file.getEncoding()) || file.getEncoding().length() == 0
                    ? Blob.ENCODING_UTF8 : file.getEncoding());
            String blob_sha = this.dataService.createBlob(repository, blob);
            TreeEntry treeEntry = new TreeEntry();
            treeEntry.setPath(file.getPath());
            treeEntry.setMode(TreeEntry.MODE_BLOB);
            treeEntry.setType(TreeEntry.TYPE_BLOB);
            treeEntry.setSha(blob_sha);
            treeEntry.setSize(blob.getContent().length());
            treeEntries.add(treeEntry);
        }
        return treeEntries;
    }


    public boolean createRepositoryR(String repoName, String organization, String description) {
        UserAppsRepository oldRepo = new UserAppsRepository();
        oldRepo.setName(repoName);
        oldRepo.setDescription(description);
        oldRepo.setMasterBranch("master");
        oldRepo.setAutoInit(true);
        try {
            //TO create new repo.
            //Repository repository = this.repositoryService.createRepository(organization, oldRepo);
            //TO fetch existing repo.
            Repository repository = repositoryService.getRepository(organization, repoName);


            String baseCommitSha = repositoryService.getBranches(repository).get(0).getCommit().getSha();
            RepositoryCommit baseCommit = commitService.getCommit(repository, baseCommitSha);

            String treeSha = baseCommit.getSha();

            // create new blob with data
            Blob blob = new Blob();
            blob.setContent("[\"" + System.currentTimeMillis() + "\"]").setEncoding(Blob.ENCODING_UTF8);
            String blob_sha = dataService.createBlob(repository, blob);
            Tree baseTree = dataService.getTree(repository, treeSha);


            Blob blob1 = new Blob();
            blob1.setContent("[\"" + "HAHAHAHAHAHHAH" + "\"]").setEncoding(Blob.ENCODING_UTF8);
            String blob_sha1 = dataService.createBlob(repository, blob1);
            //Tree baseTree1 = dataService.getTree(repository, treeSha);

            // create new tree entry
            TreeEntry treeEntry = new TreeEntry();
            treeEntry.setPath("testfile.txt");
            treeEntry.setMode(TreeEntry.MODE_BLOB);
            treeEntry.setType(TreeEntry.TYPE_BLOB);
            treeEntry.setSha(blob_sha);
            treeEntry.setSize(blob.getContent().length());

            TreeEntry treeEntry1 = new TreeEntry();
            treeEntry1.setPath("testfile1.txt");
            treeEntry1.setMode(TreeEntry.MODE_BLOB);
            treeEntry1.setType(TreeEntry.TYPE_BLOB);
            treeEntry1.setSha(blob_sha1);
            treeEntry1.setSize(blob1.getContent().length());
            Collection<TreeEntry> entries = new ArrayList<TreeEntry>();
            entries.add(treeEntry);
            entries.add(treeEntry1);
            Tree newTree = dataService.createTree(repository, entries, baseTree.getSha());

            // create commit
            Commit commit = new Commit();
            commit.setMessage("first commit at " + new Date(System.currentTimeMillis()).toLocaleString());
            commit.setTree(newTree);

            UserService userService = new UserService(gitHubClient);
            User user = userService.getUser();
            CommitUser author = new CommitUser();
            author.setName(user.getName());
            author.setEmail(Objects.isNull(user.getEmail()) ? user.getName() + "@paypal.com" : user.getEmail());
            Calendar now = Calendar.getInstance();
            author.setDate(now.getTime());
            commit.setAuthor(author);
            commit.setCommitter(author);


            List<Commit> listOfCommits = new ArrayList<Commit>();
            listOfCommits.add(new Commit().setSha(baseCommitSha));
            // listOfCommits.containsAll(base_commit.getParents());
            commit.setParents(listOfCommits);
            // commit.setSha(base_commit.getSha());
            Commit newCommit = dataService.createCommit(repository, commit);

            // create resource
            TypedResource commitResource = new TypedResource();
            commitResource.setSha(newCommit.getSha());
            commitResource.setType(TypedResource.TYPE_COMMIT);
            commitResource.setUrl(newCommit.getUrl());

            // get master reference and update it
            Reference reference = dataService.getReference(repository, "heads/master");
            reference.setObject(commitResource);
            dataService.editReference(repository, reference, true);
            System.out.println("Committed URL: " + newCommit.getUrl());
            return true;


        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }


    private String getBranchSha(List<RepositoryBranch> branches) {
        //For creating main branch.
        if (branches.size() == 1) {
            return branches.get(0).getCommit().getSha();
        }
        //For creating other branch from main or master.
        if (branches.stream().noneMatch(repositoryBranch -> repositoryBranch.getName().equalsIgnoreCase(MAIN)))
            return branches.stream().filter(repositoryBranch -> repositoryBranch.getName().equalsIgnoreCase(MASTER)).findFirst().get().getCommit().getSha();
        return branches.stream().filter(repositoryBranch -> repositoryBranch.getName().equalsIgnoreCase(MAIN)).findFirst().get().getCommit().getSha();
    }

    private RepositoryCommit getLatestCommit(List<RepositoryCommit> commits) {
        return commits.get(0);
    }


    public void resetReleaseBranch(IRepositoryIdProvider repository, PullRequest pullRequest) throws IOException {
        // close pull request
        pullRequest.setState(CLOSED_STATE);
        this.pullRequestService.editPullRequest(repository, pullRequest);
        // rename branch
        List<RepositoryBranch> branches = this.repositoryService.getBranches(repository);
        List<RepositoryBranch> releaseBranches = branches.stream().filter(p -> p.getName().equals(RELEASE)).collect(Collectors.toList());
        int releaseBackupsNumber = (int) branches.stream().filter(p -> p.getName().contains(RELEASE_BCK)).count();

        if (releaseBranches.isEmpty()) {
            return;
        }
        String name = RELEASE_BCK;
        if (releaseBackupsNumber > 0) {
            name += (releaseBackupsNumber + 1);
        }
        createBranch((Repository) repository, name);

        Reference reference = new Reference();
        TypedResource typedResource = new TypedResource();
        typedResource.setSha(getBranchSha(releaseBranches));
        reference.setRef(REFS + SEPARATOR + HEADS + SEPARATOR + RELEASE);
        reference.setObject(typedResource);

        this.dataService.deleteReference(repository, reference);

    }


}
