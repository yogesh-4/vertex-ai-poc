package org.example.model;

import org.eclipse.egit.github.core.RepositoryContents;

import java.util.List;

public class GithubPushSqlFiles {

    private List<RepositoryContents> githubFiles;

    private String owner;
    private String repo;
    private String commitMessage;
    private String authorName;

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public List<RepositoryContents> getGithubFiles() {
        return githubFiles;
    }

    public void setGithubFiles(List<RepositoryContents> githubFiles) {
        this.githubFiles = githubFiles;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }
}
